package com.foliore.android.metadata

import android.graphics.BitmapFactory
import com.foliore.shared.domain.LibraryBook
import com.foliore.android.storage.copyBounded
import com.foliore.android.storage.safeChildDirectory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.serialization.json.*

/** Optional assets never determine whether a bibliographic search succeeds. */
internal class MetadataCandidateEnricher(private val cacheDir: File) {
    fun enrichMetadata(
        candidate: MetadataLookupCandidate,
        getText: (String) -> String = MetadataHttpClient(8_000)::getText,
        onUpdated: (MetadataLookupCandidate) -> Unit = {},
    ): MetadataLookupCandidate {
        var result = candidate
        // A result from another catalog can locate the same work by its edition ISBN.
        // This lookup only supplements series data; it never replaces that result's edition fields.
        if (candidate.workKey == null && candidate.editionKey == null && candidate.metadata.seriesIndex == null) {
            MetadataSearchRules.bestIsbn(candidate.metadata)?.let { isbn ->
                runCatching {
                    val edition = Json.parseToJsonElement(getText("https://openlibrary.org/isbn/$isbn.json")).jsonObject
                    openLibraryEdition(edition, emptySourceMetadata(""), requestedIsbn = isbn)?.let { detail ->
                        result = result.copy(workKey = detail.workKey,
                            metadata = result.metadata.withSeries(edition.catalogSeries()))
                        onUpdated(result)
                    }
                }
            }
        }
        candidate.editionKey?.takeUnless { candidate.editionResolved }?.safeEditionKey()?.let { key ->
            runCatching {
                val edition = Json.parseToJsonElement(getText("https://openlibrary.org$key.json")).jsonObject
                openLibraryEdition(edition, candidate.metadata)?.let { detail ->
                    result = candidate.copy(
                        metadata = candidate.metadata.applySelectedMetadata(detail.metadata, MetadataField.defaultSelection).withSeries(edition.catalogSeries()),
                        matchedIsbns = detail.matchedIsbns + candidate.matchedIsbns,
                        workKey = detail.workKey ?: candidate.workKey, sourceLanguage = detail.sourceLanguage ?: candidate.sourceLanguage,
                        coverUrls = (detail.coverUrls + candidate.coverUrls).distinct(), authorKeys = detail.authorKeys, editionResolved = true,
                    )
                    onUpdated(result)
                }
            }
        }
        result.workKey?.safeWorkKey()?.let { key ->
            runCatching {
                val work = Json.parseToJsonElement(getText("https://openlibrary.org$key.json")).jsonObject
                val series = work.catalogSeries()
                val withSeries = if (series == null) result.metadata else result.metadata.withSeries(series.copy(
                    index = series.index ?: result.metadata.seriesIndex.takeIf { result.metadata.seriesName?.seriesKey() == series.name.seriesKey() },
                ))
                result = result.copy(metadata = withSeries.copy(
                    description = result.metadata.description ?: work.description(),
                    tags = (result.metadata.tags + work.strings("subjects").filter { it.length <= 80 && !it.startsWith("series:", true) }).distinct().take(16),
                ), authorKeys = result.authorKeys.ifEmpty {
                    work.items("authors").mapNotNull { (it as? JsonObject)?.obj("author")?.text("key")?.safeAuthorKey() }
                })
                onUpdated(result)
            }
            if (result.metadata.seriesIndex == null) {
                runCatching {
                    val editions = Json.parseToJsonElement(getText("https://openlibrary.org$key/editions.json?limit=50")).jsonObject
                    seriesFromWorkEditions(key, editions, result.metadata.seriesName, result.metadata.language)?.let { series ->
                        result = result.copy(metadata = result.metadata.withSeries(series))
                        onUpdated(result)
                    }
                }
            }
        }
        if (result.metadata.seriesIndex == null) {
            // Duplicate work records can be incomplete. Search has already checked matching
            // title, author and language; only series fields may cross this link.
            for (key in candidate.seriesWorkKeys.take(1).mapNotNull { it.safeWorkKey() }) {
                runCatching {
                    val work = Json.parseToJsonElement(getText("https://openlibrary.org$key.json")).jsonObject
                    val name = work.catalogSeries()?.name ?: result.metadata.seriesName
                    val editions = Json.parseToJsonElement(getText("https://openlibrary.org$key/editions.json?limit=50")).jsonObject
                    seriesFromWorkEditions(key, editions, name, result.metadata.language)?.let { series ->
                        result = result.copy(metadata = result.metadata.withSeries(series))
                        onUpdated(result)
                    }
                }
                if (result.metadata.seriesIndex != null) break
            }
        }
        if (result.metadata.authors.isEmpty()) {
            val authors = result.authorKeys.take(4).mapNotNull { key ->
                key.safeAuthorKey()?.let { safe -> runCatching {
                    Json.parseToJsonElement(getText("https://openlibrary.org$safe.json")).jsonObject.text("name")
                }.getOrNull() }
            }
            if (authors.isNotEmpty()) {
                result = result.copy(metadata = result.metadata.copy(authors = authors, sortAuthor = authors.first().sortAuthor()))
                onUpdated(result)
            }
        }
        if (result.metadata.seriesName != candidate.metadata.seriesName || result.metadata.seriesIndex != candidate.metadata.seriesIndex) {
            result = result.copy(sourceName = (result.sourceName.split(" · ") + "Open Library").distinct().joinToString(" · "))
        }
        return result
    }

