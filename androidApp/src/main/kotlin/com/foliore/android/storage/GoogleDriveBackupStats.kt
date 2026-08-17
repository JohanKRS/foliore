package com.foliore.android.storage

import android.app.PendingIntent
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class GoogleDriveBackupStats(
    val hasBackup: Boolean,
    val syncedBooks: Int,
    val remoteFiles: Int,
    val totalBytes: Long,
    val rootItems: Int,
    val recognizedRootItems: Int,
    val unknownRootItems: Int,
    val updatedAt: String?,
    val remoteItems: Int = 0,
    val deletionMarkerPresent: Boolean = false,
) {
    val hasStoredLibraryData: Boolean
        get() = remoteItems > 0 || remoteFiles > 0 || rootItems > 0

    val hasOrphanedFiles: Boolean
        get() = !hasBackup && hasStoredLibraryData

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

        val Deleted = Empty.copy(deletionMarkerPresent = true)
    }
}

data class GoogleDriveRemoteLibraryRecovery(
    val remainingFiles: Int,
    val remainingBytes: Long,
)

internal object GoogleDriveBackupStatsCacheCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(stats: GoogleDriveBackupStats): String {
        return json.encodeToString(stats)
    }

    fun decode(encoded: String): GoogleDriveBackupStats? {
        return runCatching {
            json.decodeFromString<GoogleDriveBackupStats>(encoded)
        }.getOrNull()
    }
}

sealed interface GoogleDriveBackupStatsResult {
    data class Loaded(val stats: GoogleDriveBackupStats) : GoogleDriveBackupStatsResult
    data class SyncFailed(val result: ManualSyncResult) : GoogleDriveBackupStatsResult
    data class NeedsResolution(val pendingIntent: PendingIntent) : GoogleDriveBackupStatsResult
}
