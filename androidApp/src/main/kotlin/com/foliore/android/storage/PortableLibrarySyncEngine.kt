package com.foliore.android.storage

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.foliore.android.ai.AiCoverPromptRecord
import com.foliore.shared.data.MetadataSnapshot
import com.foliore.shared.data.TechnicalSnapshot
import com.foliore.shared.domain.Book
import com.foliore.shared.domain.BookMetadata
import com.foliore.shared.domain.BookStatus
import com.foliore.shared.domain.EpubTechnicalInfo
import com.foliore.shared.domain.ImportedBookRecord
import com.foliore.shared.domain.LibraryBook
import com.foliore.shared.domain.MetadataSource
import com.foliore.shared.domain.SyncStatus
import com.foliore.shared.library.LibraryRepository
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class PortableLibrarySyncEngine(
    context: Context,
    private val libraryRepository: LibraryRepository,
    private val deviceIdProvider: DeviceIdProvider,
    private val aiCoverPromptRepository: AiCoverPromptRepository,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(SyncPreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val pendingRemoteDeletionStore = PendingRemoteDeletionStore(appContext)
    private val repositoryLock = Any()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun sync(
        store: RemoteLibraryStore,
        onProgress: (ManualSyncProgress) -> Unit = {},
    ): ManualSyncResult.Synced {
        val startedAtElapsedMillis = SystemClock.elapsedRealtime()
        onProgress(ManualSyncProgress(ManualSyncPhase.PREPARING))
        val syncedAt = Clock.System.now().toString()
        val deviceId = deviceIdProvider.getDeviceId()
        val remoteManifest = store.readManifest()
        val remoteBooks = remoteManifest?.books
            ?.filterNot { it.deleted }
            ?.associateBy { it.bookId }
            .orEmpty()
        val counters = SyncCounters()
        val pendingStatusUpdates = PendingSyncStatusUpdates()
        val localBooks = libraryRepository.getBooks()
        val localBooksById = localBooks.associateBy { it.book.id }
        val pendingDeletedBookIds = pendingRemoteDeletionStore.pendingBookIds() - localBooksById.keys
        val remoteBookList = remoteBooks.values.toList()
        val missingRemoteBookIds = linkedSetOf<String>()
        val removedRemoteBookIds = linkedSetOf<String>()
        val remoteDownloadActions = mutableListOf<RemoteDownloadAction>()
        Log.d(
            TAG,
            "sync start store=${store.label} local=${localBooks.size} remote=${remoteBooks.size} " +
                "schema=${remoteManifest?.schemaVersion ?: 0}",
        )

        onProgress(
            ManualSyncProgress(
                phase = ManualSyncPhase.CHECKING_REMOTE,
                current = 0,
                total = remoteBookList.size,
            ),
        )
        remoteBookList.forEachIndexed { index, remoteBook ->
            val localBook = localBooksById[remoteBook.bookId]
            val shouldDeleteRemoteBook = remoteBook.bookId in pendingDeletedBookIds ||
                (localBook == null && remoteBook.updatedByDeviceId == deviceId)
            val hasCurrentRemoteFiles = remoteManifest?.schemaVersion?.let { it >= CURRENT_MANIFEST_SCHEMA_VERSION } == true &&
                remoteBook.files.isNotEmpty() &&
                remoteBook.stateBundleFile != null

            if (shouldDeleteRemoteBook) {
                remoteBook.deleteRemoteFiles(store)
                removedRemoteBookIds += remoteBook.bookId
            } else if (hasCurrentRemoteFiles) {
                planRemoteBookAction(
                    remoteBook = remoteBook,
                    local = localBook,
                    currentDeviceId = deviceId,
                    counters = counters,
                    pendingStatusUpdates = pendingStatusUpdates,
                )?.let(remoteDownloadActions::add)
            } else if (localBook == null) {
                removedRemoteBookIds += remoteBook.bookId
            } else {
                missingRemoteBookIds += remoteBook.bookId
            }
            onProgress(
                ManualSyncProgress(
                    phase = ManualSyncPhase.CHECKING_REMOTE,
                    current = index + 1,
                    total = remoteBookList.size,
                ),
            )
        }

        var downloadProgress = 0
        if (remoteDownloadActions.isNotEmpty()) {
            onProgress(
                ManualSyncProgress(
                    phase = ManualSyncPhase.DOWNLOADING,
                    current = downloadProgress,
                    total = remoteDownloadActions.size,
                ),
            )
        }
        downloadRemoteBooks(
            store = store,
            actions = remoteDownloadActions,
            syncedAt = syncedAt,
            onDownloaded = { action ->
                downloadProgress += 1
                counters.downloadedBooks += 1
                action.duplicateBookToRemove?.let { duplicateBook ->
                    removeLocalDuplicateBook(duplicateBook)
                    pendingStatusUpdates.syncedBookIds += action.remoteBook.bookId
                }
                onProgress(
                    ManualSyncProgress(
                        phase = ManualSyncPhase.DOWNLOADING,
                        current = downloadProgress,
                        total = remoteDownloadActions.size,
                    ),
                )
            },
        )

        val shouldRewriteRemoteMetadataBase = remoteManifest == null ||
            (remoteManifest.schemaVersion < CURRENT_MANIFEST_SCHEMA_VERSION) ||
            missingRemoteBookIds.isNotEmpty() ||
            removedRemoteBookIds.isNotEmpty() ||
            !store.hasDirectory("devices") ||
            !store.hasDirectory("snapshots")

        val booksAfterRemote = libraryRepository.getBooks()
        val remoteLayoutsByBookId = booksAfterRemote.remoteLayoutsByBookId()
        val localManifestIndex = buildLocalManifestIndex(
            localBooks = booksAfterRemote,
            remoteLayoutsByBookId = remoteLayoutsByBookId,
            remoteBooks = remoteBooks,
            previousCache = readLocalManifestCache(),
        )
        val expectedRemoteBooksById = localManifestIndex.booksById
        val shouldRewriteRemoteMetadata = shouldRewriteRemoteMetadataBase ||
            booksAfterRemote.any { book ->
                book.book.syncStatus != SyncStatus.CONFLICT &&
                    book.book.id !in pendingStatusUpdates.conflictedBookIds &&
                    remoteBooks[book.book.id]?.files?.isEmpty() == true
            }
        val shouldRefreshRemoteBookFolders =
            preferences.getInt(KEY_SYNC_UPLOAD_STRATEGY_VERSION, 0) < CURRENT_SYNC_UPLOAD_STRATEGY_VERSION
        val uploadCandidateIds = booksAfterRemote
            .filterNot { book ->
                book.book.syncStatus == SyncStatus.CONFLICT ||
                    book.book.id in pendingStatusUpdates.conflictedBookIds
            }
            .filter { book ->
                book.shouldUploadTo(
                    remoteBook = remoteBooks[book.book.id],
                    forceUpload = shouldRefreshRemoteBookFolders || book.book.id in missingRemoteBookIds,
                    expectedRemoteBook = expectedRemoteBooksById[book.book.id],
                )
            }
            .map { it.book.id }
            .toSet()
        Log.d(
            TAG,
            "sync plan downloads=${remoteDownloadActions.size} candidates=${uploadCandidateIds.size} " +
                "rewriteManifest=$shouldRewriteRemoteMetadata " +
                "refreshFolders=$shouldRefreshRemoteBookFolders missing=${missingRemoteBookIds.size} " +
                "removed=${removedRemoteBookIds.size} " +
                "remoteManifestHits=${localManifestIndex.remoteManifestHits} " +
                "localCacheHits=${localManifestIndex.cacheHits} " +
                "localCacheMisses=${localManifestIndex.cacheMisses}",
        )
        var uploadProgress = 0

        if (uploadCandidateIds.isNotEmpty()) {
            onProgress(
                ManualSyncProgress(
                    phase = ManualSyncPhase.UPLOADING,
                    current = uploadProgress,
                    total = uploadCandidateIds.size,
                ),
            )
        }

        if (booksAfterRemote.isNotEmpty() || remoteBooks.isNotEmpty()) {
            store.ensureDirectory("books")
        }
        val uploadCandidates = booksAfterRemote
            .filterNot { book ->
                book.book.syncStatus == SyncStatus.CONFLICT ||
                    book.book.id in pendingStatusUpdates.conflictedBookIds
            }
            .filter { book -> book.book.id in uploadCandidateIds }

        uploadBooks(
            store = store,
            books = uploadCandidates,
            remoteLayoutsByBookId = remoteLayoutsByBookId,
            remoteBooks = remoteBooks,
            onUploaded = { bookId ->
                uploadProgress += 1
                counters.uploadedBooks += 1
                pendingStatusUpdates.syncedBookIds += bookId
                onProgress(
                    ManualSyncProgress(
                        phase = ManualSyncPhase.UPLOADING,
                        current = uploadProgress,
                        total = uploadCandidateIds.size,
                    ),
                )
            },
        )

        booksAfterRemote
            .filterNot { book ->
                book.book.syncStatus == SyncStatus.CONFLICT ||
                    book.book.id in pendingStatusUpdates.conflictedBookIds ||
                    book.book.id in uploadCandidateIds
            }
            .filter { book -> book.book.syncStatus != SyncStatus.SYNCED }
            .forEach { book -> pendingStatusUpdates.syncedBookIds += book.book.id }
        val shouldCleanRemoteBookFolders = shouldRewriteRemoteMetadata ||
            uploadCandidateIds.isNotEmpty() ||
            shouldRefreshRemoteBookFolders
        val cleanedRemoteBooks = if (shouldCleanRemoteBookFolders && store.hasDirectory("books")) {
            store.deleteUnexpectedChildren(
                directoryPath = "books",
                expectedDirectoryNames = remoteLayoutsByBookId.values.mapTo(mutableSetOf()) { it.folderName },
            )
        } else {
            false
        }
        val aiCoverPromptSync = syncAiCoverPrompts(
            store = store,
            syncedAt = syncedAt,
            deviceId = deviceId,
        )

        if (counters.uploadedBooks > 0 ||
            shouldRewriteRemoteMetadata ||
            cleanedRemoteBooks ||
            aiCoverPromptSync.shouldWriteRemote
        ) {
            onProgress(ManualSyncProgress(ManualSyncPhase.WRITING_MANIFEST))
            store.ensureDirectory("devices")
            store.ensureDirectory("snapshots")
            store.writeText(
                path = "devices/$deviceId.json",
                text = json.encodeToString(
                    DeviceSnapshot(
                        deviceId = deviceId,
                        deviceName = android.os.Build.MODEL ?: "Android",
                        platform = "android",
                        updatedAt = syncedAt,
                    ),
                ),
            )
            store.writeText(
                path = "library.manifest.json",
                text = json.encodeToString(
                    LibraryManifestSnapshot(
                        schemaVersion = CURRENT_MANIFEST_SCHEMA_VERSION,
                        libraryId = libraryId(remoteManifest),
                        updatedAt = syncedAt,
                        updatedByDeviceId = deviceId,
                        aiCoverPromptsFile = aiCoverPromptSync
                            .mergedRecords
                            .takeIf { it.isNotEmpty() }
                            ?.let { AI_COVER_PROMPTS_FILE },
                        books = mergedManifestBooks(
                            remoteBooks = remoteBooks - removedRemoteBookIds,
                            localBooks = booksAfterRemote,
                            remoteLayoutsByBookId = remoteLayoutsByBookId,
                            conflictedBookIds = pendingStatusUpdates.conflictedBookIds,
                            localManifestBooksById = expectedRemoteBooksById,
                        ),
                    ),
                ),
            )
            if (aiCoverPromptSync.shouldWriteRemote) {
                store.writeText(
                    path = AI_COVER_PROMPTS_FILE,
                    text = json.encodeToString(aiCoverPromptSync.snapshot),
                )
            }
            store.writeText(
                path = "snapshots/last-sync.txt",
                text = syncedAt,
                mimeType = "text/plain",
            )
        }

        if (pendingStatusUpdates.hasUpdates()) {
            onProgress(ManualSyncProgress(ManualSyncPhase.APPLYING_STATUS))
            pendingStatusUpdates.conflictedBookIds.forEach { bookId ->
                libraryRepository.markSyncConflict(bookId)
            }
            pendingStatusUpdates.syncedBookIds
                .filterNot { it in pendingStatusUpdates.conflictedBookIds }
                .forEach { bookId ->
                    libraryRepository.markSynced(bookId, syncedAt)
                }
        }

        if (shouldRefreshRemoteBookFolders) {
            preferences.edit()
                .putInt(KEY_SYNC_UPLOAD_STRATEGY_VERSION, CURRENT_SYNC_UPLOAD_STRATEGY_VERSION)
                .apply()
        }
        pendingRemoteDeletionStore.clear(pendingDeletedBookIds)
        writeLocalManifestCache(localManifestIndex.snapshot)

        val durationMs = SystemClock.elapsedRealtime() - startedAtElapsedMillis
        Log.d(
            TAG,
            "sync end durationMs=$durationMs uploaded=${counters.uploadedBooks} " +
                "downloaded=${counters.downloadedBooks} conflicted=${counters.conflictedBooks}",
        )

        return ManualSyncResult.Synced(
            uploadedBooks = counters.uploadedBooks,
            downloadedBooks = counters.downloadedBooks,
            conflictedBooks = counters.conflictedBooks,
            folderLabel = store.label,
        )
    }

    private fun syncAiCoverPrompts(
        store: RemoteLibraryStore,
        syncedAt: String,
        deviceId: String,
    ): AiCoverPromptSync {
        val localRecords = aiCoverPromptRepository.records()
        val remoteSnapshot = store.readAiCoverPromptSnapshot()
        val remoteRecords = remoteSnapshot?.records.orEmpty().normalizedAiCoverPromptRecords()
        val mergedRecords = (remoteRecords + localRecords).normalizedAiCoverPromptRecords()

        if (mergedRecords != localRecords) {
            aiCoverPromptRepository.replaceAll(mergedRecords)
        }

        val shouldWriteRemote = mergedRecords.isNotEmpty() &&
            (remoteSnapshot == null || remoteRecords != mergedRecords)

        return AiCoverPromptSync(
            mergedRecords = mergedRecords,
            shouldWriteRemote = shouldWriteRemote,
            snapshot = AiCoverPromptLibrarySnapshot(
                schemaVersion = 1,
                updatedAt = syncedAt,
                updatedByDeviceId = deviceId,
                records = mergedRecords,
            ),
        )
    }

    private fun LibraryBook.shouldUploadTo(
        remoteBook: ManifestBookSnapshot?,
        forceUpload: Boolean,
        expectedRemoteBook: ManifestBookSnapshot?,
    ): Boolean {
        return forceUpload ||
            remoteBook == null ||
            expectedRemoteBook?.let { expected -> remoteBook.hasDifferentPathsThan(expected) } == true ||
            expectedRemoteBook?.let { expected -> remoteBook.hasDifferentFileSignaturesThan(expected) } == true ||
            book.revision > remoteBook.revision ||
            book.syncStatus == SyncStatus.LOCAL_ONLY ||
            book.syncStatus == SyncStatus.PENDING_UPLOAD
    }

    private fun planRemoteBookAction(
        remoteBook: ManifestBookSnapshot,
        local: LibraryBook?,
        currentDeviceId: String,
        counters: SyncCounters,
        pendingStatusUpdates: PendingSyncStatusUpdates,
    ): RemoteDownloadAction? {
        if (local == null) {
            val duplicateBookId = libraryRepository.findBookIdByHash(remoteBook.originalHashSha256)
            if (duplicateBookId != null) {
                val duplicateBook = libraryRepository.getBook(duplicateBookId)
                if (duplicateBook != null && duplicateBook.book.id != remoteBook.bookId) {
                    if (duplicateBook.canAdoptRemoteDuplicate(remoteBook)) {
                        return RemoteDownloadAction(
                            remoteBook = remoteBook,
                            currentBook = duplicateBook,
                            duplicateBookToRemove = duplicateBook,
                        )
                    } else {
                        pendingStatusUpdates.conflictedBookIds += duplicateBook.book.id
                        counters.conflictedBooks += 1
                    }
                } else {
                    pendingStatusUpdates.syncedBookIds += duplicateBookId
                }
                return null
            }

            return RemoteDownloadAction(
                remoteBook = remoteBook,
                currentBook = null,
            )
        }

        return when {
            local.book.syncStatus == SyncStatus.CONFLICT -> {
                counters.conflictedBooks += 1
                null
            }
            remoteBook.revision > local.book.revision -> {
                if (local.book.syncStatus == SyncStatus.PENDING_UPLOAD &&
                    remoteBook.updatedByDeviceId != currentDeviceId
                ) {
                    pendingStatusUpdates.conflictedBookIds += local.book.id
                    counters.conflictedBooks += 1
                    null
                } else {
                    RemoteDownloadAction(
                        remoteBook = remoteBook,
                        currentBook = local,
                    )
                }
            }
            remoteBook.revision == local.book.revision -> {
                if (remoteBook.updatedAt != local.book.updatedAt &&
                    remoteBook.updatedByDeviceId != local.book.updatedByDeviceId
                ) {
                    pendingStatusUpdates.conflictedBookIds += local.book.id
                    counters.conflictedBooks += 1
                    null
                } else if (local.book.syncStatus != SyncStatus.SYNCED) {
                    pendingStatusUpdates.syncedBookIds += local.book.id
                    null
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun LibraryBook.canAdoptRemoteDuplicate(remoteBook: ManifestBookSnapshot): Boolean {
        val hasLocalOnlyImport = book.syncStatus == SyncStatus.LOCAL_ONLY &&
            book.revision <= 1L &&
            book.status in setOf(BookStatus.IMPORTED, BookStatus.NEEDS_REVIEW, BookStatus.ERROR)
        val remoteIsAtLeastAsFresh = remoteBook.revision >= book.revision
        return book.syncStatus == SyncStatus.SYNCED ||
            book.syncStatus == SyncStatus.PENDING_DOWNLOAD ||
            (hasLocalOnlyImport && remoteIsAtLeastAsFresh)
    }

    private fun removeLocalDuplicateBook(duplicateBook: LibraryBook) {
        val bookDir = File(duplicateBook.book.originalFilePath).parentFile
        libraryRepository.deleteBook(duplicateBook.book.id)
        bookDir?.deleteRecursively()
    }

    private fun downloadRemoteBook(
        store: RemoteLibraryStore,
        remoteBook: ManifestBookSnapshot,
        syncedAt: String,
        currentBook: LibraryBook?,
    ) {
        val startedAtElapsedMillis = SystemClock.elapsedRealtime()
        Log.d(TAG, "downloadBook start id=${remoteBook.bookId}")
        val bookDir = File(appContext.filesDir, "books/${remoteBook.bookId}")

        if (bookDir.exists()) {
            bookDir.deleteRecursively()
        }
        bookDir.mkdirs()

        val stateBundlePath = remoteBook.stateBundleFile
            ?: error("Missing remote state bundle for ${remoteBook.bookId}")
        store.copyToLocal(stateBundlePath, File(bookDir, STATE_BUNDLE_FILE_NAME))
            ?.applyManifestModifiedTime(remoteBook, stateBundlePath)
            ?.also { bundleFile ->
                extractStateBundle(bundleFile, bookDir)
                bundleFile.delete()
            }
            ?: error("Missing remote state bundle for ${remoteBook.bookId}")

        val localMetadataFile = File(bookDir, "metadata.json")
        val metadataText = localMetadataFile
            .takeIf { file -> file.exists() }
            ?.readText(Charsets.UTF_8)
            ?: error("Missing bundled metadata for ${remoteBook.bookId}")

        val localTechnicalFile = File(bookDir, "technical.json")
        val technicalText = localTechnicalFile
            .takeIf { file -> file.exists() }
            ?.readText(Charsets.UTF_8)
            ?: error("Missing bundled technical snapshot for ${remoteBook.bookId}")

        val metadataSnapshot = json.decodeFromString<MetadataSnapshot>(metadataText)
        val technicalSnapshot = json.decodeFromString<TechnicalSnapshot>(technicalText)

        val localOriginalFile = File(bookDir, "original.epub")
        store.copyToLocal(remoteBook.originalFile, localOriginalFile)
            ?.applyManifestModifiedTime(remoteBook, remoteBook.originalFile)
            ?: error("Missing remote original EPUB for ${remoteBook.bookId}")
        val localPreparedFile = remoteBook.preparedFile?.let { path ->
            store.copyToLocal(path, File(bookDir, "prepared.epub"))
                ?.applyManifestModifiedTime(remoteBook, path)
        }
        val localCoverFile = File(bookDir, "cover_original.jpg")
            .takeIf { file -> file.exists() }
        val localCoverThumbFile = File(bookDir, "cover_thumb.jpg")
            .takeIf { file -> file.exists() }

        val bookStatus = metadataSnapshot.status.toBookStatus()
        val technicalInfo = technicalSnapshot.toDomain()
        val book = Book(
            id = remoteBook.bookId,
            originalFilePath = localOriginalFile.absolutePath,
            preparedFilePath = localPreparedFile?.absolutePath,
            coverImagePath = localCoverFile?.absolutePath,
            coverThumbPath = localCoverThumbFile?.absolutePath,
            originalHashSha256 = metadataSnapshot.originalHashSha256,
            fileSizeBytes = localOriginalFile.length(),
            importedAt = currentBook?.book?.importedAt ?: syncedAt,
            updatedAt = metadataSnapshot.updatedAt,
            lastPreparedAt = currentBook?.book?.lastPreparedAt
                ?: localPreparedFile?.let { metadataSnapshot.updatedAt },
            lastExportedAt = currentBook?.book?.lastExportedAt,
            lastSyncedAt = syncedAt,
            status = bookStatus,
            syncStatus = SyncStatus.SYNCED,
            revision = metadataSnapshot.revision,
            updatedByDeviceId = metadataSnapshot.updatedByDeviceId,
            technicalStatus = technicalInfo.errors.takeIf { it.isNotEmpty() }?.joinToString("; "),
        )
        val metadata = metadataSnapshot.toDomain()

        synchronized(repositoryLock) {
            libraryRepository.insertImportedBook(
                ImportedBookRecord(
                    book = book,
                    metadata = metadata,
                    technicalInfo = technicalInfo,
                ),
            )
        }
        Log.d(
            TAG,
            "downloadBook end id=${remoteBook.bookId} " +
                "durationMs=${SystemClock.elapsedRealtime() - startedAtElapsedMillis}",
        )
    }

    private fun extractStateBundle(
        bundleFile: File,
        destinationDir: File,
    ) {
        ZipInputStream(bundleFile.inputStream().buffered()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val fileName = entry.name.substringAfterLast('/')
                if (!entry.isDirectory && fileName in STATE_BUNDLE_ENTRY_NAMES) {
                    val destination = destinationDir.resolve(fileName)
                    destination.outputStream().buffered().use { output ->
                        input.copyTo(output)
                    }
                    entry.time
                        .takeIf { modifiedAt -> modifiedAt > 0L }
                        ?.let(destination::setLastModified)
                }
                input.closeEntry()
            }
        }
    }

    private fun uploadBook(
        store: RemoteLibraryStore,
        libraryBook: LibraryBook,
        layout: RemoteBookLayout,
        previousRemoteBook: ManifestBookSnapshot?,
    ) {
        val localDir = File(libraryBook.book.originalFilePath).parentFile
            ?: error("Missing local directory for ${libraryBook.book.id}")
        val epubSource = libraryBook.book.preparedFilePath
            ?.let(::File)
            ?.takeIf { it.exists() }
            ?: localDir.resolve("original.epub")

        store.deleteObsoleteBookFolders(
            bookId = libraryBook.book.id,
            activeFolderPath = layout.folderPath,
            previousRemoteBook = previousRemoteBook,
        )
        store.ensureDirectory(layout.folderPath)

        val localFiles = libraryBook.localSyncFiles(layout, localDir, epubSource)
        val localFilePaths = localFiles.mapTo(mutableSetOf()) { it.remotePath }
        val expectedManifest = libraryBook.toManifestBook(
            layout = layout,
            files = localFiles.map { file -> file.toManifestFileSnapshot() },
        )
        val canUsePreviousFileIndex = previousRemoteBook != null &&
            previousRemoteBook.files.isNotEmpty() &&
            !previousRemoteBook.hasDifferentPathsThan(expectedManifest)
        val previousFilesByPath = previousRemoteBook
            ?.files
            .orEmpty()
            .associateBy { file -> file.path }

        if (canUsePreviousFileIndex) {
            var deletedFiles = 0
            previousFilesByPath.keys
                .filterNot { path -> path in localFilePaths }
                .forEach { path ->
                    store.deleteTree(path)
                    deletedFiles += 1
                }
            if (deletedFiles > 0) {
                Log.d(TAG, "uploadBook id=${libraryBook.book.id} deletedObsoleteFiles=$deletedFiles")
            }
        } else {
            store.deleteUnexpectedChildren(layout.folderPath, expectedDirectoryNames = emptySet())
        }

        var uploadedFiles = 0
        var skippedFiles = 0
        Log.d(TAG, "uploadBook start id=${libraryBook.book.id} files=${localFiles.size}")
        localFiles.forEach { syncFile ->
            val previousFile = previousFilesByPath[syncFile.remotePath]
            if (!canUsePreviousFileIndex || !syncFile.hasSameSignatureAs(previousFile)) {
                store.replaceFile(
                    path = syncFile.remotePath,
                    source = syncFile.source,
                    mimeType = syncFile.mimeType,
                )
                uploadedFiles += 1
            } else {
                skippedFiles += 1
            }
        }
        Log.d(
            TAG,
            "uploadBook id=${libraryBook.book.id} fullRewrite=${!canUsePreviousFileIndex} " +
                "uploadedFiles=$uploadedFiles skippedFiles=$skippedFiles",
        )
    }

    private fun downloadRemoteBooks(
        store: RemoteLibraryStore,
        actions: List<RemoteDownloadAction>,
        syncedAt: String,
        onDownloaded: (RemoteDownloadAction) -> Unit,
    ) {
        if (actions.isEmpty()) return

        val parallelism = store.maxConcurrentDownloads.coerceAtLeast(1).coerceAtMost(actions.size)
        if (parallelism == 1) {
            actions.forEach { action ->
                downloadRemoteBook(
                    store = store,
                    remoteBook = action.remoteBook,
                    syncedAt = syncedAt,
                    currentBook = action.currentBook,
                )
                onDownloaded(action)
            }
            return
        }

        val executor = Executors.newFixedThreadPool(parallelism)
        val completionService = ExecutorCompletionService<RemoteDownloadAction>(executor)
        try {
            actions.forEach { action ->
                completionService.submit(
                    Callable {
                        downloadRemoteBook(
                            store = store,
                            remoteBook = action.remoteBook,
                            syncedAt = syncedAt,
                            currentBook = action.currentBook,
                        )
                        action
                    },
                )
            }

            repeat(actions.size) {
                onDownloaded(completionService.take().get())
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun uploadBooks(
        store: RemoteLibraryStore,
        books: List<LibraryBook>,
        remoteLayoutsByBookId: Map<String, RemoteBookLayout>,
        remoteBooks: Map<String, ManifestBookSnapshot>,
        onUploaded: (String) -> Unit,
    ) {
        if (books.isEmpty()) return

        val parallelism = store.maxConcurrentUploads.coerceAtLeast(1).coerceAtMost(books.size)
        if (parallelism == 1) {
            books.forEach { book ->
                uploadBook(
                    store = store,
                    libraryBook = book,
                    layout = remoteLayoutsByBookId.getValue(book.book.id),
                    previousRemoteBook = remoteBooks[book.book.id],
                )
                onUploaded(book.book.id)
            }
            return
        }

        val executor = Executors.newFixedThreadPool(parallelism)
        val completionService = ExecutorCompletionService<String>(executor)
        try {
            books.forEach { book ->
                completionService.submit(
                    Callable {
                        uploadBook(
                            store = store,
                            libraryBook = book,
                            layout = remoteLayoutsByBookId.getValue(book.book.id),
                            previousRemoteBook = remoteBooks[book.book.id],
                        )
                        book.book.id
                    },
                )
            }

            repeat(books.size) {
                onUploaded(completionService.take().get())
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun RemoteLibraryStore.readManifest(): LibraryManifestSnapshot? {
        return readText("library.manifest.json")?.let { manifestText ->
            json.decodeFromString<LibraryManifestSnapshot>(manifestText)
        }
    }

    private fun RemoteLibraryStore.readAiCoverPromptSnapshot(): AiCoverPromptLibrarySnapshot? {
        return readText(AI_COVER_PROMPTS_FILE)?.let { promptText ->
            runCatching {
                json.decodeFromString<AiCoverPromptLibrarySnapshot>(promptText)
            }.getOrNull()
        }
    }

    private fun buildLocalManifestIndex(
        localBooks: List<LibraryBook>,
        remoteLayoutsByBookId: Map<String, RemoteBookLayout>,
        remoteBooks: Map<String, ManifestBookSnapshot>,
        previousCache: LocalManifestCacheSnapshot?,
    ): LocalManifestIndex {
        val previousBooksById = previousCache
            ?.takeIf { cache -> cache.schemaVersion == CURRENT_LOCAL_MANIFEST_CACHE_SCHEMA_VERSION }
            ?.books
            ?.associateBy { entry -> entry.bookId }
            .orEmpty()
        val booksById = linkedMapOf<String, ManifestBookSnapshot>()
        val cacheEntries = mutableListOf<LocalManifestCacheEntry>()
        var remoteManifestHits = 0
        var cacheHits = 0
        var cacheMisses = 0

        localBooks.forEach { localBook ->
            val layout = remoteLayoutsByBookId.getValue(localBook.book.id)
            val remoteBook = remoteBooks[localBook.book.id]
            val previousEntry = previousBooksById[localBook.book.id]
            val manifest = when {
                localBook.canReuseRemoteManifest(remoteBook) -> {
                    remoteManifestHits += 1
                    remoteBook!!
                }
                previousEntry?.matches(localBook, layout) == true -> {
                    cacheHits += 1
                    previousEntry.manifest
                }
                else -> {
                    cacheMisses += 1
                    localBook.toManifestBook(layout)
                }
            }
            booksById[localBook.book.id] = manifest
            cacheEntries += LocalManifestCacheEntry.from(localBook, layout, manifest)
        }

        return LocalManifestIndex(
            booksById = booksById,
            snapshot = LocalManifestCacheSnapshot(
                schemaVersion = CURRENT_LOCAL_MANIFEST_CACHE_SCHEMA_VERSION,
                updatedAt = Clock.System.now().toString(),
                books = cacheEntries,
            ),
            remoteManifestHits = remoteManifestHits,
            cacheHits = cacheHits,
            cacheMisses = cacheMisses,
        )
    }

    private fun LibraryBook.canReuseRemoteManifest(remoteBook: ManifestBookSnapshot?): Boolean {
        return remoteBook != null &&
            remoteBook.files.isNotEmpty() &&
            remoteBook.stateBundleFile != null &&
            book.syncStatus == SyncStatus.SYNCED &&
            remoteBook.revision == book.revision &&
            remoteBook.updatedAt == book.updatedAt &&
            remoteBook.updatedByDeviceId == book.updatedByDeviceId &&
            remoteBook.originalHashSha256 == book.originalHashSha256
    }

    private fun readLocalManifestCache(): LocalManifestCacheSnapshot? {
        val cacheFile = localManifestCacheFile()
        if (!cacheFile.exists()) return null
        return runCatching {
            json.decodeFromString<LocalManifestCacheSnapshot>(cacheFile.readText(Charsets.UTF_8))
        }.onFailure { error ->
            Log.w(TAG, "Ignoring invalid local sync manifest cache", error)
            cacheFile.delete()
        }.getOrNull()
    }

    private fun writeLocalManifestCache(snapshot: LocalManifestCacheSnapshot) {
        val cacheFile = localManifestCacheFile()
        runCatching {
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeText(json.encodeToString(snapshot), Charsets.UTF_8)
        }.onFailure { error ->
            Log.w(TAG, "Failed to write local sync manifest cache", error)
        }
    }

    private fun localManifestCacheFile(): File {
        return File(appContext.filesDir, "sync/local-manifest-cache.json")
    }

    private fun RemoteLibraryStore.deleteObsoleteBookFolders(
        bookId: String,
        activeFolderPath: String,
        previousRemoteBook: ManifestBookSnapshot?,
    ) {
        listOfNotNull(
            previousRemoteBook?.bookFolderPath(),
            "books/$bookId",
        )
            .distinct()
            .filterNot { path -> path == activeFolderPath }
            .forEach(::deleteTree)
    }

    private fun libraryId(remoteManifest: LibraryManifestSnapshot?): String {
        if (remoteManifest != null) {
            preferences.edit().putString(KEY_LIBRARY_ID, remoteManifest.libraryId).apply()
            return remoteManifest.libraryId
        }
        val existing = preferences.getString(KEY_LIBRARY_ID, null)
        if (existing != null) return existing
        val created = java.util.UUID.randomUUID().toString()
        preferences.edit().putString(KEY_LIBRARY_ID, created).apply()
        return created
    }

    private fun mergedManifestBooks(
        remoteBooks: Map<String, ManifestBookSnapshot>,
        localBooks: List<LibraryBook>,
        remoteLayoutsByBookId: Map<String, RemoteBookLayout>,
        conflictedBookIds: Set<String>,
        localManifestBooksById: Map<String, ManifestBookSnapshot>,
    ): List<ManifestBookSnapshot> {
        val merged = linkedMapOf<String, ManifestBookSnapshot>()
        remoteBooks.values.forEach { remoteBook -> merged[remoteBook.bookId] = remoteBook }

        localBooks
            .filter { it.book.syncStatus == SyncStatus.CONFLICT || it.book.id in conflictedBookIds }
            .mapNotNull { remoteBooks[it.book.id] }
            .forEach { remoteBook -> merged[remoteBook.bookId] = remoteBook }

        localBooks
            .filterNot { it.book.syncStatus == SyncStatus.CONFLICT || it.book.id in conflictedBookIds }
            .map { localBook ->
                localManifestBooksById[localBook.book.id]
                    ?: localBook.toManifestBook(remoteLayoutsByBookId.getValue(localBook.book.id))
            }
            .forEach { localBook -> merged[localBook.bookId] = localBook }

        return merged.values.toList()
    }

    private fun ManifestBookSnapshot.bookFolderPath(): String {
        return metadataFile.substringBeforeLast(
            delimiter = "/",
            missingDelimiterValue = originalFile.substringBeforeLast(
                delimiter = "/",
                missingDelimiterValue = "books/$bookId",
            ),
        )
    }

    private fun ManifestBookSnapshot.bookFileName(fileName: String): String {
        return "${bookFolderPath()}/$fileName"
    }

    private fun ManifestBookSnapshot.deleteRemoteFiles(store: RemoteLibraryStore) {
        listOf(bookFolderPath(), "books/$bookId")
            .distinct()
            .forEach(store::deleteTree)
    }

    private fun ManifestBookSnapshot.hasDifferentPathsThan(expected: ManifestBookSnapshot): Boolean {
        if (metadataFile != expected.metadataFile ||
            originalFile != expected.originalFile ||
            preparedFile != expected.preparedFile ||
            coverFile != expected.coverFile ||
            stateBundleFile != expected.stateBundleFile
        ) {
            return true
        }

        val currentFilePaths = files.mapTo(mutableSetOf()) { file -> file.path }
        val expectedFilePaths = expected.files.mapTo(mutableSetOf()) { file -> file.path }
        return currentFilePaths.isNotEmpty() &&
            expectedFilePaths.isNotEmpty() &&
            currentFilePaths != expectedFilePaths
    }

    private fun ManifestBookSnapshot.hasDifferentFileSignaturesThan(expected: ManifestBookSnapshot): Boolean {
        if (files.isEmpty() || expected.files.isEmpty()) return false
        val currentFilesByPath = files.associateBy { file -> file.path }
        val expectedFilesByPath = expected.files.associateBy { file -> file.path }
        if (currentFilesByPath.keys != expectedFilesByPath.keys) return true
        return expectedFilesByPath.any { (path, expectedFile) ->
            currentFilesByPath[path]?.hasSameSignatureAs(expectedFile) != true
        }
    }

    private fun ManifestFileSnapshot.hasSameSignatureAs(other: ManifestFileSnapshot): Boolean {
        return path == other.path &&
            mimeType == other.mimeType &&
            sizeBytes == other.sizeBytes &&
            modifiedAtEpochMillis == other.modifiedAtEpochMillis
    }

    private fun ManifestBookSnapshot.fileSnapshot(path: String): ManifestFileSnapshot? {
        return files.firstOrNull { file -> file.path == path }
    }

    private fun File.applyManifestModifiedTime(
        remoteBook: ManifestBookSnapshot,
        remotePath: String,
    ): File {
        remoteBook.fileSnapshot(remotePath)
            ?.modifiedAtEpochMillis
            ?.takeIf { modifiedAt -> modifiedAt > 0L }
            ?.let(::setLastModified)
        return this
    }

    private fun MetadataSnapshot.toDomain(): BookMetadata {
        return BookMetadata(
            bookId = bookId,
            title = title,
            subtitle = subtitle,
            authors = authors,
            sortAuthor = sortAuthor,
            language = language,
            publisher = publisher,
            publishedDate = publishedDate,
            description = description,
            tags = tags,
            isbn = isbn,
            identifiers = identifiers,
            seriesName = seriesName,
            seriesIndex = seriesIndex,
            source = if (status.toBookStatus() == BookStatus.IMPORTED) {
                MetadataSource.EPUB
            } else {
                MetadataSource.MANUAL
            },
        )
    }

    private fun TechnicalSnapshot.toDomain(): EpubTechnicalInfo {
        return EpubTechnicalInfo(
            bookId = bookId,
            epubVersion = epubVersion,
            opfPath = opfPath,
            navPath = navPath,
            ncxPath = ncxPath,
            coverHref = coverHref,
            packageIdentifier = packageIdentifier,
            hasNav = hasNav,
            hasNcx = hasNcx,
            manifestItemCount = manifestItemCount,
            spineItemCount = spineItemCount,
            warnings = warnings,
            errors = errors,
        )
    }

    private fun String.toBookStatus(): BookStatus {
        return runCatching { BookStatus.valueOf(this) }.getOrDefault(BookStatus.IMPORTED)
    }

    private fun List<LibraryBook>.remoteLayoutsByBookId(): Map<String, RemoteBookLayout> {
        val baseNameCounts = groupingBy { book -> book.remoteFolderBaseName() }.eachCount()
        return associate { book ->
            val baseName = book.remoteFolderBaseName()
            val folderName = if (baseNameCounts.getValue(baseName) > 1) {
                "$baseName - ${book.book.id}"
            } else {
                baseName
            }
            book.book.id to book.remoteLayout(folderName)
        }
    }

    private fun LibraryBook.remoteFolderBaseName(): String {
        val author = metadata.authors.firstOrNull()
            ?.sanitizeRemotePathPart()
            ?.ifBlank { null }
            ?: "Autor desconhecido"
        val title = metadata.title.sanitizeRemotePathPart().ifBlank { "Livro sem titulo" }
        return "$author - $title".takeRemotePathLength()
    }

    private fun LibraryBook.remoteLayout(folderName: String): RemoteBookLayout {
        val safeTitle = metadata.title.sanitizeRemotePathPart().ifBlank { "Livro sem titulo" }
        val prepared = book.preparedFilePath?.let { File(it) }?.takeIf { it.exists() }
        val cover = book.coverImagePath?.let { File(it) }?.takeIf { it.exists() }
        val epubFileName = "$safeTitle.epub".takeRemotePathLength(extension = ".epub")
        val folderPath = "books/$folderName"
        val epubFile = "$folderPath/$epubFileName"
        return RemoteBookLayout(
            folderName = folderName,
            folderPath = folderPath,
            epubFileName = epubFileName,
            metadataFile = "$folderPath/metadata.json",
            epubFile = epubFile,
            preparedFile = prepared?.let { epubFile },
            coverFile = cover?.let { "$folderPath/cover_original.jpg" },
            stateBundleFile = "$folderPath/$STATE_BUNDLE_FILE_NAME",
        )
    }

    private fun LibraryBook.toManifestBook(
        layout: RemoteBookLayout,
        files: List<ManifestFileSnapshot> = localSyncFiles(layout).map { file -> file.toManifestFileSnapshot() },
    ): ManifestBookSnapshot {
        return ManifestBookSnapshot(
            bookId = book.id,
            revision = book.revision,
            updatedAt = book.updatedAt,
            updatedByDeviceId = book.updatedByDeviceId,
            originalHashSha256 = book.originalHashSha256,
            metadataFile = layout.metadataFile,
            originalFile = layout.epubFile,
            preparedFile = layout.preparedFile,
            coverFile = layout.coverFile,
            deleted = false,
            files = files,
            stateBundleFile = files
                .firstOrNull { file -> file.path == layout.stateBundleFile }
                ?.path,
        )
    }

    private fun LibraryBook.localSyncFiles(
        layout: RemoteBookLayout,
        localDir: File = File(book.originalFilePath).parentFile
            ?: error("Missing local directory for ${book.id}"),
        epubSource: File = book.preparedFilePath
            ?.let(::File)
            ?.takeIf { it.exists() }
            ?: localDir.resolve("original.epub"),
    ): List<LocalSyncFile> {
        val epubFile = LocalSyncFile(epubSource, "${layout.folderPath}/${layout.epubFileName}", "application/epub+zip")
        val stateBundle = buildStateBundle(localDir, book.id)
        return listOf(
            epubFile,
            LocalSyncFile(stateBundle, layout.stateBundleFile, STATE_BUNDLE_MIME_TYPE),
        ).filter { file -> file.source.exists() }
    }

    private fun buildStateBundle(
        localDir: File,
        bookId: String,
    ): File {
        val sources = stateBundleSources(localDir)
        val hasRequiredSnapshots = sources.any { it.entryName == "metadata.json" } &&
            sources.any { it.entryName == "technical.json" }
        check(hasRequiredSnapshots) {
            "Missing local metadata or technical snapshot for $bookId"
        }

        val bundleFile = File(appContext.cacheDir, "sync-state/$bookId/$STATE_BUNDLE_FILE_NAME")
        bundleFile.parentFile?.mkdirs()
        ZipOutputStream(bundleFile.outputStream().buffered()).use { output ->
            sources.forEach { source ->
                val entry = ZipEntry(source.entryName).apply {
                    time = source.file.lastModified().takeIf { it > 0L } ?: 0L
                }
                output.putNextEntry(entry)
                source.file.inputStream().buffered().use { input ->
                    input.copyTo(output)
                }
                output.closeEntry()
            }
        }
        sources.maxOfOrNull { source -> source.file.lastModified() }
            ?.takeIf { modifiedAt -> modifiedAt > 0L }
            ?.let(bundleFile::setLastModified)
        return bundleFile
    }

    private fun stateBundleSources(localDir: File): List<StateBundleSource> {
        return listOf(
            "metadata.json",
            "technical.json",
            "changes.jsonl",
            "cover_original.jpg",
            "cover_thumb.jpg",
        ).mapNotNull { fileName ->
            localDir.resolve(fileName)
                .takeIf { file -> file.exists() && file.isFile }
                ?.let { file -> StateBundleSource(fileName, file) }
        }
    }

    private fun String.sanitizeRemotePathPart(): String {
        return trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .takeRemotePathLength()
    }

    private fun String.takeRemotePathLength(
        maxLength: Int = 96,
        extension: String = "",
    ): String {
        if (length <= maxLength) return trim()
        val extensionless = removeSuffix(extension)
        return if (extension.isBlank() || extensionless == this) {
            take(maxLength).trim()
        } else {
            extensionless.take(maxLength - extension.length).trim() + extension
        }
    }

    @Serializable
    private data class LibraryManifestSnapshot(
        val schemaVersion: Int,
        val libraryId: String,
        val updatedAt: String,
        val updatedByDeviceId: String,
        val aiCoverPromptsFile: String? = null,
        val books: List<ManifestBookSnapshot>,
    )

    @Serializable
    private data class ManifestBookSnapshot(
        val bookId: String,
        val revision: Long,
        val updatedAt: String,
        val updatedByDeviceId: String,
        val originalHashSha256: String,
        val metadataFile: String,
        val originalFile: String,
        val preparedFile: String?,
        val coverFile: String?,
        val deleted: Boolean,
        val files: List<ManifestFileSnapshot> = emptyList(),
        val stateBundleFile: String? = null,
    )

    @Serializable
    private data class ManifestFileSnapshot(
        val path: String,
        val name: String,
        val mimeType: String,
        val sizeBytes: Long,
        val modifiedAtEpochMillis: Long,
    )

    @Serializable
    internal data class DeviceSnapshot(
        val deviceId: String,
        val deviceName: String,
        val platform: String,
        val updatedAt: String,
    )

    @Serializable
    private data class LocalManifestCacheSnapshot(
        val schemaVersion: Int,
        val updatedAt: String,
        val books: List<LocalManifestCacheEntry>,
    )

    @Serializable
    private data class LocalManifestCacheEntry(
        val bookId: String,
        val revision: Long,
        val updatedAt: String,
        val updatedByDeviceId: String,
        val status: String,
        val originalHashSha256: String,
        val fileSizeBytes: Long,
        val originalFilePath: String,
        val preparedFilePath: String?,
        val coverImagePath: String?,
        val coverThumbPath: String?,
        val layoutFolderPath: String,
        val layoutMetadataFile: String,
        val layoutOriginalFile: String,
        val layoutPreparedFile: String?,
        val layoutCoverFile: String?,
        val layoutStateBundleFile: String? = null,
        val manifest: ManifestBookSnapshot,
    ) {
        fun matches(
            localBook: LibraryBook,
            layout: RemoteBookLayout,
        ): Boolean {
            return manifest.files.isNotEmpty() &&
                manifest.stateBundleFile != null &&
                bookId == localBook.book.id &&
                revision == localBook.book.revision &&
                updatedAt == localBook.book.updatedAt &&
                updatedByDeviceId == localBook.book.updatedByDeviceId &&
                status == localBook.book.status.name &&
                originalHashSha256 == localBook.book.originalHashSha256 &&
                fileSizeBytes == localBook.book.fileSizeBytes &&
                originalFilePath == localBook.book.originalFilePath &&
                preparedFilePath == localBook.book.preparedFilePath &&
                coverImagePath == localBook.book.coverImagePath &&
                coverThumbPath == localBook.book.coverThumbPath &&
                layoutFolderPath == layout.folderPath &&
                layoutMetadataFile == layout.metadataFile &&
                layoutOriginalFile == layout.epubFile &&
                layoutPreparedFile == layout.preparedFile &&
                layoutCoverFile == layout.coverFile &&
                layoutStateBundleFile == layout.stateBundleFile &&
                manifest.metadataFile == layout.metadataFile &&
                manifest.originalFile == layout.epubFile &&
                manifest.preparedFile == layout.preparedFile &&
                manifest.coverFile == layout.coverFile
        }

        companion object {
            fun from(
                localBook: LibraryBook,
                layout: RemoteBookLayout,
                manifest: ManifestBookSnapshot,
            ): LocalManifestCacheEntry {
                return LocalManifestCacheEntry(
                    bookId = localBook.book.id,
                    revision = localBook.book.revision,
                    updatedAt = localBook.book.updatedAt,
                    updatedByDeviceId = localBook.book.updatedByDeviceId,
                    status = localBook.book.status.name,
                    originalHashSha256 = localBook.book.originalHashSha256,
                    fileSizeBytes = localBook.book.fileSizeBytes,
                    originalFilePath = localBook.book.originalFilePath,
                    preparedFilePath = localBook.book.preparedFilePath,
                    coverImagePath = localBook.book.coverImagePath,
                    coverThumbPath = localBook.book.coverThumbPath,
                    layoutFolderPath = layout.folderPath,
                    layoutMetadataFile = layout.metadataFile,
                    layoutOriginalFile = layout.epubFile,
                    layoutPreparedFile = layout.preparedFile,
                    layoutCoverFile = layout.coverFile,
                    layoutStateBundleFile = layout.stateBundleFile,
                    manifest = manifest,
                )
            }
        }
    }

    private data class SyncCounters(
        var uploadedBooks: Int = 0,
        var downloadedBooks: Int = 0,
        var conflictedBooks: Int = 0,
    )

    private data class LocalManifestIndex(
        val booksById: Map<String, ManifestBookSnapshot>,
        val snapshot: LocalManifestCacheSnapshot,
        val remoteManifestHits: Int,
        val cacheHits: Int,
        val cacheMisses: Int,
    )

    private data class StateBundleSource(
        val entryName: String,
        val file: File,
    )

    private data class RemoteDownloadAction(
        val remoteBook: ManifestBookSnapshot,
        val currentBook: LibraryBook?,
        val duplicateBookToRemove: LibraryBook? = null,
    )

    @Serializable
    private data class AiCoverPromptLibrarySnapshot(
        val schemaVersion: Int,
        val updatedAt: String,
        val updatedByDeviceId: String,
        val records: List<AiCoverPromptRecord>,
    )

    private data class AiCoverPromptSync(
        val mergedRecords: List<AiCoverPromptRecord>,
        val shouldWriteRemote: Boolean,
        val snapshot: AiCoverPromptLibrarySnapshot,
    )

    private data class LocalSyncFile(
        val source: File,
        val remotePath: String,
        val mimeType: String,
    ) {
        fun toManifestFileSnapshot(): ManifestFileSnapshot {
            return ManifestFileSnapshot(
                path = remotePath,
                name = remotePath.remoteName(),
                mimeType = mimeType,
                sizeBytes = source.length(),
                modifiedAtEpochMillis = source.lastModified(),
            )
        }

        fun hasSameSignatureAs(remoteFile: ManifestFileSnapshot?): Boolean {
            val localFile = toManifestFileSnapshot()
            return remoteFile != null &&
                localFile.path == remoteFile.path &&
                localFile.mimeType == remoteFile.mimeType &&
                localFile.sizeBytes == remoteFile.sizeBytes &&
                localFile.modifiedAtEpochMillis == remoteFile.modifiedAtEpochMillis
        }
    }

    private data class RemoteBookLayout(
        val folderName: String,
        val folderPath: String,
        val epubFileName: String,
        val metadataFile: String,
        val epubFile: String,
        val preparedFile: String?,
        val coverFile: String?,
        val stateBundleFile: String,
    )

    private data class PendingSyncStatusUpdates(
        val syncedBookIds: LinkedHashSet<String> = linkedSetOf(),
        val conflictedBookIds: LinkedHashSet<String> = linkedSetOf(),
    ) {
        fun hasUpdates(): Boolean = syncedBookIds.isNotEmpty() || conflictedBookIds.isNotEmpty()
    }

    private companion object {
        const val KEY_LIBRARY_ID = "library_id"
        const val KEY_SYNC_UPLOAD_STRATEGY_VERSION = "sync_upload_strategy_version"
        const val CURRENT_SYNC_UPLOAD_STRATEGY_VERSION = 3
        const val CURRENT_MANIFEST_SCHEMA_VERSION = 3
        const val CURRENT_LOCAL_MANIFEST_CACHE_SCHEMA_VERSION = 1
        const val AI_COVER_PROMPTS_FILE = "ai-cover-prompts.json"
        const val STATE_BUNDLE_FILE_NAME = "book-state.zip"
        const val STATE_BUNDLE_MIME_TYPE = "application/zip"
        val STATE_BUNDLE_ENTRY_NAMES = setOf(
            "metadata.json",
            "technical.json",
            "changes.jsonl",
            "cover_original.jpg",
            "cover_thumb.jpg",
        )
        const val TAG = "FolioreSync"
    }
}

private fun List<AiCoverPromptRecord>.normalizedAiCoverPromptRecords(): List<AiCoverPromptRecord> {
    return groupBy { it.id }
        .values
        .map { records -> records.maxBy { it.selectedAtEpochMillis } }
        .sortedWith(compareBy<AiCoverPromptRecord> { it.selectedAtEpochMillis }.thenBy { it.id })
}
