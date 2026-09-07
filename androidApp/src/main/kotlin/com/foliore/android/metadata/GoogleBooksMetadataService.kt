package com.foliore.android.metadata

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
            val root = Json.parseToJsonElement(getText("https://www.googleapis.com/books/v1/volumes?q=${query.urlEncoded()}&maxResults=12&printType=books&projection=full&orderBy=relevance&key=${key.urlEncoded()}")).jsonObject
            successfulResponse = true
            found += root.items("items").mapNotNull { (it as? JsonObject)?.let { volume -> googleCandidate(volume, draft) } }
            val ranked = MetadataSearchRules.rank(found, draft, limit)
            if (ranked.isNotEmpty()) return MetadataLookupResult.Success(ranked)
        } catch (error: Exception) {
            failure = error
            if (error is MetadataHttpException && error.status in setOf(400, 401, 403, 429)) break
            if (Thread.currentThread().isInterrupted) break
        }
    }
    return if (successfulResponse && failure == null) MetadataLookupResult.Success(MetadataSearchRules.rank(found, draft, limit))
    else MetadataLookupResult.Failure(failure?.message)
}

private fun googleCandidate(volume: JsonObject, current: BookMetadataDraft): MetadataLookupCandidate? {
    val info = volume.obj("volumeInfo") ?: return null
    val title = info.text("title") ?: return null
    val id = volume.text("id") ?: return null
    val authors = info.strings("authors")
    val isbns = info.items("industryIdentifiers").mapNotNull { (it as? JsonObject)?.text("identifier")?.let(MetadataSearchRules::isbn) }.toSet()
    val isbn = MetadataSearchRules.bestIsbn(current)?.takeIf { it in isbns } ?: isbns.sorted().firstOrNull()
    val links = info.obj("imageLinks")
    val covers = GoogleBooksCoverUrlNormalizer.orderedCoverUrls(links?.text("extraLarge"), links?.text("large"), links?.text("medium"), links?.text("small"), links?.text("thumbnail"), links?.text("smallThumbnail"))
    return MetadataLookupCandidate(
        id = "google:$id", sourceName = "Google Books", coverImagePath = null,
        sourceUrl = "https://books.google.com/books?id=${id.urlEncoded()}", matchedIsbns = isbns, coverUrls = covers, sourceLanguage = info.text("language"),
        metadata = current.copy(
            title = title, subtitle = info.text("subtitle") ?: current.subtitle,
            authors = authors.ifEmpty { current.authors },
            sortAuthor = if (authors.isEmpty() || authors == current.authors) current.sortAuthor else authors.first().sortAuthor(),
            language = info.text("language") ?: current.language,
            publisher = info.text("publisher") ?: current.publisher,
            publishedDate = info.text("publishedDate") ?: current.publishedDate,
            description = info.description() ?: current.description,
            tags = info.strings("categories").filter { it.length <= 60 }.distinct().take(8).ifEmpty { current.tags },
            isbn = isbn ?: current.isbn,
            identifiers = current.identifiers + mapOf("google_books_volume" to id) + (isbn?.let { mapOf("google_books_isbn" to it) } ?: emptyMap()),
        ),
    )
}
