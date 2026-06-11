package com.foliore.android.viewmodel

import com.foliore.shared.domain.LibraryBook

data class LibraryUiState(
    val books: List<LibraryBook> = emptyList(),
    val isImporting: Boolean = false,
    val isSearchOpen: Boolean = false,
    val query: String = "",
    val activeFilter: BookFilter = BookFilter.ALL,
)

enum class BookFilter {
    ALL,
    NEEDS_REVIEW,
    PREPARED,
    SYNCED,
    ERROR,
}

data class LibraryNotice(
    val type: LibraryNoticeType,
)

enum class LibraryNoticeType {
    IMPORTED,
    DUPLICATE,
    FAILED,
    MIXED,
    METADATA_SAVED,
    COVER_UPDATED,
    UPDATE_FAILED,
    INVALID_TITLE,
}
