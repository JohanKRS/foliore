package com.foliore.android.metadata

import android.content.Context
import androidx.core.text.HtmlCompat
import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.domain.LibraryBook
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class OpenLibraryMetadataService(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    fun search(
        book: LibraryBook,
        draft: BookMetadataDraft,
        limit: Int = 5,
    ): MetadataLookupResult {
        val executor = Executors.newSingleThreadExecutor()
        val future = executor.submit<MetadataLookupResult> {
            searchBlocking(book = book, draft = draft, limit = limit)
        }

        return try {
            future.get(LOOKUP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            MetadataLookupResult.Failure("Open Library lookup timed out")
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
        return runCatching {
            val docs = searchDocuments(draft, limit)
            val candidates = docs
                .distinctBy { it.key ?: it.title.orEmpty() + it.authorName.joinToString() }
                .take(limit)
                .mapIndexedNotNull { index, doc ->
                    doc.toCandidate(
                        book = book,
                        current = draft,
                        index = index,
                        includeDescription = index == 0,
                        includeCover = false,
                    )
                }
            MetadataLookupResult.Success(candidates)
        }.getOrElse {
            MetadataLookupResult.Failure(it.message)
        }
    }

    private fun searchDocuments(
        draft: BookMetadataDraft,
        limit: Int,
    ): List<OpenLibrarySearchDoc> {
        val isbn = draft.bestIsbn()
        val queries = buildList {
            if (isbn != null) {
                add(openLibrarySearchUrl("isbn=$isbn", limit))
            }
            add(
                openLibrarySearchUrl(
                    buildList {
                        add("title=${draft.title.urlEncoded()}")
                        draft.authors.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
                            add("author=${it.urlEncoded()}")
                        }
                    }.joinToString("&"),
                    limit,
                ),
            )
        }

        queries.forEach { url ->
            val response = getText(url)
            val docs = json.decodeFromString(OpenLibrarySearchResponse.serializer(), response).docs
            if (docs.isNotEmpty()) return docs
        }

        return emptyList()
    }

    private fun OpenLibrarySearchDoc.toCandidate(
        book: LibraryBook,
        current: BookMetadataDraft,
        index: Int,
        includeDescription: Boolean,
        includeCover: Boolean,
    ): MetadataLookupCandidate? {
        val cleanTitle = title?.cleanOptional() ?: return null
        val workDescription = if (includeDescription) key?.let(::fetchWorkDescription) else null
        val cleanAuthors = authorName.mapNotNull { it.cleanOptional() }
        val cleanSeriesName = seriesName.firstCleanOptional()
        val cleanSeriesIndex = seriesPosition
            .asSequence()
            .mapNotNull { it.toSeriesIndexOrNull() }
            .firstOrNull()
        val resolvedSeriesName = cleanSeriesName ?: current.seriesName?.cleanOptional()
        val cleanTags = subject
            .asSequence()
            .mapNotNull { it.cleanOptional() }
            .filterNot { it.isOpenLibrarySeriesSubject() }
            .filterNot { tag -> resolvedSeriesName != null && tag.isSeriesTagFor(resolvedSeriesName) }
            .filterNot { it.length > 42 }
            .distinctBy { it.lowercase() }
            .take(8)
            .toList()
        val coverPath = if (includeCover) {
            downloadCover(
                bookId = book.book.id,
                candidateId = key?.substringAfterLast('/') ?: "open-library-$index",
                coverId = coverId,
                isbn = isbn.bestIsbn(),
            )
        } else {
            null
        }
        val identifiers = current.identifiers +
            listOfNotNull(
                key?.let { "openlibrary_work" to it },
                editionKeys.firstOrNull()?.let { "openlibrary_edition" to it },
                seriesKeys.firstOrNull()?.let { "openlibrary_series" to it },
            )

        return MetadataLookupCandidate(
            id = key ?: "open-library-$index",
            sourceName = "Open Library",
            metadata = BookMetadataDraft(
                title = cleanTitle,
                subtitle = current.subtitle,
                authors = cleanAuthors.ifEmpty { current.authors },
                sortAuthor = current.sortAuthor ?: cleanAuthors.firstOrNull()?.toSortAuthor(),
                language = language.firstOrNull()?.toIso639Code() ?: current.language,
                publisher = publisher.firstOrNull()?.cleanOptional() ?: current.publisher,
                publishedDate = firstPublishYear?.toString() ?: current.publishedDate,
                description = workDescription ?: current.description,
                tags = cleanTags.ifEmpty { current.tags },
                isbn = isbn.bestIsbn() ?: current.bestIsbn(),
                identifiers = identifiers,
                seriesName = resolvedSeriesName,
                seriesIndex = cleanSeriesIndex ?: current.seriesIndex,
            ),
            coverImagePath = coverPath,
            sourceUrl = key?.let { "https://openlibrary.org$it" },
        )
    }

    private fun fetchWorkDescription(workKey: String): String? {
        return runCatching {
            val response = getText("https://openlibrary.org$workKey.json")
            json.decodeFromString(OpenLibraryWorkResponse.serializer(), response)
                .description
                .descriptionText()
                ?.toDisplayText()
        }.getOrNull()
    }

    private fun downloadCover(
        bookId: String,
        candidateId: String,
        coverId: Long?,
        isbn: String?,
    ): String? {
        val coverUrl = when {
            coverId != null -> "https://covers.openlibrary.org/b/id/$coverId-L.jpg?default=false"
            isbn != null -> "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg?default=false"
            else -> return null
        }
        return runCatching {
            val outputDir = File(appContext.cacheDir, "metadata-covers/$bookId").apply { mkdirs() }
            val outputFile = File(outputDir, "${candidateId.sanitizeFileName()}.jpg")
            val connection = (URL(coverUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = SECONDARY_CONNECT_TIMEOUT_MS
                readTimeout = SECONDARY_READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (connection.responseCode !in 200..299) {
                connection.disconnect()
                return@runCatching null
            }
            connection.inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            connection.disconnect()
            outputFile.takeIf { it.length() > 0L }?.absolutePath
        }.getOrNull()
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
            error("Open Library request failed: HTTP $responseCode")
        }
        return responseText
    }

    private fun openLibrarySearchUrl(query: String, limit: Int): String {
        return "https://openlibrary.org/search.json?$query&limit=$limit&fields=$SEARCH_FIELDS"
    }

    private fun JsonElement?.descriptionText(): String? {
        return when (this) {
            is JsonPrimitive -> contentOrNull
            is JsonObject -> get("value")?.jsonPrimitive?.contentOrNull
            else -> null
        }?.cleanOptional()
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

    private fun List<String>.firstCleanOptional(): String? {
        return asSequence()
            .mapNotNull { it.cleanOptional() }
            .firstOrNull()
    }

    private fun String.toSeriesIndexOrNull(): Double? {
        val normalized = trim().replace(',', '.')
        return normalized.toDoubleOrNull()
            ?: Regex("""(?i)(?:book|volume|vol\.?|#)?\s*(\d+(?:[.]\d+)?)""")
                .find(normalized)
                ?.groupValues
                ?.getOrNull(1)
                ?.toDoubleOrNull()
    }

    private fun String.isOpenLibrarySeriesSubject(): Boolean {
        return startsWith("series:", ignoreCase = true)
    }

    private fun String.isSeriesTagFor(seriesName: String): Boolean {
        val tag = normalizeSeriesToken()
        val series = seriesName.normalizeSeriesToken()
        return tag == series ||
            tag == "$series series" ||
            (tag.startsWith("$series ") && tag.contains("series"))
    }

    private fun String.normalizeSeriesToken(): String {
        return lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun String.urlEncoded(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun String.sanitizeFileName(): String {
        return replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifBlank { "cover" }
    }

    private fun String.toSortAuthor(): String {
        val parts = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return if (parts.size <= 1) this else "${parts.last()}, ${parts.dropLast(1).joinToString(" ")}"
    }

    private fun String.toIso639Code(): String {
        return when (lowercase()) {
            "eng" -> "en"
            "por" -> "pt"
            "spa" -> "es"
            "fre", "fra" -> "fr"
            "ger", "deu" -> "de"
            "ita" -> "it"
            "jpn" -> "ja"
            "chi", "zho" -> "zh"
            "rus" -> "ru"
            else -> lowercase()
        }
    }

    private fun List<String>.bestIsbn(): String? {
        return asSequence()
            .mapNotNull { it.extractIsbn() }
            .sortedByDescending { it.length }
            .firstOrNull()
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
    private data class OpenLibrarySearchResponse(
        val docs: List<OpenLibrarySearchDoc> = emptyList(),
    )

    @Serializable
    private data class OpenLibrarySearchDoc(
        val key: String? = null,
        val title: String? = null,
        @SerialName("author_name") val authorName: List<String> = emptyList(),
        @SerialName("first_publish_year") val firstPublishYear: Int? = null,
        val publisher: List<String> = emptyList(),
        val language: List<String> = emptyList(),
        val isbn: List<String> = emptyList(),
        val subject: List<String> = emptyList(),
        @SerialName("series_name") val seriesName: List<String> = emptyList(),
        @SerialName("series_position") val seriesPosition: List<String> = emptyList(),
        @SerialName("series_key") val seriesKeys: List<String> = emptyList(),
        @SerialName("cover_i") val coverId: Long? = null,
        @SerialName("edition_key") val editionKeys: List<String> = emptyList(),
    )

    @Serializable
    private data class OpenLibraryWorkResponse(
        val description: JsonElement? = null,
    )

    private companion object {
        const val USER_AGENT = "Foliore/0.1 metadata lookup"
        const val SEARCH_CONNECT_TIMEOUT_MS = 10_000
        const val SEARCH_READ_TIMEOUT_MS = 12_000
        const val SECONDARY_CONNECT_TIMEOUT_MS = 4_000
        const val SECONDARY_READ_TIMEOUT_MS = 6_000
        const val LOOKUP_TIMEOUT_MS = 18_000L
        const val SEARCH_FIELDS =
            "key,title,author_name,first_publish_year,publisher,language,isbn,subject,series_name,series_position,series_key,cover_i,edition_key"
    }
}
