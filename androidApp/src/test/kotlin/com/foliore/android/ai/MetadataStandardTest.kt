package com.foliore.android.ai

import com.foliore.android.viewmodel.libraryTags
import com.foliore.shared.domain.Book
import com.foliore.shared.domain.BookMetadata
import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.domain.BookStatus
import com.foliore.shared.domain.LibraryBook
import com.foliore.shared.domain.MetadataSource
import com.foliore.shared.domain.SyncStatus
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataStandardTest {
    private fun draft(language: String?, tags: List<String>) = BookMetadataDraft(
        title = "2010: Odyssey Two",
        subtitle = null,
        authors = listOf("Arthur C. Clarke"),
        sortAuthor = null,
        language = language,
        publisher = null,
        publishedDate = null,
        description = null,
        tags = tags,
        isbn = null,
        identifiers = emptyMap(),
        seriesName = null,
        seriesIndex = null,
    )

    @Test
    fun languageBecomesBcp47Code() {
        assertEquals("en", MetadataStandard.standardize(draft("English", emptyList()), emptyList()).language)
        assertEquals("pt-BR", MetadataStandard.standardize(draft("pt_BR", emptyList()), emptyList()).language)
        assertEquals("fr", MetadataStandard.standardize(draft("fre", emptyList()), emptyList()).language)
    }

    @Test
    fun tagsReuseTheLibrarySpelling() {
        val result = MetadataStandard.standardize(
            draft("pt-BR", listOf("ficcao cientifica", "exploração espacial.", "Ficção Científica")),
            libraryTags = listOf("Ficção científica"),
        )
        assertEquals(listOf("Ficção científica", "Exploração espacial"), result.tags)
    }

    @Test
    fun tagsAreCappedAtSix() {
        val result = MetadataStandard.standardize(draft("en", (1..9).map { "Tag $it" }), emptyList())
        assertEquals(MetadataStandard.MAX_TAGS, result.tags.size)
    }

    @Test
    fun instructionsFollowTheChosenLanguage() {
        val book = MetadataStandard.instructions(textLanguageTag = null, libraryTags = emptyList())
        assertTrue(book.contains("book's own language"))
        assertTrue(book.contains("Thema"))
        assertFalse(book.contains("already used in this library"))

        val app = MetadataStandard.instructions(textLanguageTag = "pt-BR", libraryTags = listOf("Fantasia", "Romance"))
        assertTrue(app.contains("Portuguese (Brazil) (pt-BR)"))
        assertTrue(app.contains("Tags already used in this library: Fantasia, Romance."))
    }

    @Test
    fun appLanguageIsOneTheAppSpeaks() {
        assertEquals("pt-BR", AiTextLanguage.appLanguageTag(Locale.forLanguageTag("pt-PT")))
        assertEquals("en", AiTextLanguage.appLanguageTag(Locale.forLanguageTag("en-US")))
        assertEquals("zh-CN", AiTextLanguage.appLanguageTag(Locale.forLanguageTag("zh-CN")))
        assertEquals("en", AiTextLanguage.appLanguageTag(Locale.forLanguageTag("sv-SE")))
    }

    @Test
    fun libraryTagsAreMostUsedFirstWithoutDuplicates() {
        fun book(id: String, vararg tags: String) = LibraryBook(
            book = Book(
                id = id, originalFilePath = "", preparedFilePath = null, coverImagePath = null, coverThumbPath = null,
                originalHashSha256 = id, fileSizeBytes = 0, importedAt = "", updatedAt = "", lastPreparedAt = null,
                lastExportedAt = null, lastSyncedAt = null, status = BookStatus.IMPORTED,
                syncStatus = SyncStatus.LOCAL_ONLY, revision = 0, updatedByDeviceId = "", technicalStatus = null,
            ),
            metadata = BookMetadata(
                bookId = id, title = id, subtitle = null, authors = emptyList(), sortAuthor = null, language = null,
                publisher = null, publishedDate = null, description = null, tags = tags.toList(), isbn = null,
                identifiers = emptyMap(), seriesName = null, seriesIndex = null, source = MetadataSource.EPUB,
            ),
        )
        val tags = listOf(
            book("a", "Fantasy", "Magic"),
            book("b", "Fantasy", " "),
            book("c", "fantasy", "Science fiction"),
        ).libraryTags()
        assertEquals(listOf("Fantasy", "Magic", "Science fiction"), tags)
    }
}
