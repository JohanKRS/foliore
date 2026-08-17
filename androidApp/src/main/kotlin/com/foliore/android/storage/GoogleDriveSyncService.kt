package com.foliore.android.storage

import android.content.Context
import android.util.Log
import com.foliore.shared.library.LibraryRepository
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GoogleDriveSyncService(
    context: Context,
    private val libraryRepository: LibraryRepository,
    private val deviceIdProvider: DeviceIdProvider,
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

    fun syncNow(
        onProgress: (ManualSyncProgress) -> Unit = {},
        allowRemoteLibraryRecreation: Boolean = false,
    ): ManualSyncResult {
        return when (val authorization = googleDriveAuthService.authorize()) {
            is GoogleDriveAuthorization.Authorized -> runCatching {
                val drive = GoogleDriveApiClient(authorization.accessToken)
                val stats = inspectBackupStats(drive)
                val localBooks = libraryRepository.getBooks()
                val hasLocalBooks = localBooks.isNotEmpty()
                val hasPriorLibraryEvidence =
                    syncSettingsRepository.hasKnownNonEmptyGoogleDriveLibrary() ||
                        localBooks.any { book -> book.book.lastSyncedAt != null }
                val shouldPause = shouldPauseGoogleDriveSync(
                    stats = stats,
                    hasLocalBooks = hasLocalBooks,
                    hasPriorLibraryEvidence = hasPriorLibraryEvidence,
                )

                rememberInspectedRemoteState(stats, hasLocalBooks, hasPriorLibraryEvidence)
                if (shouldPause && !allowRemoteLibraryRecreation) {
                    return@runCatching ManualSyncResult.GoogleDriveRemoteLibraryRemoved(
                        remainingFiles = stats.remoteFiles,
                        remainingBytes = stats.totalBytes,
                    )
                }

                if (allowRemoteLibraryRecreation && stats.syncedBooks == 0) {
                    deleteAllAppDataFiles(
                        drive = drive,
                        onProgress = { current, total ->
                            onProgress(
                                ManualSyncProgress(
                                    phase = ManualSyncPhase.DELETING_REMOTE,
                                    current = current,
                                    total = total,
                                ),
                            )
                        },
                    )
                } else if (!hasLocalBooks && stats.syncedBooks == 0 && !stats.hasBackup) {
                    return@runCatching ManualSyncResult.Synced(
                        uploadedBooks = 0,
                        downloadedBooks = 0,
                        conflictedBooks = 0,
                        folderLabel = syncSettingsRepository.googleDriveAccountLabel()
                            ?: SyncSettingsRepository.GOOGLE_DRIVE_DEFAULT_LABEL,
                    )
                }

                val store = GoogleDriveAppDataRemoteLibraryStore(
                    drive = drive,
                    label = syncSettingsRepository.googleDriveAccountLabel()
                        ?: SyncSettingsRepository.GOOGLE_DRIVE_DEFAULT_LABEL,
                )
                val result = syncEngine.sync(store, onProgress)
                if (libraryRepository.getBooks().isNotEmpty()) {
                    syncSettingsRepository.rememberNonEmptyGoogleDriveLibrary()
                } else {
                    syncSettingsRepository.clearGoogleDriveRemoteLibraryRecovery()
                }
                result
            }.getOrElse { error ->
                Log.e(
                    TAG,
                    "Google Drive sync failed type=${error::class.java.name} message=${error.message}",
                    error,
                )
                error.toGoogleDriveSyncResult()
            }
            is GoogleDriveAuthorization.NeedsResolution -> ManualSyncResult.GoogleDriveAuthorizationRequired
            is GoogleDriveAuthorization.Failed -> authorization.reason.toManualSyncResult()
        }
    }

    fun deleteBackup(
        accessToken: String,
        onProgress: (ManualSyncProgress) -> Unit = {},
    ): ManualSyncResult {
        return runCatching {
            onProgress(ManualSyncProgress(ManualSyncPhase.DELETING_REMOTE))
            val drive = GoogleDriveApiClient(accessToken)
            val deletionMarker = drive.uploadText(
                parentId = GoogleDriveApiClient.APP_DATA_ROOT_ID,
                name = DELETION_MARKER_FILE_NAME,
                mimeType = "application/json",
                text = json.encodeToString(
                    DeletionMarker(
                        deletedAt = Clock.System.now().toString(),
                        deletedByDeviceId = deviceIdProvider.getDeviceId(),
                    ),
                ),
            )
            deleteAllAppDataFiles(
                drive = drive,
                preservedFileIds = setOf(deletionMarker.id),
                onProgress = { current, total ->
                    onProgress(
                        ManualSyncProgress(
                            phase = ManualSyncPhase.DELETING_REMOTE,
                            current = current,
                            total = total,
                        ),
                    )
                },
            )
            if (libraryRepository.getBooks().isNotEmpty()) {
                syncSettingsRepository.markGoogleDriveRemoteLibraryRemoved(
                    remainingFiles = 0,
                    remainingBytes = 0L,
                )
            } else {
                syncSettingsRepository.clearGoogleDriveRemoteLibraryRecovery()
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
            val stats = inspectBackupStats(drive)
            val localBooks = libraryRepository.getBooks()
            rememberInspectedRemoteState(
                stats = stats,
                hasLocalBooks = localBooks.isNotEmpty(),
                hasPriorLibraryEvidence =
                    syncSettingsRepository.hasKnownNonEmptyGoogleDriveLibrary() ||
                        localBooks.any { book -> book.book.lastSyncedAt != null },
            )
            GoogleDriveBackupStatsResult.Loaded(stats)
        }.getOrElse { error ->
            Log.e(TAG, "Failed to inspect Google Drive appDataFolder backup", error)
            GoogleDriveBackupStatsResult.SyncFailed(error.toGoogleDriveSyncResult())
        }
    }

    private fun inspectBackupStats(drive: GoogleDriveApiClient): GoogleDriveBackupStats {
        val allAppDataItems = drive.listAppDataFiles()
        val allRootChildren = drive.listChildren(GoogleDriveApiClient.APP_DATA_ROOT_ID)
        val deletionMarkers = allRootChildren.filter { child ->
            child.name == DELETION_MARKER_FILE_NAME &&
                child.mimeType != GoogleDriveApiClient.FOLDER_MIME_TYPE
        }
        val deletionMarkerIds = deletionMarkers.mapTo(mutableSetOf()) { it.id }
        val appDataItems = allAppDataItems.filterNot { item -> item.id in deletionMarkerIds }
        val rootChildren = allRootChildren.filterNot { item -> item.id in deletionMarkerIds }
        val knownRootChildren = rootChildren.filter(::isKnownFolioreRootChild)
        val unknownRootChildren = rootChildren.filterNot(::isKnownFolioreRootChild)
        val manifestInfo = rootChildren
            .filter { child ->
                child.name == MANIFEST_FILE_NAME &&
                    child.mimeType != GoogleDriveApiClient.FOLDER_MIME_TYPE
            }
            .sortedWith(
                compareByDescending<DriveFileMetadata> { it.modifiedTime.orEmpty() }
                    .thenBy { it.id },
            )
            .firstNotNullOfOrNull { file ->
                runCatching { parseManifestInfo(drive.readText(file.id)) }.getOrNull()
            }
        val remoteFiles = appDataItems.filter { item ->
            item.mimeType != GoogleDriveApiClient.FOLDER_MIME_TYPE
        }
        val deletionMarkerPresent = deletionMarkers.isNotEmpty()

        return GoogleDriveBackupStats(
            hasBackup = !deletionMarkerPresent && manifestInfo != null,
            syncedBooks = if (deletionMarkerPresent) 0 else manifestInfo?.bookCount ?: 0,
            remoteFiles = remoteFiles.size,
            totalBytes = remoteFiles.sumOf { file -> file.sizeText?.toLongOrNull() ?: 0L },
            rootItems = rootChildren.size,
            recognizedRootItems = knownRootChildren.size,
            unknownRootItems = unknownRootChildren.size,
            updatedAt = if (deletionMarkerPresent) {
                deletionMarkers.mapNotNull { it.modifiedTime }.maxOrNull()
            } else {
                manifestInfo?.updatedAt ?: rootChildren.mapNotNull { it.modifiedTime }.maxOrNull()
            },
            remoteItems = appDataItems.size,
            deletionMarkerPresent = deletionMarkerPresent,
        )
    }

    private fun rememberInspectedRemoteState(
        stats: GoogleDriveBackupStats,
        hasLocalBooks: Boolean,
        hasPriorLibraryEvidence: Boolean,
    ) {
        when {
            stats.syncedBooks > 0 && stats.hasBackup -> {
                syncSettingsRepository.rememberNonEmptyGoogleDriveLibrary()
            }
            shouldPauseGoogleDriveSync(stats, hasLocalBooks, hasPriorLibraryEvidence) -> {
                syncSettingsRepository.markGoogleDriveRemoteLibraryRemoved(
                    remainingFiles = stats.remoteFiles,
                    remainingBytes = stats.totalBytes,
                )
            }
            !hasLocalBooks && stats.syncedBooks == 0 -> {
                if (stats.hasBackup) {
                    syncSettingsRepository.forgetNonEmptyGoogleDriveLibrary()
                }
                syncSettingsRepository.clearGoogleDriveRemoteLibraryRecovery()
            }
        }
    }

    private fun deleteAllAppDataFiles(
        drive: GoogleDriveApiClient,
        preservedFileIds: Set<String> = emptySet(),
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        var totalItems = -1
        var reportedDeletedItems = 0
        repeat(MAX_DELETE_PASSES) {
            val filesToDelete = drive.listAppDataFiles()
                .filterNot { file -> file.id in preservedFileIds }
            if (totalItems < 0) {
                totalItems = filesToDelete.size
                onProgress(0, totalItems)
            }
            if (filesToDelete.isEmpty()) {
                onProgress(totalItems, totalItems)
                return
            }

            val targets = planGoogleDriveDeletionTargets(filesToDelete)
            val deletedBeforePass = (totalItems - filesToDelete.size).coerceAtLeast(0)
            var deletedInPass = 0

            val executor = Executors.newFixedThreadPool(
                MAX_CONCURRENT_DELETES.coerceAtMost(targets.size),
            )
            val completionService = ExecutorCompletionService<Int>(executor)
            try {
                targets.forEach { target ->
                    completionService.submit(
                        Callable {
                            deleteFileWithRetry(drive, target.file.id)
                            target.representedItems
                        },
                    )
                }
                repeat(targets.size) {
                    deletedInPass += completionService.take().get()
                    reportedDeletedItems = maxOf(
                        reportedDeletedItems,
                        (deletedBeforePass + deletedInPass).coerceAtMost(totalItems),
                    )
                    onProgress(reportedDeletedItems, totalItems)
                }
            } finally {
                executor.shutdownNow()
            }
        }

        val remaining = drive.listAppDataFiles().filterNot { it.id in preservedFileIds }
        if (remaining.isNotEmpty()) {
            throw IOException("Google Drive cleanup left ${remaining.size} app data items")
        }
        onProgress(totalItems.coerceAtLeast(0), totalItems.coerceAtLeast(0))
    }

    private fun deleteFileWithRetry(
        drive: GoogleDriveApiClient,
        fileId: String,
    ) {
        var lastError: Throwable? = null
        repeat(DELETE_MAX_ATTEMPTS) { attempt ->
            try {
                drive.deleteFile(fileId)
                return
            } catch (error: Throwable) {
                lastError = error
                val retryable = error is IOException ||
                    error is GoogleDriveHttpException && error.isRetryable
                if (!retryable || attempt == DELETE_MAX_ATTEMPTS - 1) throw error
                Thread.sleep(DELETE_RETRY_DELAYS_MS[attempt])
            }
        }
        throw checkNotNull(lastError)
    }

    private fun isKnownFolioreRootChild(child: DriveFileMetadata): Boolean {
        return if (child.mimeType == GoogleDriveApiClient.FOLDER_MIME_TYPE) {
            child.name in FOLIORE_ROOT_FOLDERS
        } else {
            child.name in FOLIORE_ROOT_FILES
        }
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

    @Serializable
    private data class DeletionMarker(
        val deletedAt: String,
        val deletedByDeviceId: String,
    )

    private fun Throwable.toGoogleDriveSyncResult(): ManualSyncResult {
        val causes = generateSequence(this) { error -> error.cause }.toList()
        val driveError = causes.filterIsInstance<GoogleDriveHttpException>().firstOrNull()
        return when {
            driveError?.isAuthorizationError == true -> ManualSyncResult.GoogleDriveAuthorizationRequired
            driveError?.isQuotaExceeded == true -> ManualSyncResult.GoogleDriveQuotaExceeded
            driveError?.isRetryable == true || causes.any { error -> error is IOException } -> {
                ManualSyncResult.GoogleDriveUnavailable
            }
            else -> ManualSyncResult.Failed
        }
    }

    private fun GoogleDriveAuthorizationFailure.toManualSyncResult(): ManualSyncResult {
        return when (this) {
            GoogleDriveAuthorizationFailure.CONFIGURATION_REQUIRED -> {
                ManualSyncResult.GoogleDriveConfigurationRequired
            }
            GoogleDriveAuthorizationFailure.CANCELLED -> {
                ManualSyncResult.GoogleDriveAuthorizationRequired
            }
            GoogleDriveAuthorizationFailure.UNAVAILABLE -> {
                ManualSyncResult.GoogleDriveUnavailable
            }
        }
    }

    private companion object {
        const val TAG = "GoogleDriveSyncService"
        const val MANIFEST_FILE_NAME = "library.manifest.json"
        const val DELETION_MARKER_FILE_NAME = "library.deleted.json"
        const val AI_COVER_PROMPTS_FILE_NAME = "ai-cover-prompts.json"
        const val MAX_CONCURRENT_DELETES = 4
        const val MAX_DELETE_PASSES = 4
        const val DELETE_MAX_ATTEMPTS = 3
        val DELETE_RETRY_DELAYS_MS = longArrayOf(750L, 2_000L)
        val FOLIORE_ROOT_FOLDERS = setOf("books", "devices", "snapshots")
        val FOLIORE_ROOT_FILES = setOf(MANIFEST_FILE_NAME, AI_COVER_PROMPTS_FILE_NAME)
    }
}

internal fun shouldPauseGoogleDriveSync(
    stats: GoogleDriveBackupStats,
    hasLocalBooks: Boolean,
    hasPriorLibraryEvidence: Boolean,
): Boolean {
    return hasLocalBooks &&
        stats.syncedBooks == 0 &&
        (
            stats.deletionMarkerPresent ||
                stats.hasOrphanedFiles ||
                hasPriorLibraryEvidence
            )
}

internal data class GoogleDriveDeletionTarget(
    val file: DriveFileMetadata,
    val representedItems: Int,
)

internal fun planGoogleDriveDeletionTargets(
    files: List<DriveFileMetadata>,
): List<GoogleDriveDeletionTarget> {
    if (files.isEmpty()) return emptyList()

    val filesById = files.associateBy { file -> file.id }
    val representedItemsByRootId = linkedMapOf<String, Int>()
    files.forEach { file ->
        var current = file
        val visitedIds = mutableSetOf(file.id)
        while (true) {
            val parent = current.parents
                .firstNotNullOfOrNull { parentId -> filesById[parentId] }
                ?: break
            if (!visitedIds.add(parent.id)) {
                current = file
                break
            }
            current = parent
        }
        representedItemsByRootId[current.id] =
            representedItemsByRootId.getOrDefault(current.id, 0) + 1
    }

    return representedItemsByRootId
        .mapNotNull { (rootId, representedItems) ->
            filesById[rootId]?.let { root ->
                GoogleDriveDeletionTarget(
                    file = root,
                    representedItems = representedItems,
                )
            }
        }
        .sortedWith(
            compareBy<GoogleDriveDeletionTarget> { target ->
                if (target.file.mimeType == GoogleDriveApiClient.FOLDER_MIME_TYPE) 0 else 1
            }.thenBy { target -> target.file.id },
        )
}
