package com.foliore.android.storage

import android.content.Context
import android.util.Log
import com.foliore.shared.library.LibraryRepository

class GoogleDriveSyncService(
    context: Context,
    libraryRepository: LibraryRepository,
    deviceIdProvider: DeviceIdProvider,
    aiCoverPromptRepository: AiCoverPromptRepository,
    private val googleDriveAuthService: GoogleDriveAuthService,
    private val syncSettingsRepository: SyncSettingsRepository,
) {
    private val appContext = context.applicationContext
    private val syncEngine = PortableLibrarySyncEngine(
        context = appContext,
        libraryRepository = libraryRepository,
        deviceIdProvider = deviceIdProvider,
        aiCoverPromptRepository = aiCoverPromptRepository,
    )

    fun syncNow(onProgress: (ManualSyncProgress) -> Unit = {}): ManualSyncResult {
        return when (val authorization = googleDriveAuthService.authorize()) {
            is GoogleDriveAuthorization.Authorized -> runCatching {
                val store = GoogleDriveAppDataRemoteLibraryStore(
                    drive = GoogleDriveApiClient(authorization.accessToken),
                    label = syncSettingsRepository.googleDriveAccountLabel()
                        ?: SyncSettingsRepository.GOOGLE_DRIVE_DEFAULT_LABEL,
                )
                syncEngine.sync(store, onProgress)
            }.getOrElse { error ->
                Log.e(
                    TAG,
                    "Google Drive sync failed type=${error::class.java.name} message=${error.message}",
                    error,
                )
                error.toGoogleDriveSyncResult()
            }
            is GoogleDriveAuthorization.NeedsResolution -> ManualSyncResult.GoogleDriveAuthorizationRequired
            GoogleDriveAuthorization.Failed -> ManualSyncResult.GoogleDriveUnavailable
        }
    }

    fun deleteBackup(accessToken: String): ManualSyncResult {
        return runCatching {
            val drive = GoogleDriveApiClient(accessToken)
            drive.listChildren(GoogleDriveApiClient.APP_DATA_ROOT_ID).forEach { child ->
                if (child.mimeType == GoogleDriveApiClient.FOLDER_MIME_TYPE) {
                    GoogleDriveAppDataRemoteLibraryStore(drive).deleteTree(child.name)
                } else {
                    drive.deleteFile(child.id)
                }
            }
            ManualSyncResult.Synced(
                uploadedBooks = 0,
                downloadedBooks = 0,
                conflictedBooks = 0,
                folderLabel = SyncSettingsRepository.GOOGLE_DRIVE_DEFAULT_LABEL,
            )
        }.getOrElse { error ->
            Log.e(TAG, "Failed to delete Google Drive appDataFolder backup", error)
            error.toGoogleDriveSyncResult()
        }
    }

    private fun Throwable.toGoogleDriveSyncResult(): ManualSyncResult {
        val driveError = this as? GoogleDriveHttpException
        return when {
            driveError?.isAuthorizationError == true -> ManualSyncResult.GoogleDriveAuthorizationRequired
            driveError?.isQuotaExceeded == true -> ManualSyncResult.GoogleDriveQuotaExceeded
            driveError?.isRetryable == true -> ManualSyncResult.GoogleDriveUnavailable
            else -> ManualSyncResult.Failed
        }
    }

    private companion object {
        const val TAG = "GoogleDriveSyncService"
    }
}
