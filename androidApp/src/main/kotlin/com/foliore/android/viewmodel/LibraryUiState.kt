package com.foliore.android.viewmodel

import com.foliore.shared.domain.LibraryBook

data class LibraryUiState(
    val books: List<LibraryBook> = emptyList(),
    val isImporting: Boolean = false,
    val isSyncing: Boolean = false,
    val syncFolderLabel: String? = null,
    val lastSyncSummary: SyncSummary? = null,
    val isSearchOpen: Boolean = false,
    val query: String = "",
    val activeFilter: BookFilter = BookFilter.ALL,
    val isBatchMode: Boolean = false,
    val selectedBookIds: Set<String> = emptySet(),
)

enum class BookFilter {
    ALL,
    NEEDS_REVIEW,
    PREPARED,
    SYNCED,
    CONFLICT,
    ERROR,
}

data class SyncSummary(
    val uploadedBooks: Int,
    val downloadedBooks: Int,
    val conflictedBooks: Int,
)

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
    BOOK_DELETED,
    DELETE_FAILED,
    BATCH_UPDATED,
    BATCH_FAILED,
    BATCH_NO_CHANGES,
    EPUB_PREPARED,
    PREPARE_FAILED,
    EXPORT_FAILED,
    SYNC_FOLDER_CONFIGURED,
    SYNC_COMPLETE,
    SYNC_COMPLETE_WITH_CONFLICTS,
    SYNC_NOT_CONFIGURED,
    SYNC_FOLDER_UNAVAILABLE,
    SYNC_FAILED,
    SYNC_DISCONNECTED,
}
