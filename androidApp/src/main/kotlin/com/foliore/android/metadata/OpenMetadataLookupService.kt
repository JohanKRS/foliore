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
    fun search(book: LibraryBook, draft: BookMetadataDraft, limit: Int = 12): MetadataLookupResult =
        collectMetadataResults(draft, limit, listOf(
            { googleBooksMetadataService.search(book, draft, 12) },
            { openLibraryMetadataService.search(book, draft, 12) },
            { searchInternetArchive(draft, 12, MetadataHttpClient()::getText) },
            { searchBnf(draft, 12, MetadataHttpClient()::getText) },
        ))

    /** Resolve any selected result, not only the first few in the list. */
    fun resolve(book: LibraryBook, candidate: MetadataLookupCandidate): MetadataLookupCandidate {
        if (candidate.detailsResolved || cacheDir == null) return candidate
        val enricher = MetadataCandidateEnricher(cacheDir)
        val pool = Executors.newFixedThreadPool(2)
        val partial = java.util.concurrent.atomic.AtomicReference(candidate)
        val metadata = pool.submit(Callable { enricher.enrichMetadata(candidate) { partial.set(it) } })
        val cover = pool.submit(Callable { enricher.enrichCover(book, candidate) })
        val deadline = System.nanoTime() + 10_000_000_000
        fun remaining() = (deadline - System.nanoTime()).coerceAtLeast(1)
        try {
            val detail = runCatching { metadata.get(remaining(), TimeUnit.NANOSECONDS) }.getOrElse { partial.get() }
            val image = runCatching { cover.get(remaining(), TimeUnit.NANOSECONDS) }.getOrDefault(candidate)
            return detail.copy(coverImagePath = image.coverImagePath, coverPixelWidth = image.coverPixelWidth,
                coverPixelHeight = image.coverPixelHeight, detailsResolved = true)
        } finally { metadata.cancel(true); cover.cancel(true); pool.shutdownNow() }
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
