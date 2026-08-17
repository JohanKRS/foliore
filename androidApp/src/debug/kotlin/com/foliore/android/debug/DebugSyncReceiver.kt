package com.foliore.android.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.foliore.android.di.AppContainer
import com.foliore.android.storage.ManualSyncProgress
import com.foliore.android.storage.ManualSyncResult

class DebugSyncReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pendingResult = goAsync()
        Thread {
            try {
                Log.d(TAG, "debug sync start")
                val result = AppContainer(context).syncService.syncNow(onProgress = ::logProgress)
                Log.d(TAG, "debug sync result=${result.toDebugString()}")
            } catch (error: Exception) {
                Log.e(TAG, "debug sync failed", error)
            } finally {
                pendingResult.finish()
            }
        }.apply {
            name = "FolioreDebugSync"
            start()
        }
    }

    private fun logProgress(progress: ManualSyncProgress) {
        Log.d(
            TAG,
            "debug sync progress phase=${progress.phase} current=${progress.current} total=${progress.total}",
        )
    }

    private fun ManualSyncResult.toDebugString(): String {
        return when (this) {
            is ManualSyncResult.Configured -> {
                "Configured folderLabel=$folderLabel hasExistingLibrary=$hasExistingLibrary"
            }
            is ManualSyncResult.Synced -> {
                "Synced uploaded=$uploadedBooks downloaded=$downloadedBooks " +
                    "conflicted=$conflictedBooks folderLabel=$folderLabel"
            }
            ManualSyncResult.NotConfigured -> "NotConfigured"
            ManualSyncResult.FolderUnavailable -> "FolderUnavailable"
            ManualSyncResult.FolderLooksLikeLibrarySubfolder -> "FolderLooksLikeLibrarySubfolder"
            ManualSyncResult.GoogleDriveAuthorizationRequired -> "GoogleDriveAuthorizationRequired"
            ManualSyncResult.GoogleDriveConfigurationRequired -> "GoogleDriveConfigurationRequired"
            ManualSyncResult.GoogleDriveQuotaExceeded -> "GoogleDriveQuotaExceeded"
            ManualSyncResult.GoogleDriveUnavailable -> "GoogleDriveUnavailable"
            is ManualSyncResult.GoogleDriveRemoteLibraryRemoved -> {
                "GoogleDriveRemoteLibraryRemoved remainingFiles=$remainingFiles " +
                    "remainingBytes=$remainingBytes"
            }
            ManualSyncResult.Failed -> "Failed"
        }
    }

    private companion object {
        const val TAG = "FolioreDebugSync"
    }
}
