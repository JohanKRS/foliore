package com.foliore.android.metadata

import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.domain.LibraryBook

class OpenMetadataLookupService(
    private val openLibraryMetadataService: OpenLibraryMetadataService,
    private val googleBooksMetadataService: GoogleBooksMetadataService,
) {
    fun search(
        book: LibraryBook,
        draft: BookMetadataDraft,
        limit: Int = 8,
    ): MetadataLookupResult {
        val googleCandidates = googleBooksMetadataService.search(
            book = book,
            draft = draft,
            limit = GOOGLE_BOOKS_LIMIT,
        ).candidatesOrEmpty()
        val openLibraryCandidates = openLibraryMetadataService.search(
            book = book,
            draft = draft,
            limit = OPEN_LIBRARY_LIMIT,
        ).candidatesOrEmpty()
        val candidates = (googleCandidates + openLibraryCandidates)
            .distinctBy { it.lookupDedupeKey() }
            .take(limit)

        return MetadataLookupResult.Success(candidates)
    }

    private fun MetadataLookupResult.candidatesOrEmpty(): List<MetadataLookupCandidate> {
        return when (this) {
            is MetadataLookupResult.Success -> candidates
            is MetadataLookupResult.Failure -> emptyList()
        }
    }

    private fun MetadataLookupCandidate.lookupDedupeKey(): String {
        val isbn = metadata.isbn?.filter { it.isDigit() || it == 'X' || it == 'x' }?.uppercase()
        if (!isbn.isNullOrBlank()) return "isbn:$isbn"
        val authors = metadata.authors.joinToString(" ")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
        val title = metadata.title
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
        return "$title|$authors"
    }

    private companion object {
        const val GOOGLE_BOOKS_LIMIT = 4
        const val OPEN_LIBRARY_LIMIT = 5
    }
}
