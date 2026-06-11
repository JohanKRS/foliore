package com.foliore.android.storage

sealed interface ManualSyncResult {
    data class Configured(val folderLabel: String) : ManualSyncResult
    data class Synced(
        val uploadedBooks: Int,
        val downloadedBooks: Int,
        val conflictedBooks: Int,
        val folderLabel: String,
    ) : ManualSyncResult
    data object NotConfigured : ManualSyncResult
    data object FolderUnavailable : ManualSyncResult
    data object Failed : ManualSyncResult
}
