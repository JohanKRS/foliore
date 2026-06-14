package com.foliore.android.viewmodel

import com.foliore.shared.domain.LibraryBook

data class LibraryUiState(
    val books: List<LibraryBook> = emptyList(),
    val isImporting: Boolean = false,
    val isSyncing: Boolean = false,
    val syncProgress: SyncProgress? = null,
    val syncFolderLabel: String? = null,
    val lastSyncSummary: SyncSummary? = null,
    val isSearchOpen: Boolean = false,
    val query: String = "",
    val activeFilter: BookFilter = BookFilter.ALL,
    val sortOrder: BookSortOrder = BookSortOrder.IMPORTED_NEWEST,
    val seriesFilter: SeriesFilter = SeriesFilter.ALL,
    val gridColumns: BookGridColumns = BookGridColumns.AUTO,
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

enum class BookSortOrder {
    IMPORTED_NEWEST,
    TITLE_ASC,
    TITLE_DESC,
    AUTHOR_ASC,
    AUTHOR_DESC,
    YEAR_DESC,
    YEAR_ASC,
    SERIES,
}

enum class SeriesFilter {
    ALL,
    WITH_SERIES,
    WITHOUT_SERIES,
}

enum class BookGridColumns(val fixedCount: Int?) {
    AUTO(null),
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5),
    SIX(6),
}

data class SyncSummary(
    val uploadedBooks: Int,
    val downloadedBooks: Int,
    val conflictedBooks: Int,
)

data class SyncProgress(
    val phase: SyncProgressPhase,
    val current: Int = 0,
    val total: Int = 0,
) {
    val fraction: Float?
        get() = total.takeIf { it > 0 }?.let { boundedTotal ->
            current.coerceIn(0, boundedTotal).toFloat() / boundedTotal
        }
}

enum class SyncProgressPhase {
    PREPARING,
    CHECKING_REMOTE,
    UPLOADING,
    WRITING_MANIFEST,
    APPLYING_STATUS,
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
    BOOK_DELETED,
    DELETE_FAILED,
    BATCH_UPDATED,
    BATCH_FAILED,
    BATCH_NO_CHANGES,
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
