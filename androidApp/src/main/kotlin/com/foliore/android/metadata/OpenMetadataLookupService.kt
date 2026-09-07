package com.foliore.android.metadata

import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.domain.LibraryBook
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OpenMetadataLookupService(
    private val openLibraryMetadataService: OpenLibraryMetadataService,
    private val googleBooksMetadataService: GoogleBooksMetadataService,
    private val cacheDir: File? = null,
) {
    fun search(book: LibraryBook, draft: BookMetadataDraft, limit: Int = 8): MetadataLookupResult {
        val result = collectMetadataResults(draft, limit, listOf(
            { googleBooksMetadataService.search(book, draft, 8) },
            { openLibraryMetadataService.search(book, draft, 8) },
        ))
        if (result !is MetadataLookupResult.Success || result.candidates.isEmpty() || cacheDir == null) return result
        val enricher = MetadataCandidateEnricher(cacheDir)
        val candidates = result.candidates
        val enriched = java.util.concurrent.atomic.AtomicReferenceArray(candidates.toTypedArray())
        val pool = Executors.newFixedThreadPool(6)
        val completion = ExecutorCompletionService<Unit>(pool)
        val tasks = candidates.take(3).flatMapIndexed { index, candidate ->
            listOf(
                completion.submit(Callable {
                    enricher.enrichMetadata(candidate) { detail ->
                        enriched.updateAndGet(index) { previous -> detail.copy(
                            coverImagePath = previous.coverImagePath,
                            coverPixelWidth = previous.coverPixelWidth,
                            coverPixelHeight = previous.coverPixelHeight,
                        ) }
                    }
                    Unit
                }),
                completion.submit(Callable {
                    val cover = enricher.enrichCover(book, candidate)
                    if (cover.coverImagePath != null) enriched.updateAndGet(index) { previous -> previous.copy(
                        coverImagePath = cover.coverImagePath,
                        coverPixelWidth = cover.coverPixelWidth,
                        coverPixelHeight = cover.coverPixelHeight,
                    ) }
                    Unit
                }),
            )
        }
        val deadline = System.nanoTime() + 6_500_000_000
        try {
            repeat(tasks.size) {
                val remaining = deadline - System.nanoTime()
                if (remaining > 0) completion.poll(remaining, TimeUnit.NANOSECONDS)?.let { future ->
                    runCatching { future.get() }
                }
            }
        } finally { tasks.forEach { it.cancel(true) }; pool.shutdownNow() }
        return MetadataLookupResult.Success(MetadataSearchRules.rank(candidates.indices.map { enriched.get(it) }, draft, limit))
    }
}

/** A slow/failing provider must not discard the other provider's successful response. */
internal fun collectMetadataResults(
    draft: BookMetadataDraft,
    limit: Int,
    providers: List<() -> MetadataLookupResult>,
    timeoutMillis: Long = 19_000,
): MetadataLookupResult {
    if (providers.isEmpty()) return MetadataLookupResult.Failure("No metadata providers configured")
    val executor = Executors.newFixedThreadPool(providers.size)
    val completion = ExecutorCompletionService<MetadataLookupResult>(executor)
    val futures = providers.map { provider -> completion.submit(Callable { provider() }) }
    val results = mutableListOf<MetadataLookupResult>()
    val deadline = System.nanoTime() + timeoutMillis * 1_000_000
    try {
        repeat(providers.size) {
            val remaining = deadline - System.nanoTime()
            if (remaining > 0) {
                completion.poll(remaining, TimeUnit.NANOSECONDS)?.let { future ->
                    results += runCatching { future.get() }.getOrElse { MetadataLookupResult.Failure("Metadata provider unavailable") }
                }
            }
        }
    } finally { futures.forEach { it.cancel(true) }; executor.shutdownNow() }
    val candidates = MetadataSearchRules.rank(results.filterIsInstance<MetadataLookupResult.Success>().flatMap { it.candidates }, draft, limit)
    if (candidates.isNotEmpty()) return MetadataLookupResult.Success(candidates)
    val failures = results.filterIsInstance<MetadataLookupResult.Failure>()
    // Do not tell the user that a book does not exist when a configured source failed.
    return if (results.size < providers.size || failures.any { !it.notConfigured } || results.none { it is MetadataLookupResult.Success })
        MetadataLookupResult.Failure("Metadata providers unavailable; retry later")
    else MetadataLookupResult.Success(emptyList())
}
