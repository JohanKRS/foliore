package com.foliore.android.viewmodel

import com.foliore.android.ai.AiCoverPromptRecord
import com.foliore.android.ai.AiProviderMode
import com.foliore.android.storage.GoogleDriveBackupStats
import com.foliore.android.storage.GoogleDriveRemoteLibraryRecovery
import com.foliore.android.storage.KindleEmailSettings
import com.foliore.android.storage.SyncProvider
import com.foliore.shared.domain.LibraryBook

data class LibraryUiState(
    val books: List<LibraryBook> = emptyList(),
    val importLimitAvailable: Int? = null,
    val importLimitSkipped: Int = 0,
    val isImporting: Boolean = false,
    val importProgress: SyncProgress? = null,
    val pendingImportConflicts: List<ImportDuplicateConflict> = emptyList(),
    val isSyncing: Boolean = false,
    val syncProgress: SyncProgress? = null,
    val syncProvider: SyncProvider = SyncProvider.NONE,
    val syncFolderLabel: String? = null,
    val googleDriveAccountLabel: String? = null,
    val isGoogleDriveConnected: Boolean = false,
    val googleDriveBackupStats: GoogleDriveBackupStats? = null,
    val googleDriveRemoteLibraryRecovery: GoogleDriveRemoteLibraryRecovery? = null,
    val isGoogleDriveBackupStatsLoading: Boolean = false,
    val isAutoSyncEnabled: Boolean = false,
    val isAutoSyncWifiOnly: Boolean = true,
    val lastSyncSummary: SyncSummary? = null,
    val isSearchOpen: Boolean = false,
    val query: String = "",
    val activeFilter: BookFilter = BookFilter.ALL,
    val sortOrder: BookSortOrder = BookSortOrder.IMPORTED_NEWEST,
    val seriesFilter: SeriesFilter = SeriesFilter.ALL,
    val gridColumns: BookGridColumns = BookGridColumns.AUTO,
    val displayOptions: LibraryDisplayOptions = LibraryDisplayOptions(),
    val isBatchMode: Boolean = false,
    val selectedBookIds: Set<String> = emptySet(),
    val aiProviderMode: AiProviderMode = AiProviderMode.DISABLED,
    val hasOpenAiApiKey: Boolean = false,
    val aiCoverPromptRecords: List<AiCoverPromptRecord> = emptyList(),
    val kindleBatchReport: com.foliore.android.storage.KindleBatchReport? = null,
    val isGmailConnecting: Boolean = false,
    val kindleEmailSettings: KindleEmailSettings = KindleEmailSettings(
        kindleEmail = null,
        senderEmail = null,
        smtpHost = "smtp.gmail.com",
        smtpPort = 587,
        smtpUsername = null,
        hasSmtpPassword = false,
    ),
) {
    val activeProgress: SyncProgress?
        get() = syncProgress ?: importProgress

    val hasActiveProgress: Boolean
        get() = isSyncing || isImporting || syncProgress != null || importProgress != null
}

data class ImportDuplicateConflict(
    val token: String,
    val existingTitle: String,
    val existingAuthors: List<String>,
    val incomingTitle: String,
    val incomingAuthors: List<String>,
)

enum class ImportDuplicateChoice {
    KEEP_LIBRARY,
    USE_IMPORTED,
}

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

data class LibraryDisplayOptions(
    val showTitle: Boolean = true,
    val showAuthor: Boolean = true,
    val showSeries: Boolean = true,
)

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
    IMPORTING,
    PREPARING,
    DELETING_REMOTE,
    CHECKING_REMOTE,
    DOWNLOADING,
    UPLOADING,
    WRITING_MANIFEST,
    APPLYING_STATUS,
    GENERATING_METADATA,
    GENERATING_COVERS,
    LOOKING_UP_METADATA,
    SENDING_TO_KINDLE,
}

data class LibraryNotice(
    val type: LibraryNoticeType,
)

enum class LibraryNoticeType {
    IMPORTED,
    IMPORT_DRM_PROTECTED,
    IMPORT_CONVERSION_FAILED,
    IMPORT_TOO_LARGE,
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
    BATCH_DELETED,
    BATCH_DELETE_FAILED,
    PREPARE_FAILED,
    EXPORT_FAILED,
    SYNC_FOLDER_CONFIGURED,
    SYNC_EXISTING_LIBRARY_CONFIGURED,
    SYNC_COMPLETE,
    SYNC_COMPLETE_WITH_CONFLICTS,
    SYNC_NOT_CONFIGURED,
    SYNC_FOLDER_UNAVAILABLE,
    SYNC_FOLDER_LOOKS_LIKE_SUBFOLDER,
    SYNC_GOOGLE_DRIVE_CONNECTED,
    SYNC_GOOGLE_DRIVE_AUTH_REQUIRED,
    SYNC_GOOGLE_DRIVE_CONFIGURATION_REQUIRED,
    SYNC_GOOGLE_DRIVE_QUOTA_EXCEEDED,
    SYNC_GOOGLE_DRIVE_UNAVAILABLE,
    SYNC_GOOGLE_DRIVE_BACKUP_DELETED,
    SYNC_AUTO_SETTINGS_UPDATED,
    SYNC_FAILED,
    SYNC_DISCONNECTED,
    AI_SETTINGS_UPDATED,
    AI_API_KEY_SAVED,
    AI_API_KEY_CLEARED,
    AI_PROVIDER_REQUIRED,
    AI_PROVIDER_NOT_READY,
    AI_REQUEST_FAILED,
    AI_METADATA_PREVIEW_READY,
    METADATA_LOOKUP_READY,
    METADATA_LOOKUP_EMPTY,
    METADATA_LOOKUP_FAILED,
    AI_COVER_PROVIDER_REQUIRED,
    AI_COVER_OPTIONS_READY,
    AI_COVER_PROMPT_SAVED,
    KINDLE_EMAIL_SAVED,
    KINDLE_EMAIL_CLEARED,
    KINDLE_EMAIL_INVALID,
    KINDLE_EMAIL_MISSING,
    KINDLE_SMTP_SAVED,
    KINDLE_SMTP_CLEARED,
    KINDLE_SMTP_INVALID,
    GMAIL_CONNECTED,
    GMAIL_DISCONNECTED,
    GMAIL_AUTH_REQUIRED,
    GMAIL_CONFIGURATION_REQUIRED,
    GMAIL_UNAVAILABLE,
    GMAIL_TOO_LARGE,
    GMAIL_RATE_LIMITED,
    GMAIL_SEND_UNKNOWN,
    KINDLE_EMAIL_SENT,
    KINDLE_EMAIL_FAILED,
}
