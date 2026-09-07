package com.foliore.android.metadata

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
    MetadataSearchRules.bestIsbn(draft)?.let { isbn ->
        try {
            val edition = Json.parseToJsonElement(getText("https://openlibrary.org/isbn/$isbn.json")).jsonObject
            openLibraryEdition(edition, draft, requestedIsbn = isbn)?.let { return MetadataLookupResult.Success(listOf(it)) }
        } catch (error: Exception) {
            if (error !is MetadataHttpException || error.status != 404) failure = error
            if (error is MetadataHttpException && error.status == 429) return MetadataLookupResult.Failure(error.message)
        }
    }
    val author = draft.authors.firstOrNull().orEmpty()
    val titles = MetadataSearchRules.titleVariants(draft.title).take(2)
    val queries = (titles.map { "title=${it.urlEncoded()}" + if (author.isBlank()) "" else "&author=${author.urlEncoded()}" } +
        titles.lastOrNull()?.let { listOf("title=${it.urlEncoded()}") }.orEmpty()).distinct().take(3)
    val found = mutableListOf<MetadataLookupCandidate>()
    for (query in queries) {
        try {
            val lang = MetadataSearchRules.language(draft.language)?.let { "&lang=${it.urlEncoded()}" }.orEmpty()
            val root = Json.parseToJsonElement(getText("https://openlibrary.org/search.json?$query&limit=12&fields=key,title,author_name,subject,cover_i,editions,editions.key,editions.title,editions.language,editions.isbn,editions.cover_i$lang")).jsonObject
            successfulResponse = true
            found += root.items("docs").mapNotNull { item ->
                val doc = item as? JsonObject ?: return@mapNotNull null
                val title = doc.text("title") ?: return@mapNotNull null
                val workKey = doc.text("key")?.safeWorkKey() ?: return@mapNotNull null
                val selected = doc.obj("editions")?.items("docs")?.firstOrNull() as? JsonObject
                val editionKey = selected?.text("key")?.safeEditionKey()
                val isbns = selected?.strings("isbn").orEmpty().mapNotNull(MetadataSearchRules::isbn).toSet()
                val authors = doc.strings("author_name")
                val cover = selected?.text("cover_i") ?: doc.text("cover_i")
                MetadataLookupCandidate(
                    id = "openlibrary:${editionKey ?: workKey}", sourceName = "Open Library", coverImagePath = null,
                    sourceUrl = "https://openlibrary.org${editionKey ?: workKey}", editionKey = editionKey, workKey = workKey,
                    coverUrls = cover?.takeIf { it.toLongOrNull()?.let { n -> n > 0 } == true }?.let { listOf("https://covers.openlibrary.org/b/id/$it.jpg?default=false") }.orEmpty(),
                    matchedIsbns = isbns, sourceLanguage = selected?.strings("language")?.firstOrNull()?.let(MetadataSearchRules::language),
                    metadata = draft.copy(
                        title = selected?.text("title") ?: title, authors = authors.ifEmpty { draft.authors },
                        sortAuthor = if (authors.isEmpty() || authors == draft.authors) draft.sortAuthor else authors.first().sortAuthor(),
                        // Work-level ISBN/language/publisher arrays mix different editions.
                        language = selected?.strings("language")?.firstOrNull()?.let(MetadataSearchRules::language) ?: draft.language,
                        isbn = isbns.sorted().firstOrNull() ?: draft.isbn,
                        tags = doc.strings("subject").filter { it.length <= 42 && !it.startsWith("series:", true) }.distinct().take(8).ifEmpty { draft.tags },
                        identifiers = draft.identifiers + mapOf("openlibrary_work" to workKey) + (editionKey?.let { mapOf("openlibrary_edition" to it) } ?: emptyMap()),
                    ),
                )
            }
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

internal fun openLibraryEdition(edition: JsonObject, current: BookMetadataDraft, requestedIsbn: String? = null): MetadataLookupCandidate? {
    val key = edition.text("key")?.safeEditionKey() ?: return null
    val title = edition.text("title") ?: return null
    val isbns = (edition.strings("isbn_13") + edition.strings("isbn_10")).mapNotNull(MetadataSearchRules::isbn).toSet()
    if (requestedIsbn != null && isbns.isNotEmpty() && requestedIsbn !in isbns) return null
    val isbn = requestedIsbn ?: isbns.sorted().firstOrNull()
    val work = edition.items("works").mapNotNull { (it as? JsonObject)?.text("key")?.safeWorkKey() }.firstOrNull()
    val lang = edition.items("languages").mapNotNull { (it as? JsonObject)?.text("key")?.let(MetadataSearchRules::language) }.firstOrNull()
    val cover = edition.items("covers").mapNotNull { (it as? JsonPrimitive)?.longOrNull }.firstOrNull { it > 0 }
    return MetadataLookupCandidate(
        id = "openlibrary:$key", sourceName = "Open Library", coverImagePath = null, sourceUrl = "https://openlibrary.org$key",
        matchedIsbns = isbns + listOfNotNull(requestedIsbn), editionKey = key, workKey = work, editionResolved = true, sourceLanguage = lang,
        coverUrls = cover?.let { listOf("https://covers.openlibrary.org/b/id/$it.jpg?default=false") }.orEmpty(),
        metadata = current.copy(
            title = title, subtitle = edition.text("subtitle") ?: current.subtitle,
            language = lang ?: current.language, publisher = edition.strings("publishers").firstOrNull() ?: current.publisher,
            publishedDate = edition.text("publish_date") ?: current.publishedDate,
            description = edition.description() ?: current.description,
            isbn = isbn ?: current.isbn,
            seriesName = edition.strings("series").firstOrNull() ?: current.seriesName,
            identifiers = current.identifiers + mapOf("openlibrary_edition" to key) + (work?.let { mapOf("openlibrary_work" to it) } ?: emptyMap()),
        ),
    )
}
