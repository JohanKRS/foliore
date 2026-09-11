package com.foliore.android.metadata

import com.foliore.shared.domain.BookMetadataDraft
import kotlinx.serialization.json.*

/** Bibliographic records only. No borrowing, login, or book downloads. */
internal fun searchInternetArchive(draft: BookMetadataDraft, limit: Int, getText: (String) -> String): MetadataLookupResult {
    val title = MetadataSearchRules.titleVariants(draft.title).lastOrNull().orEmpty().searchPhrase()
    if (title.isBlank()) return MetadataLookupResult.Success(emptyList())
    val author = draft.authors.firstOrNull()?.authorSearchName()?.searchPhrase()
    val queries = buildList {
        MetadataSearchRules.bestIsbn(draft)?.let { add("isbn:$it") }
        add("title:(\"$title\")" + if (author.isNullOrBlank()) "" else " AND creator:(\"$author\")")
        add("title:(\"$title\")")
    }.distinct()
    val found = mutableListOf<MetadataLookupCandidate>()
    var succeeded = false
    for (query in queries) {
        try {
            val fields = listOf("identifier", "title", "creator", "publisher", "date", "language", "isbn", "subject", "description", "series", "openlibrary_work")
                .joinToString("") { "&fl[]=$it" }
            val root = Json.parseToJsonElement(getText("https://archive.org/advancedsearch.php?q=${("mediatype:texts AND ($query)").urlEncoded()}$fields&rows=16&output=json")).jsonObject
            val response = root.obj("response") ?: error("Missing search response")
            succeeded = true
            found += response.items("docs").mapNotNull { (it as? JsonObject)?.let(::internetArchiveCandidate) }
            val ranked = MetadataSearchRules.rank(found, draft, limit)
            if (ranked.size >= 4 || (!query.startsWith("isbn:") && ranked.isNotEmpty())) return MetadataLookupResult.Success(ranked)
        } catch (error: Exception) {
            if (error is MetadataHttpException && error.status in setOf(400, 401, 403, 429)) break
            if (Thread.currentThread().isInterrupted) break
        }
    }
    val ranked = MetadataSearchRules.rank(found, draft, limit)
    return if (succeeded || ranked.isNotEmpty()) MetadataLookupResult.Success(ranked) else MetadataLookupResult.Failure()
}

internal fun internetArchiveCandidate(doc: JsonObject): MetadataLookupCandidate? {
    val id = doc.text("identifier")?.takeIf { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,199}")) } ?: return null
    val title = doc.values("title").firstOrNull()?.displayMetadataText() ?: return null
    val authors = doc.values("creator").map(String::catalogAuthor).filter(String::isNotBlank).distinct()
    val isbns = doc.values("isbn").mapNotNull(MetadataSearchRules::isbn).toSet()
    val language = doc.values("language").firstNotNullOfOrNull(MetadataLanguages::code)
    val series = catalogSeries(explicit = doc.values("series"), titles = listOfNotNull(title), subjects = doc.values("subject"))
    val descriptions = doc.values("description").map(String::displayMetadataText)
        .filter { it.length >= 80 && !it.startsWith("Includes bibliographical", true) }
    // Archive's Jan 1 timestamp represents the catalog year, not a known publication day.
    val year = doc.text("date")?.take(4)?.takeIf { it.matches(Regex("\\d{4}")) }
    return MetadataLookupCandidate("archive:$id", "Internet Archive",
        emptySourceMetadata(title).copy(authors = authors, sortAuthor = authors.firstOrNull()?.sortAuthor(),
            seriesName = series?.name, seriesIndex = series?.index,
            language = language, publisher = doc.values("publisher").firstOrNull(), publishedDate = year,
            description = descriptions.joinToString("\n\n").takeIf(String::isNotBlank),
            tags = doc.values("subject").filter { it.length <= 80 && !it.startsWith("series:", true) }.distinct().take(16),
            isbn = isbns.sorted().firstOrNull(), identifiers = mapOf("internet_archive" to id)),
        coverImagePath = null, sourceUrl = "https://archive.org/details/$id", matchedIsbns = isbns,
        sourceLanguage = language,
        workKey = doc.values("openlibrary_work").firstNotNullOfOrNull { ("/works/" + it.substringAfterLast('/')).safeWorkKey() },
        coverUrls = isbns.sorted().take(2).map { "https://covers.openlibrary.org/b/isbn/$it-L.jpg?default=false" } + "https://archive.org/services/img/$id",
    )
}

internal fun JsonObject.values(key: String): List<String> = strings(key).ifEmpty { listOfNotNull(text(key)) }
internal fun String.searchPhrase(): String = replace(Regex("[\\\\\"()]"), " ").trim()
internal fun String.authorSearchName(): String = catalogAuthor().substringAfterLast(' ').trim()
internal fun String.catalogAuthor(): String {
    val clean = replace(Regex("\\s*\\([^)]*\\d[^)]*\\)"), "")
        .replace(Regex(",?\\s*\\d{3,4}\\s*[-–].*$"), "")
        .replace(Regex("(?i),?\\s*(author|auteur|illustrator|editor|traducteur)\\.?$"), "").trim().trimEnd(',')
    val parts = clean.split(",", limit = 2).map(String::trim)
    return if (parts.size == 2 && parts.all(String::isNotBlank)) "${parts[1]} ${parts[0]}" else clean
}
