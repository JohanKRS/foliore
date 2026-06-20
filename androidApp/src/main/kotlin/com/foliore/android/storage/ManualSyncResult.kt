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
    data object Failed : ManualSyncResult
}

data class ManualSyncProgress(
    val phase: ManualSyncPhase,
    val current: Int = 0,
    val total: Int = 0,
)

enum class ManualSyncPhase {
    PREPARING,
    CHECKING_REMOTE,
    UPLOADING,
    WRITING_MANIFEST,
    APPLYING_STATUS,
}
