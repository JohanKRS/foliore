package com.foliore.android.storage

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SyncWorkScheduler(
    context: Context,
    private val syncSettingsRepository: SyncSettingsRepository,
) {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    fun refreshPeriodicSync() {
        if (!syncSettingsRepository.isAutoSyncEnabled()) {
            workManager.cancelUniqueWork(PERIODIC_SYNC_WORK_NAME)
            return
        }

        val request = PeriodicWorkRequestBuilder<FolioreSyncWorker>(
            repeatInterval = PERIODIC_SYNC_HOURS,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .setConstraints(syncConstraints())
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun enqueueAutoSync() {
        if (!syncSettingsRepository.isAutoSyncEnabled()) return
        val request = OneTimeWorkRequestBuilder<FolioreSyncWorker>()
            .setConstraints(syncConstraints())
            .build()
        workManager.enqueueUniqueWork(
            ONE_SHOT_SYNC_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private fun syncConstraints(): Constraints {
        val networkType = if (syncSettingsRepository.isAutoSyncWifiOnly()) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }
        return Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()
    }

    companion object {
        private const val ONE_SHOT_SYNC_WORK_NAME = "foliore_auto_sync_once"
        private const val PERIODIC_SYNC_WORK_NAME = "foliore_auto_sync_periodic"
        private const val PERIODIC_SYNC_HOURS = 6L
    }
}
