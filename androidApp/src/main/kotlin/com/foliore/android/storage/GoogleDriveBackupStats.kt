package com.foliore.android.storage

import android.app.PendingIntent

data class GoogleDriveBackupStats(
    val hasBackup: Boolean,
    val syncedBooks: Int,
    val remoteFiles: Int,
    val totalBytes: Long,
    val rootItems: Int,
    val recognizedRootItems: Int,
    val unknownRootItems: Int,
    val updatedAt: String?,
) {
    companion object {
        val Empty = GoogleDriveBackupStats(
            hasBackup = false,
            syncedBooks = 0,
            remoteFiles = 0,
            totalBytes = 0L,
            rootItems = 0,
            recognizedRootItems = 0,
            unknownRootItems = 0,
            updatedAt = null,
        )
    }
}

sealed interface GoogleDriveBackupStatsResult {
    data class Loaded(val stats: GoogleDriveBackupStats) : GoogleDriveBackupStatsResult
    data class SyncFailed(val result: ManualSyncResult) : GoogleDriveBackupStatsResult
    data class NeedsResolution(val pendingIntent: PendingIntent) : GoogleDriveBackupStatsResult
}
