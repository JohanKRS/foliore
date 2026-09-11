package com.foliore.android.metadata

import com.foliore.shared.domain.publicationDateOrNull

import android.content.Context
import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.domain.LibraryBook
import kotlinx.serialization.json.*

class OpenLibraryMetadataService(@Suppress("UNUSED_PARAMETER") context: Context) {
    fun search(book: LibraryBook, draft: BookMetadataDraft, limit: Int = 8): MetadataLookupResult {
        val http = MetadataHttpClient()
        return searchOpenLibrary(draft, limit, http::getText)
    }
}

internal fun searchOpenLibrary(draft: BookMetadataDraft, limit: Int, getText: (String) -> String): MetadataLookupResult {
    var successfulResponse = false
    var failure: Exception? = null
    val found = mutableListOf<MetadataLookupCandidate>()
    MetadataSearchRules.bestIsbn(draft)?.let { isbn ->
        try {
            val edition = Json.parseToJsonElement(getText("https://openlibrary.org/isbn/$isbn.json")).jsonObject
            openLibraryEdition(edition, emptySourceMetadata(""), requestedIsbn = isbn)?.let { found += it }
        } catch (error: Exception) {
            if (error !is MetadataHttpException || error.status != 404) failure = error
            if (error is MetadataHttpException && error.status == 429) return MetadataLookupResult.Failure(error.message)
        }
    }
    val author = draft.authors.firstOrNull().orEmpty()
    val titles = MetadataSearchRules.titleVariants(draft.title).take(2)
    val queries = (titles.map { "title=${it.urlEncoded()}" + if (author.isBlank()) "" else "&author=${author.urlEncoded()}" } +
        titles.lastOrNull()?.let { listOf("title=${it.urlEncoded()}") }.orEmpty()).distinct().take(3)
    for (query in queries) {
        try {
            val lang = MetadataSearchRules.language(draft.language)?.let { "&lang=${it.urlEncoded()}" }.orEmpty()
            val root = Json.parseToJsonElement(getText("https://openlibrary.org/search.json?$query&limit=12&fields=key,title,author_name,author_key,subject,cover_i,editions,editions.key,editions.title,editions.subtitle,editions.series,editions.language,editions.isbn,editions.publisher,editions.publish_date,editions.cover_i$lang")).jsonObject
            successfulResponse = true
            found += root.items("docs").mapNotNull { item ->
                val doc = item as? JsonObject ?: return@mapNotNull null
                val title = doc.text("title") ?: return@mapNotNull null
                val workKey = doc.text("key")?.safeWorkKey() ?: return@mapNotNull null
                val selected = doc.obj("editions")?.items("docs")?.firstOrNull() as? JsonObject
                val editionKey = selected?.text("key")?.safeEditionKey()
                val isbns = selected?.strings("isbn").orEmpty().mapNotNull(MetadataSearchRules::isbn).toSet()
                val authors = doc.strings("author_name")
                val series = catalogSeries(explicit = selected?.values("series").orEmpty(), titles = listOfNotNull(selected?.text("subtitle"), selected?.text("title"), doc.text("title")), subjects = doc.strings("subject"))
                val cover = selected?.text("cover_i") ?: doc.text("cover_i")
                MetadataLookupCandidate(
                    id = "openlibrary:${editionKey ?: workKey}", sourceName = "Open Library", coverImagePath = null,
                    sourceUrl = "https://openlibrary.org${editionKey ?: workKey}", editionKey = editionKey, workKey = workKey,
                    workEditionCount = doc.obj("editions")?.text("numFound")?.toIntOrNull() ?: 0,
                    authorKeys = doc.strings("author_key").mapNotNull { ("/authors/" + it.substringAfterLast('/')).safeAuthorKey() },
                    coverUrls = cover?.takeIf { it.toLongOrNull()?.let { n -> n > 0 } == true }?.let { listOf("https://covers.openlibrary.org/b/id/$it-L.jpg?default=false") }.orEmpty(),
                    matchedIsbns = isbns, sourceLanguage = selected?.strings("language")?.firstOrNull()?.let(MetadataSearchRules::language),
                    metadata = emptySourceMetadata(title).copy(
                        title = (selected?.text("title") ?: title).withoutAuthorByline(authors), authors = authors,
                        seriesName = series?.name, seriesIndex = series?.index,
                        subtitle = selected?.text("subtitle"), publisher = selected?.values("publisher")?.firstOrNull(),
                        publishedDate = selected?.values("publish_date")?.firstOrNull().publicationDateOrNull(),
                        sortAuthor = authors.firstOrNull()?.sortAuthor(),
                        // Work-level ISBN/language/publisher arrays mix different editions.
                        language = selected?.strings("language")?.firstOrNull()?.let(MetadataSearchRules::language),
                        isbn = isbns.sorted().firstOrNull(),
                        tags = doc.strings("subject").filter { it.length <= 42 && !it.startsWith("series:", true) }.distinct().take(16),
                        identifiers = mapOf("openlibrary_work" to workKey) + (editionKey?.let { mapOf("openlibrary_edition" to it) } ?: emptyMap()),
                    ),
                )
            }
            val ranked = MetadataSearchRules.rank(found, draft, limit)
            if (ranked.size >= 4 || query == queries.last()) return MetadataLookupResult.Success(ranked)
        } catch (error: Exception) {
            failure = error
            if (error is MetadataHttpException && error.status in setOf(400, 401, 403, 429)) break
            if (Thread.currentThread().isInterrupted) break
        }
    }
    return if (successfulResponse && failure == null) MetadataLookupResult.Success(MetadataSearchRules.rank(found, draft, limit))
    else if (found.isNotEmpty()) MetadataLookupResult.Success(MetadataSearchRules.rank(found, draft, limit))
    else MetadataLookupResult.Failure(failure?.message)
}

