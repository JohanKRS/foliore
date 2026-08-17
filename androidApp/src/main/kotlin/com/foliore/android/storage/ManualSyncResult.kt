package com.foliore.android.storage

sealed interface ManualSyncResult {
    data class Configured(
        val folderLabel: String,
        val hasExistingLibrary: Boolean,
    ) : ManualSyncResult
    data class Synced(
        val uploadedBooks: Int,
        val downloadedBooks: Int,
        val conflictedBooks: Int,
        val folderLabel: String,
    ) : ManualSyncResult
    data object NotConfigured : ManualSyncResult
    data object FolderUnavailable : ManualSyncResult
    data object FolderLooksLikeLibrarySubfolder : ManualSyncResult
    data object GoogleDriveAuthorizationRequired : ManualSyncResult
    data object GoogleDriveConfigurationRequired : ManualSyncResult
    data object GoogleDriveQuotaExceeded : ManualSyncResult
    data object GoogleDriveUnavailable : ManualSyncResult
    data class GoogleDriveRemoteLibraryRemoved(
        val remainingFiles: Int,
        val remainingBytes: Long,
    ) : ManualSyncResult
    data object Failed : ManualSyncResult
}

data class ManualSyncProgress(
    val phase: ManualSyncPhase,
    val current: Int = 0,
    val total: Int = 0,
    val committedBookId: String? = null,
    val removedLocalBookId: String? = null,
)

enum class ManualSyncPhase {
    PREPARING,
    DELETING_REMOTE,
    CHECKING_REMOTE,
    DOWNLOADING,
    UPLOADING,
    WRITING_MANIFEST,
    APPLYING_STATUS,
}

enum class SyncProvider {
    NONE,
    MANUAL_FOLDER,
    GOOGLE_DRIVE_APPDATA,
}
