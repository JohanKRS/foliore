package com.foliore.android.metadata

import com.foliore.shared.domain.publicationDateOrNull

import android.content.Context
import com.foliore.android.storage.MetadataPreferenceRepository
import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.domain.LibraryBook
import kotlinx.serialization.json.*

class GoogleBooksMetadataService(
    @Suppress("UNUSED_PARAMETER") context: Context,
    private val metadataPreferenceRepository: MetadataPreferenceRepository,
) {
    fun search(book: LibraryBook, draft: BookMetadataDraft, limit: Int = 8): MetadataLookupResult {
        val key = metadataPreferenceRepository.googleBooksApiKey()
            ?: return MetadataLookupResult.Failure("Google Books API key is not configured", notConfigured = true)
        val http = MetadataHttpClient()
        return searchGoogleBooks(draft, limit, key, http::getText)
    }
}

internal fun googleBooksQueries(draft: BookMetadataDraft): List<String> = buildList {
    MetadataSearchRules.bestIsbn(draft)?.let { add("isbn:$it") }
    val author = draft.authors.firstOrNull()?.replace('"', ' ')?.trim().orEmpty()
    val titles = MetadataSearchRules.titleVariants(draft.title)
    titles.take(2).forEach { title ->
        add("intitle:\"${title.replace('"', ' ')}\"" + if (author.isBlank()) "" else " inauthor:\"$author\"")
    }
    titles.take(2).lastOrNull()?.let { add("intitle:\"${it.replace('"', ' ')}\"") }
}.distinct().take(4)

internal fun searchGoogleBooks(draft: BookMetadataDraft, limit: Int, key: String, getText: (String) -> String): MetadataLookupResult {
    var successfulResponse = false
    var failure: Exception? = null
    val found = mutableListOf<MetadataLookupCandidate>()
    for (query in googleBooksQueries(draft)) {
        try {
            val root = Json.parseToJsonElement(getText("https://www.googleapis.com/books/v1/volumes?q=${query.urlEncoded()}&maxResults=20&printType=books&projection=full&orderBy=relevance&key=${key.urlEncoded()}")).jsonObject
            successfulResponse = true
            found += root.items("items").mapNotNull { (it as? JsonObject)?.let { volume -> googleCandidate(volume, draft) } }
            val ranked = MetadataSearchRules.rank(found, draft, limit)
            if (ranked.size >= 4 || (!query.startsWith("isbn:") && ranked.isNotEmpty())) return MetadataLookupResult.Success(resolveGoogleSeries(ranked, key, getText))
        } catch (error: Exception) {
            failure = error
            if (error is MetadataHttpException && error.status in setOf(400, 401, 403, 429)) break
            if (Thread.currentThread().isInterrupted) break
        }
    }
    return if (successfulResponse && failure == null) MetadataLookupResult.Success(resolveGoogleSeries(MetadataSearchRules.rank(found, draft, limit), key, getText))
    else MetadataLookupResult.Failure(failure?.message)
}

internal fun googleCandidate(volume: JsonObject, current: BookMetadataDraft): MetadataLookupCandidate? {
    val info = volume.obj("volumeInfo") ?: return null
    val title = info.text("title") ?: return null
    val id = volume.text("id") ?: return null
    val authors = info.strings("authors")
    val isbns = info.items("industryIdentifiers").mapNotNull { (it as? JsonObject)?.text("identifier")?.let(MetadataSearchRules::isbn) }.toSet()
    val isbn = MetadataSearchRules.bestIsbn(current)?.takeIf { it in isbns } ?: isbns.sorted().firstOrNull()
    val links = info.obj("imageLinks")
    val covers = GoogleBooksCoverUrlNormalizer.orderedCoverUrls(links?.text("extraLarge"), links?.text("large"), links?.text("medium"), links?.text("small"), links?.text("thumbnail"), links?.text("smallThumbnail"))
    val series = catalogSeries(titles = listOfNotNull(info.text("subtitle"), title))
    val membership = info.obj("seriesInfo")?.items("volumeSeries")?.filterIsInstance<JsonObject>()?.singleOrNull()
        ?.takeIf { it.items("issue").size <= 1 }
    return MetadataLookupCandidate(
        id = "google:$id", sourceName = "Google Books", coverImagePath = null,
        sourceUrl = "https://books.google.com/books?id=${id.urlEncoded()}", matchedIsbns = isbns, coverUrls = covers, sourceLanguage = info.text("language"),
        googleSeriesId = membership?.text("seriesId"),
        googleSeriesOrder = membership?.text("orderNumber")?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 },
        metadata = emptySourceMetadata(title).copy(
            title = title, subtitle = info.text("subtitle"),
            seriesName = series?.name, seriesIndex = series?.index,
            authors = authors,
            sortAuthor = authors.firstOrNull()?.sortAuthor(),
            language = info.text("language"),
            publisher = info.text("publisher"),
            publishedDate = info.text("publishedDate").publicationDateOrNull(),
            description = info.description(),
            tags = info.strings("categories").filter { it.length <= 60 }.distinct().take(16),
            isbn = isbn,
            identifiers = mapOf("google_books_volume" to id) + (isbn?.let { mapOf("google_books_isbn" to it) } ?: emptyMap()),
        ),
    )
}

/** The volume gives the sequence; the series endpoint gives its name. Display numbers aren't sequence numbers. */
internal fun resolveGoogleSeries(candidates: List<MetadataLookupCandidate>, key: String, getText: (String) -> String): List<MetadataLookupCandidate> {
    val ids = candidates.mapNotNull { it.googleSeriesId }.distinct().take(12)
    if (ids.isEmpty()) return candidates
    return runCatching {
        val query = ids.joinToString("&") { "series_id=${it.urlEncoded()}" }
        val response = Json.parseToJsonElement(getText("https://www.googleapis.com/books/v1/series/get?$query&key=${key.urlEncoded()}")).jsonObject
        val names = response.items("series").filterIsInstance<JsonObject>().mapNotNull { item ->
            val id = item.text("seriesId") ?: return@mapNotNull null
            item.text("title")?.let { id to it }
        }.toMap()
        candidates.map { candidate ->
            val name = names[candidate.googleSeriesId]
            if (name == null) candidate else candidate.copy(metadata = candidate.metadata.withSeries(CatalogSeries(name,
                candidate.googleSeriesOrder ?: candidate.metadata.seriesIndex.takeIf { candidate.metadata.seriesName?.seriesKey() == name.seriesKey() })))
        }
    }.getOrDefault(candidates)
}
