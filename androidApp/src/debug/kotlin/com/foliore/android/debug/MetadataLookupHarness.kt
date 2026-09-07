package com.foliore.android.debug

import android.content.Context
import com.foliore.android.di.AppContainer
import com.foliore.android.metadata.*
import com.foliore.shared.domain.BookMetadataDraft
import java.io.File
import kotlinx.serialization.json.*

/** Read-only live-provider QA. Never applies a candidate, saves metadata or sends an EPUB. */
internal object MetadataLookupHarness {
    fun run(context: Context, bookId: String) {
        val app = AppContainer(context.applicationContext)
        val file = File(context.cacheDir, "metadata-qa-result.json")
        file.writeText("{\"status\":\"RUNNING\"}")
        val book = checkNotNull(app.libraryRepository.getBook(bookId))
        val m = book.metadata
        val draft = BookMetadataDraft(m.title, m.subtitle, m.authors, m.sortAuthor, m.language, m.publisher,
            m.publishedDate, m.description, m.tags, m.isbn, m.identifiers, m.seriesName, m.seriesIndex)
        val start = System.nanoTime()
        val result = app.openMetadataLookupService.search(book, draft)
        file.writeText(buildJsonObject {
            put("title", m.title)
            put("elapsedMillis", (System.nanoTime() - start) / 1_000_000)
            put("googleBooksConfigured", app.metadataPreferenceRepository.hasGoogleBooksApiKey.value)
            when (result) {
                is MetadataLookupResult.Failure -> { put("status", "FAILURE"); put("reason", result.message) }
                is MetadataLookupResult.Success -> {
                    put("status", "SUCCESS")
                    put("candidates", JsonArray(result.candidates.map { candidate -> buildJsonObject {
                        put("title", candidate.metadata.title); put("source", candidate.sourceName)
                        put("isbn", candidate.metadata.isbn); put("language", candidate.metadata.language)
                        put("publisher", candidate.metadata.publisher); put("year", candidate.metadata.publishedDate)
                        put("descriptionLength", candidate.metadata.description?.length ?: 0)
                        put("cover", candidate.coverImagePath != null); put("sourceUrl", candidate.sourceUrl)
                    } }))
                }
            }
        }.toString())
    }
}
