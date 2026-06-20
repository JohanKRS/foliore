package com.foliore.android.storage

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.foliore.android.di.AppContainer

class FolioreSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val appContainer = AppContainer(applicationContext)
        if (!appContainer.syncSettingsRepository.isAutoSyncEnabled()) {
            return Result.success()
        }

        return when (appContainer.syncService.syncNow()) {
            is ManualSyncResult.Synced,
            ManualSyncResult.NotConfigured,
            ManualSyncResult.GoogleDriveAuthorizationRequired,
            ManualSyncResult.FolderLooksLikeLibrarySubfolder,
            ManualSyncResult.FolderUnavailable -> Result.success()

            ManualSyncResult.GoogleDriveQuotaExceeded -> Result.failure()
            ManualSyncResult.GoogleDriveUnavailable,
            ManualSyncResult.Failed -> Result.retry()
            is ManualSyncResult.Configured -> Result.success()
        }
    }
}
