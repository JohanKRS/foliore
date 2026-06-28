package com.foliore.android.storage

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri

class SyncService(
    private val manualSyncService: ManualSyncService,
    private val googleDriveSyncService: GoogleDriveSyncService,
    private val googleDriveAuthService: GoogleDriveAuthService,
    private val syncSettingsRepository: SyncSettingsRepository,
) {
    fun activeProvider(): SyncProvider {
        return syncSettingsRepository.activeProvider()
    }

    fun syncLabel(): String? {
        return when (syncSettingsRepository.activeProvider()) {
            SyncProvider.MANUAL_FOLDER -> manualSyncService.folderLabel()
            SyncProvider.GOOGLE_DRIVE_APPDATA -> {
                syncSettingsRepository.googleDriveAccountLabel()
                    ?: SyncSettingsRepository.GOOGLE_DRIVE_DEFAULT_LABEL
            }
            SyncProvider.NONE -> null
        }
    }

    fun isGoogleDriveConnected(): Boolean {
        return syncSettingsRepository.googleDriveAccountLabel() != null ||
            syncSettingsRepository.activeProvider() == SyncProvider.GOOGLE_DRIVE_APPDATA
    }

    fun googleDriveAccountLabel(): String? {
        return syncSettingsRepository.googleDriveAccountLabel()
    }

    fun autoSyncEnabled(): Boolean {
        return syncSettingsRepository.isAutoSyncEnabled()
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        syncSettingsRepository.setAutoSyncEnabled(enabled)
    }

    fun autoSyncWifiOnly(): Boolean {
        return syncSettingsRepository.isAutoSyncWifiOnly()
    }

    fun setAutoSyncWifiOnly(enabled: Boolean) {
        syncSettingsRepository.setAutoSyncWifiOnly(enabled)
    }

    fun syncNow(onProgress: (ManualSyncProgress) -> Unit = {}): ManualSyncResult {
        return when (syncSettingsRepository.activeProvider()) {
            SyncProvider.MANUAL_FOLDER -> manualSyncService.syncNow(onProgress)
            SyncProvider.GOOGLE_DRIVE_APPDATA -> googleDriveSyncService.syncNow(onProgress)
            SyncProvider.NONE -> ManualSyncResult.NotConfigured
        }
    }

    fun configureManualFolder(uri: Uri): ManualSyncResult {
        return manualSyncService.configureFolder(uri)
    }

    fun disconnectActiveProvider() {
        when (syncSettingsRepository.activeProvider()) {
            SyncProvider.MANUAL_FOLDER -> manualSyncService.disconnect()
            SyncProvider.GOOGLE_DRIVE_APPDATA -> syncSettingsRepository.disconnectGoogleDrive()
            SyncProvider.NONE -> Unit
        }
    }

    fun beginGoogleDriveConnection(): GoogleDriveConnectionResult {
        return when (val authorization = googleDriveAuthService.authorize()) {
            is GoogleDriveAuthorization.Authorized -> {
                syncSettingsRepository.setGoogleDriveConnected(accountLabel = null)
                GoogleDriveConnectionResult.Connected
            }
            is GoogleDriveAuthorization.NeedsResolution -> {
                GoogleDriveConnectionResult.NeedsResolution(authorization.pendingIntent)
            }
            GoogleDriveAuthorization.Failed -> GoogleDriveConnectionResult.Failed
        }
    }

    fun completeGoogleDriveConnection(data: Intent?): GoogleDriveConnectionResult {
        return when (val authorization = googleDriveAuthService.authorizationFromIntent(data)) {
            is GoogleDriveAuthorization.Authorized -> {
                syncSettingsRepository.setGoogleDriveConnected(accountLabel = null)
                GoogleDriveConnectionResult.Connected
            }
            is GoogleDriveAuthorization.NeedsResolution -> {
                GoogleDriveConnectionResult.NeedsResolution(authorization.pendingIntent)
            }
            GoogleDriveAuthorization.Failed -> GoogleDriveConnectionResult.Failed
        }
    }

    fun deleteGoogleDriveBackup(): GoogleDriveConnectionResult {
        return when (val authorization = googleDriveAuthService.authorize()) {
            is GoogleDriveAuthorization.Authorized -> {
                val result = googleDriveSyncService.deleteBackup(authorization.accessToken)
                if (result is ManualSyncResult.Synced) {
                    GoogleDriveConnectionResult.DeletedBackup
                } else {
                    GoogleDriveConnectionResult.SyncFailed(result)
                }
            }
            is GoogleDriveAuthorization.NeedsResolution -> {
                GoogleDriveConnectionResult.NeedsResolution(authorization.pendingIntent)
            }
            GoogleDriveAuthorization.Failed -> GoogleDriveConnectionResult.Failed
        }
    }

    fun googleDriveBackupStats(): GoogleDriveBackupStatsResult {
        return when (val authorization = googleDriveAuthService.authorize()) {
            is GoogleDriveAuthorization.Authorized -> {
                googleDriveSyncService.backupStats(authorization.accessToken)
            }
            is GoogleDriveAuthorization.NeedsResolution -> {
                GoogleDriveBackupStatsResult.NeedsResolution(authorization.pendingIntent)
            }
            GoogleDriveAuthorization.Failed -> {
                GoogleDriveBackupStatsResult.SyncFailed(ManualSyncResult.GoogleDriveUnavailable)
            }
        }
    }

    fun hasConfiguredManualFolder(): Boolean {
        return manualSyncService.hasConfiguredFolder()
    }
}

sealed interface GoogleDriveConnectionResult {
    data object Connected : GoogleDriveConnectionResult
    data object DeletedBackup : GoogleDriveConnectionResult
    data object Failed : GoogleDriveConnectionResult
    data class SyncFailed(val result: ManualSyncResult) : GoogleDriveConnectionResult
    data class NeedsResolution(val pendingIntent: PendingIntent) : GoogleDriveConnectionResult
}
