package com.foliore.android.debug

import android.content.Context
import com.foliore.android.di.AppContainer
import com.foliore.android.metadata.*
import com.foliore.shared.domain.BookMetadataDraft
import java.io.File
import kotlinx.serialization.json.*

/** Read-only live-provider QA. Never applies a candidate, saves metadata or sends an EPUB. */
internal object MetadataLookupHarness {
    fun run(context: Context, bookId: String, title: String? = null, author: String? = null, isbn: String? = null) {
        val app = AppContainer(context.applicationContext)
        val file = File(context.cacheDir, "metadata-qa-result.json")
        file.writeText("{\"status\":\"RUNNING\"}")
        val book = checkNotNull(app.libraryRepository.getBook(bookId))
        val m = book.metadata
        val original = BookMetadataDraft(m.title, m.subtitle, m.authors, m.sortAuthor, m.language, m.publisher,
            m.publishedDate, m.description, m.tags, m.isbn, m.identifiers, m.seriesName, m.seriesIndex)
        val draft = if (title == null) original else original.copy(title = title, authors = listOfNotNull(author),
            isbn = isbn, identifiers = emptyMap(), subtitle = null, seriesName = null, seriesIndex = null, language = "en")
        val start = System.nanoTime()
        val result = app.openMetadataLookupService.search(book, draft)
        val searchMillis = (System.nanoTime() - start) / 1_000_000
        val resolved = (result as? MetadataLookupResult.Success)?.candidates?.firstOrNull()?.let { app.openMetadataLookupService.resolve(book, it) }
        file.writeText(buildJsonObject {
            put("title", draft.title)
            put("elapsedMillis", (System.nanoTime() - start) / 1_000_000)
            put("searchMillis", searchMillis)
            resolved?.let { candidate -> put("resolved", buildJsonObject {
                put("title", candidate.metadata.title); put("authors", candidate.metadata.authors.joinToString(", "))
                put("language", candidate.metadata.language); put("languageDisplay", MetadataLanguages.display(candidate.metadata.language, java.util.Locale.forLanguageTag("pt-BR")))
                put("publisher", candidate.metadata.publisher); put("date", candidate.metadata.publishedDate)
                put("series", candidate.metadata.seriesName); put("seriesIndex", candidate.metadata.seriesIndex)
                put("fields", JsonArray(MetadataField.entries.filter { it.available(candidate.metadata) }.map { JsonPrimitive(it.name) }))
                put("descriptionLength", candidate.metadata.description?.length ?: 0)
                put("cover", candidate.coverImagePath != null)
                put("defaultCoverSelected", MetadataField.COVER in MetadataField.defaultSelection)
            }) }
            put("googleBooksConfigured", app.metadataPreferenceRepository.hasGoogleBooksApiKey.value)
            when (result) {
                is MetadataLookupResult.Failure -> { put("status", "FAILURE"); put("reason", result.message) }
                is MetadataLookupResult.Success -> {
                    put("status", "SUCCESS")
                    put("candidates", JsonArray(result.candidates.map { candidate -> buildJsonObject {
                        put("title", candidate.metadata.title); put("source", candidate.sourceName)
                        put("isbn", candidate.metadata.isbn); put("language", candidate.metadata.language)
                        put("publisher", candidate.metadata.publisher); put("year", candidate.metadata.publishedDate)
                        put("series", candidate.metadata.seriesName); put("seriesIndex", candidate.metadata.seriesIndex)
                        put("descriptionLength", candidate.metadata.description?.length ?: 0)
                        put("cover", candidate.coverImagePath != null); put("sourceUrl", candidate.sourceUrl)
                    } }))
                }
            }
        }.toString())
    }
}
