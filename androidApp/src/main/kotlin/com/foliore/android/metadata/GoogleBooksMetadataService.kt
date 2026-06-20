package com.foliore.android.metadata

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.text.HtmlCompat
import com.foliore.android.storage.MetadataPreferenceRepository
import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.domain.LibraryBook
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class GoogleBooksMetadataService(
    context: Context,
    private val metadataPreferenceRepository: MetadataPreferenceRepository,
) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun search(
        book: LibraryBook,
        draft: BookMetadataDraft,
        limit: Int = 4,
    ): MetadataLookupResult {
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit<MetadataLookupResult> {
            searchBlocking(book = book, draft = draft, limit = limit)
        }

        return try {
            future.get(LOOKUP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            MetadataLookupResult.Failure("Google Books lookup timed out")
        } catch (error: Exception) {
            MetadataLookupResult.Failure(error.message)
        } finally {
            executor.shutdownNow()
        }
    }

    private fun searchBlocking(
        book: LibraryBook,
        draft: BookMetadataDraft,
        limit: Int,
    ): MetadataLookupResult {
        val apiKey = metadataPreferenceRepository.googleBooksApiKey()
            ?: return MetadataLookupResult.Success(emptyList())
        return runCatching {
            val volumes = searchVolumes(draft, limit, apiKey)
            val candidates = volumes
                .distinctBy { it.id ?: it.volumeInfo.title.orEmpty() + it.volumeInfo.authors.joinToString() }
                .take(limit)
                .mapIndexedNotNull { index, volume ->
                    volume.toCandidate(
                        book = book,
                        current = draft,
                        index = index,
                    )
                }
            MetadataLookupResult.Success(candidates)
        }.getOrElse {
            MetadataLookupResult.Failure(it.message)
        }
    }

    private fun searchVolumes(
        draft: BookMetadataDraft,
        limit: Int,
        apiKey: String,
    ): List<GoogleBookVolume> {
        val isbn = draft.bestIsbn()
        val queries = buildList {
            if (isbn != null) add("isbn:$isbn")
            add(
                buildList {
                    draft.title.cleanOptional()?.let { add("intitle:$it") }
                    draft.authors.firstOrNull()?.cleanOptional()?.let { add("inauthor:$it") }
                }.joinToString(" "),
            )
        }.filter { it.isNotBlank() }

        queries.forEach { query ->
            val response = getText(googleBooksSearchUrl(query, limit, apiKey))
            val volumes = json.decodeFromString(GoogleBooksSearchResponse.serializer(), response).items
            if (volumes.isNotEmpty()) return volumes
        }

        return emptyList()
    }

    private fun GoogleBookVolume.toCandidate(
        book: LibraryBook,
        current: BookMetadataDraft,
        index: Int,
    ): MetadataLookupCandidate? {
        val cleanTitle = volumeInfo.title?.cleanOptional() ?: return null
        val cleanSubtitle = volumeInfo.subtitle?.cleanOptional()
        val cleanAuthors = volumeInfo.authors.mapNotNull { it.cleanOptional() }
        val cleanCategories = volumeInfo.categories
            .asSequence()
            .mapNotNull { it.cleanOptional() }
            .filterNot { it.length > 42 }
            .distinctBy { it.lowercase() }
            .take(8)
            .toList()
        val industryIsbns = volumeInfo.industryIdentifiers.mapNotNull { it.identifier.extractIsbn() }
        val bestIsbn = industryIsbns.sortedByDescending { it.length }.firstOrNull()
        val cover = volumeInfo.imageLinks?.let { links ->
            downloadCover(
                bookId = book.book.id,
                candidateId = id ?: "google-books-$index",
                urls = GoogleBooksCoverUrlNormalizer.orderedCoverUrls(
                    extraLarge = links.extraLarge,
                    large = links.large,
                    medium = links.medium,
                    small = links.small,
                    thumbnail = links.thumbnail,
                    smallThumbnail = links.smallThumbnail,
                ),
            )
        }
        val identifiers = current.identifiers +
            listOfNotNull(
                id?.let { "google_books_volume" to it },
                bestIsbn?.let { "google_books_isbn" to it },
            )

        return MetadataLookupCandidate(
            id = id ?: "google-books-$index",
            sourceName = "Google Books",
            metadata = BookMetadataDraft(
                title = cleanTitle,
                subtitle = cleanSubtitle ?: current.subtitle,
                authors = cleanAuthors.ifEmpty { current.authors },
                sortAuthor = current.sortAuthor ?: cleanAuthors.firstOrNull()?.toSortAuthor(),
                language = volumeInfo.language?.cleanOptional() ?: current.language,
                publisher = volumeInfo.publisher?.cleanOptional() ?: current.publisher,
                publishedDate = volumeInfo.publishedDate?.cleanOptional() ?: current.publishedDate,
                description = volumeInfo.description?.toDisplayText() ?: current.description,
                tags = cleanCategories.ifEmpty { current.tags },
                isbn = bestIsbn ?: current.bestIsbn(),
                identifiers = identifiers,
                seriesName = current.seriesName,
                seriesIndex = current.seriesIndex,
            ),
            coverImagePath = cover?.file?.absolutePath,
            coverPixelWidth = cover?.width,
            coverPixelHeight = cover?.height,
            sourceUrl = volumeInfo.infoLink ?: volumeInfo.canonicalVolumeLink ?: volumeInfo.previewLink,
        )
    }

    private fun downloadCover(
        bookId: String,
        candidateId: String,
        urls: List<String>,
    ): DownloadedCover? {
        val outputDir = File(appContext.cacheDir, "metadata-covers/$bookId").apply { mkdirs() }
        urls.forEachIndexed { index, coverUrl ->
            val outputFile = File(outputDir, "${candidateId.sanitizeFileName()}-$index.jpg")
            val downloaded = runCatching {
                downloadCoverUrl(coverUrl, outputFile)
                outputFile.coverDimensions()
                    ?.takeIf { it.isHighQualityCover() }
                    ?.let { dimensions ->
                        DownloadedCover(
                            file = outputFile,
                            width = dimensions.width,
                            height = dimensions.height,
                        )
                    }
            }.getOrNull()
            if (downloaded != null) return downloaded
            outputFile.delete()
        }
        return null
    }

    private fun downloadCoverUrl(
        coverUrl: String,
        outputFile: File,
    ) {
        val connection = (URL(coverUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = COVER_CONNECT_TIMEOUT_MS
            readTimeout = COVER_READ_TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (connection.responseCode !in 200..299) return
            connection.inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyToWithLimit(output, MAX_COVER_BYTES)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun getText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = SEARCH_CONNECT_TIMEOUT_MS
            readTimeout = SEARCH_READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        val responseCode = connection.responseCode
        val responseText = if (responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
        }
        connection.disconnect()
        if (responseCode !in 200..299) {
            error("Google Books request failed: HTTP $responseCode")
        }
        return responseText
    }

    private fun googleBooksSearchUrl(query: String, limit: Int, apiKey: String): String {
        val encodedQuery = query.urlEncoded()
        val encodedApiKey = apiKey.urlEncoded()
        return "https://www.googleapis.com/books/v1/volumes?q=$encodedQuery" +
            "&maxResults=$limit&printType=books&projection=lite&key=$encodedApiKey"
    }

    private fun CoverDimensions.isHighQualityCover(): Boolean {
        return width >= MIN_COVER_WIDTH &&
            height >= MIN_COVER_HEIGHT &&
            height >= width
    }

    private fun File.coverDimensions(): CoverDimensions? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(absolutePath, options)
        return CoverDimensions(
            width = options.outWidth,
            height = options.outHeight,
        ).takeIf { it.width > 0 && it.height > 0 }
    }

    private fun InputStream.copyToWithLimit(output: java.io.OutputStream, maxBytes: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            copied += read
            if (copied > maxBytes) error("Cover image is too large")
            output.write(buffer, 0, read)
        }
    }

    private fun String.toDisplayText(): String {
        val plain = HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace('\u00A0', ' ')
        return plain.lines()
            .joinToString("\n") { it.trim() }
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun String.cleanOptional(): String? = trim().takeIf { it.isNotEmpty() }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun String.sanitizeFileName(): String {
        return replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifBlank { "cover" }
    }

    private fun String.toSortAuthor(): String {
        val parts = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (parts.size <= 1) this else "${parts.last()}, ${parts.dropLast(1).joinToString(" ")}"
    }

    private fun BookMetadataDraft.bestIsbn(): String? {
        return isbn?.extractIsbn()
            ?: identifiers.values.asSequence().mapNotNull { it.extractIsbn() }.firstOrNull()
    }

    private fun String.extractIsbn(): String? {
        val compact = filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase()
        return when (compact.length) {
            10, 13 -> compact
            else -> null
        }
    }

    @Serializable
    private data class GoogleBooksSearchResponse(
        val items: List<GoogleBookVolume> = emptyList(),
    )

    @Serializable
    private data class GoogleBookVolume(
        val id: String? = null,
        val volumeInfo: GoogleBookVolumeInfo,
    )

    @Serializable
    private data class GoogleBookVolumeInfo(
        val title: String? = null,
        val subtitle: String? = null,
        val authors: List<String> = emptyList(),
        val publisher: String? = null,
        val publishedDate: String? = null,
        val description: String? = null,
        val industryIdentifiers: List<GoogleBookIndustryIdentifier> = emptyList(),
        val categories: List<String> = emptyList(),
        val language: String? = null,
        val imageLinks: GoogleBookImageLinks? = null,
        val previewLink: String? = null,
        val infoLink: String? = null,
        val canonicalVolumeLink: String? = null,
    )

    @Serializable
    private data class GoogleBookIndustryIdentifier(
        val type: String? = null,
        val identifier: String,
    )

    @Serializable
    private data class GoogleBookImageLinks(
        val smallThumbnail: String? = null,
        val thumbnail: String? = null,
        val small: String? = null,
        val medium: String? = null,
        val large: String? = null,
        @SerialName("extraLarge") val extraLarge: String? = null,
    )

    private data class DownloadedCover(
        val file: File,
        val width: Int,
        val height: Int,
    )

    private data class CoverDimensions(
        val width: Int,
        val height: Int,
    )

    private companion object {
        const val USER_AGENT = "Foliore/0.1 metadata lookup"
        const val SEARCH_CONNECT_TIMEOUT_MS = 8_000
        const val SEARCH_READ_TIMEOUT_MS = 10_000
        const val COVER_CONNECT_TIMEOUT_MS = 5_000
        const val COVER_READ_TIMEOUT_MS = 8_000
        const val LOOKUP_TIMEOUT_MS = 16_000L
        const val MIN_COVER_WIDTH = 400
        const val MIN_COVER_HEIGHT = 600
        const val MAX_COVER_BYTES = 8L * 1024L * 1024L
    }
}
