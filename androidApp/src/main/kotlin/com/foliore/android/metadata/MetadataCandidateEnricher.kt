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
    fun enrichMetadata(candidate: MetadataLookupCandidate, onUpdated: (MetadataLookupCandidate) -> Unit): MetadataLookupCandidate {
        val http = MetadataHttpClient(6_000)
        var result = candidate
        candidate.editionKey?.takeUnless { candidate.editionResolved }?.safeEditionKey()?.let { key ->
            runCatching {
                val edition = Json.parseToJsonElement(http.getText("https://openlibrary.org$key.json")).jsonObject
                openLibraryEdition(edition, candidate.metadata)?.let { detail ->
                    result = detail.copy(id = candidate.id, matchedIsbns = detail.matchedIsbns + candidate.matchedIsbns,
                        workKey = detail.workKey ?: candidate.workKey, sourceLanguage = detail.sourceLanguage ?: candidate.sourceLanguage, coverUrls = detail.coverUrls.ifEmpty { candidate.coverUrls })
                    onUpdated(result)
                }
            }
        }
        result.workKey?.safeWorkKey()?.let { key ->
            runCatching {
                val work = Json.parseToJsonElement(http.getText("https://openlibrary.org$key.json")).jsonObject
                work.description()?.let { description ->
                    result = result.copy(metadata = result.metadata.copy(description = description))
                    onUpdated(result)
                }
            }
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
                if (bounds.outWidth >= 400 && bounds.outHeight >= 600 && bounds.outHeight >= bounds.outWidth && bounds.outWidth.toLong() * bounds.outHeight <= 80_000_000)
                    return result.copy(coverImagePath = cached.absolutePath, coverPixelWidth = bounds.outWidth, coverPixelHeight = bounds.outHeight)
            }
            val file = File(directory, "${UUID.randomUUID()}.jpg")
            val cover = runCatching {
                http.remainingMillis()
                val parsed = URL(url)
                check(parsed.protocol == "https" && parsed.host in setOf("covers.openlibrary.org", "books.google.com", "books.googleusercontent.com"))
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
                    check(bounds.outWidth >= 400 && bounds.outHeight >= 600 && bounds.outHeight >= bounds.outWidth && bounds.outWidth.toLong() * bounds.outHeight <= 80_000_000)
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