    fun enrichCover(book: LibraryBook, candidate: MetadataLookupCandidate): MetadataLookupCandidate {
        val http = MetadataHttpClient(6_000)
        val result = candidate
        // Covers have their own bounded budget; edition/description latency cannot consume it.
        val directory = safeChildDirectory(File(cacheDir, "metadata-covers").apply { mkdirs() }, book.book.id).apply { mkdirs() }
        for (url in result.coverUrls.take(2)) {
            val cacheName = java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
            val cached = File(directory, "$cacheName.jpg")
            if (cached.isFile) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(cached.absolutePath, bounds)
                if (bounds.outWidth >= 160 && bounds.outHeight >= 200 && bounds.outHeight >= bounds.outWidth && bounds.outWidth.toLong() * bounds.outHeight <= 80_000_000)
                    return result.copy(coverImagePath = cached.absolutePath, coverPixelWidth = bounds.outWidth, coverPixelHeight = bounds.outHeight)
            }
            val file = File(directory, "${UUID.randomUUID()}.jpg")
            val cover = runCatching {
                http.remainingMillis()
                val parsed = URL(url)
                check(parsed.protocol == "https" && parsed.host in setOf("covers.openlibrary.org", "books.google.com", "books.googleusercontent.com", "archive.org"))
                val connection = parsed.openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = minOf(2_000L, http.remainingMillis()).toInt()
                    connection.readTimeout = minOf(2_000L, http.remainingMillis()).toInt()
                    connection.setRequestProperty("User-Agent", "Foliore/0.1 (metadata lookup)")
                    check(connection.responseCode in 200..299)
                    connection.inputStream.use { input -> file.outputStream().use { output ->
                        object : java.io.FilterInputStream(input) {
                            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                                http.remainingMillis()
                                return super.read(buffer, offset, length).also { http.remainingMillis() }
                            }
                        }.copyBounded(output, 8L * 1024 * 1024)
                    } }
                    http.remainingMillis()
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    check(bounds.outWidth >= 160 && bounds.outHeight >= 200 && bounds.outHeight >= bounds.outWidth && bounds.outWidth.toLong() * bounds.outHeight <= 80_000_000)
                    java.nio.file.Files.move(file.toPath(), cached.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    result.copy(coverImagePath = cached.absolutePath, coverPixelWidth = bounds.outWidth, coverPixelHeight = bounds.outHeight)
                } finally { connection.disconnect() }
            }.getOrNull()
            if (cover != null) return cover
            file.delete()
            if (Thread.currentThread().isInterrupted) break
        }
        return result
    }
}