internal fun openLibraryEdition(edition: JsonObject, current: BookMetadataDraft, requestedIsbn: String? = null): MetadataLookupCandidate? {
    val key = edition.text("key")?.safeEditionKey() ?: return null
    val title = edition.text("title")?.withoutAuthorByline(current.authors) ?: return null
    val isbns = (edition.strings("isbn_13") + edition.strings("isbn_10")).mapNotNull(MetadataSearchRules::isbn).toSet()
    if (requestedIsbn != null && isbns.isNotEmpty() && requestedIsbn !in isbns) return null
    val isbn = requestedIsbn ?: isbns.sorted().firstOrNull()
    val work = edition.items("works").mapNotNull { (it as? JsonObject)?.text("key")?.safeWorkKey() }.firstOrNull()
    val lang = edition.items("languages").mapNotNull { (it as? JsonObject)?.text("key")?.let(MetadataSearchRules::language) }.firstOrNull()
    val series = edition.catalogSeries()
    val cover = edition.items("covers").mapNotNull { (it as? JsonPrimitive)?.longOrNull }.firstOrNull { it > 0 }
    return MetadataLookupCandidate(
        id = "openlibrary:$key", sourceName = "Open Library", coverImagePath = null, sourceUrl = "https://openlibrary.org$key",
        matchedIsbns = isbns + listOfNotNull(requestedIsbn), editionKey = key, workKey = work, editionResolved = true, sourceLanguage = lang,
        authorKeys = edition.items("authors").mapNotNull { (it as? JsonObject)?.text("key")?.safeAuthorKey() },
        coverUrls = cover?.let { listOf("https://covers.openlibrary.org/b/id/$it-L.jpg?default=false") }.orEmpty(),
        metadata = emptySourceMetadata(title).copy(
            title = title, subtitle = edition.text("subtitle"),
            authors = current.authors, sortAuthor = current.sortAuthor,
            tags = edition.strings("subjects").filter { it.length <= 80 && !it.startsWith("series:", true) }.distinct().take(16).ifEmpty { current.tags },
            language = lang, publisher = edition.strings("publishers").firstOrNull(),
            publishedDate = edition.text("publish_date").publicationDateOrNull(),
            description = edition.description(),
            isbn = isbn,
            seriesName = series?.name, seriesIndex = series?.index,
            identifiers = mapOf("openlibrary_edition" to key) + (work?.let { mapOf("openlibrary_work" to it) } ?: emptyMap()),
        ),
    )
}
