package com.foliore.android.storage

import android.content.Context
import android.util.Log
import com.foliore.shared.library.LibraryRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GoogleDriveSyncService(
    context: Context,
    libraryRepository: LibraryRepository,
    deviceIdProvider: DeviceIdProvider,
    aiCoverPromptRepository: AiCoverPromptRepository,
    private val googleDriveAuthService: GoogleDriveAuthService,
    private val syncSettingsRepository: SyncSettingsRepository,
) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
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
            drive.listChildren(GoogleDriveApiClient.APP_DATA_ROOT_ID)
                .forEach { child ->
                    drive.deleteTree(child)
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

    fun backupStats(accessToken: String): GoogleDriveBackupStatsResult {
        return runCatching {
            val drive = GoogleDriveApiClient(accessToken)
            val rootChildren = drive.listChildren(GoogleDriveApiClient.APP_DATA_ROOT_ID)
            val knownRootChildren = rootChildren.filter(::isKnownFolioreRootChild)
            val unknownRootChildren = rootChildren.filterNot(::isKnownFolioreRootChild)
            val manifest = rootChildren.firstOrNull { child ->
                child.name == MANIFEST_FILE_NAME &&
                    child.mimeType != GoogleDriveApiClient.FOLDER_MIME_TYPE
            }
            val manifestInfo = manifest?.let { file ->
                runCatching { parseManifestInfo(drive.readText(file.id)) }.getOrNull()
            }
            val appDataFiles = drive.listAppDataFiles()
                .filter { file -> file.mimeType != GoogleDriveApiClient.FOLDER_MIME_TYPE }

            GoogleDriveBackupStatsResult.Loaded(
                GoogleDriveBackupStats(
                    hasBackup = rootChildren.isNotEmpty(),
                    syncedBooks = manifestInfo?.bookCount ?: 0,
                    remoteFiles = appDataFiles.size,
                    totalBytes = appDataFiles.sumOf { file -> file.sizeText?.toLongOrNull() ?: 0L },
                    rootItems = rootChildren.size,
                    recognizedRootItems = knownRootChildren.size,
                    unknownRootItems = unknownRootChildren.size,
                    updatedAt = manifestInfo?.updatedAt ?: rootChildren
                        .mapNotNull { it.modifiedTime }
                        .maxOrNull(),
                ),
            )
        }.getOrElse { error ->
            Log.e(TAG, "Failed to inspect Google Drive appDataFolder backup", error)
            GoogleDriveBackupStatsResult.SyncFailed(error.toGoogleDriveSyncResult())
        }
    }

    private fun isKnownFolioreRootChild(child: DriveFileMetadata): Boolean {
        return if (child.mimeType == GoogleDriveApiClient.FOLDER_MIME_TYPE) {
            child.name in FOLIORE_ROOT_FOLDERS
        } else {
            child.name in FOLIORE_ROOT_FILES
        }
    }

    private fun GoogleDriveApiClient.deleteTree(file: DriveFileMetadata) {
        if (file.mimeType == GoogleDriveApiClient.FOLDER_MIME_TYPE) {
            listChildren(file.id).forEach { child ->
                deleteTree(child)
            }
        }
        deleteFile(file.id)
    }

    private fun parseManifestInfo(manifestText: String): ManifestInfo {
        val manifest = json.parseToJsonElement(manifestText).jsonObject
        val updatedAt = manifest["updatedAt"]?.jsonPrimitive?.contentOrNull
        val books = manifest["books"]?.jsonArray.orEmpty()
        val activeBookCount = books.count { element ->
            val book = element.jsonObject
            book["deleted"]?.jsonPrimitive?.booleanOrNull != true
        }
        return ManifestInfo(
            bookCount = activeBookCount,
            updatedAt = updatedAt,
        )
    }

    private data class ManifestInfo(
        val bookCount: Int,
        val updatedAt: String?,
    )

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
        const val MANIFEST_FILE_NAME = "library.manifest.json"
        const val AI_COVER_PROMPTS_FILE_NAME = "ai-cover-prompts.json"
        val FOLIORE_ROOT_FOLDERS = setOf("books", "devices", "snapshots")
        val FOLIORE_ROOT_FILES = setOf(MANIFEST_FILE_NAME, AI_COVER_PROMPTS_FILE_NAME)
    }
}
