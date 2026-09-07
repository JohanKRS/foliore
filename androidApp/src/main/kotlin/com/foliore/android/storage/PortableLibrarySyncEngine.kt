package com.foliore.android.storage

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.foliore.android.ai.AiCoverPromptRecord
import com.foliore.shared.data.MetadataSnapshot
import com.foliore.shared.data.PortableSnapshots
import com.foliore.shared.data.TechnicalSnapshot
import com.foliore.shared.domain.Book
import com.foliore.shared.domain.BookMetadata
import com.foliore.shared.domain.BookStatus
import com.foliore.shared.domain.EpubTechnicalInfo
import com.foliore.shared.domain.ImportedBookRecord
import com.foliore.shared.domain.LibraryBook
import com.foliore.shared.domain.MetadataSource
import com.foliore.shared.domain.SyncStatus
import com.foliore.shared.epub.EpubParseResult
import com.foliore.shared.epub.EpubParser
import com.foliore.shared.library.LibraryRepository
import java.io.File
import java.io.IOException
import java.util.UUID
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
import kotlinx.serialization.json.*

internal class PortableLibrarySyncEngine(
    context: Context,
    private val libraryRepository: LibraryRepository,
    private val deviceIdProvider: DeviceIdProvider,
    private val aiCoverPromptRepository: AiCoverPromptRepository,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(SyncPreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)
    private var cacheScope = "default"
    private val repositoryLock = SyncOperationCoordinator.localDataLock
    private val epubParser = EpubParser()
    private val coverImageWriter = CoverImageWriter()
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun sync(
        store: RemoteLibraryStore,
        onProgress: (ManualSyncProgress) -> Unit = {},
    ): ManualSyncResult.Synced {
        val uploadSnapshotDir = File(appContext.cacheDir, "sync-upload/${UUID.randomUUID()}")
        return try { syncSnapshot(store, uploadSnapshotDir, onProgress) }
        finally { uploadSnapshotDir.deleteRecursively() }
    }

    private fun syncSnapshot(store: RemoteLibraryStore, uploadSnapshotDir: File,
        onProgress: (ManualSyncProgress) -> Unit): ManualSyncResult.Synced {
        cacheScope = java.security.MessageDigest.getInstance("SHA-256").digest(store.deletionScope.orEmpty().toByteArray())
            .joinToString("") { "%02x".format(it) }
        val previousManifestCache = readLocalManifestCache()
        val previousBaseById = previousManifestCache?.books.orEmpty().associate { it.bookId to it.manifest }
        val pendingRemoteDeletionStore = PendingRemoteDeletionStore(appContext, store.deletionScope)
        val startedAtElapsedMillis = SystemClock.elapsedRealtime()
        onProgress(ManualSyncProgress(ManualSyncPhase.PREPARING))
        val syncedAt = Clock.System.now().toString()
        val deviceId = deviceIdProvider.getDeviceId()
        val journal = RemoteManifestJournal(store)
        val remoteManifest = journal.read()?.let { json.decodeFromString<LibraryManifestSnapshot>(it) }
        remoteManifest?.validate()
        val remoteBooks = remoteManifest?.books
            ?.filterNot { it.deleted }
            ?.associateBy { it.bookId }
            .orEmpty()
        val counters = SyncCounters()
        val pendingStatusUpdates = PendingSyncStatusUpdates()
        val localBooks = libraryRepository.getBooks()
        val localBooksById = localBooks.associateBy { it.book.id }
        val pendingDeletedBookIds = pendingRemoteDeletionStore.pendingBookIds() - localBooksById.keys
        remoteManifest?.books.orEmpty().filter { it.deleted }.forEach { tombstone ->
            synchronized(repositoryLock) {
                val local = libraryRepository.getBook(tombstone.bookId)
                if (local != null) {
                    if (local.book.syncStatus == SyncStatus.SYNCED && local.book.revision <= tombstone.revision) {
                        libraryRepository.deleteBook(tombstone.bookId)
                    } else {
                        preserveLocalConflict(local)
                        libraryRepository.deleteBook(tombstone.bookId)
                        counters.conflictedBooks += 1
                    }
                }
            }
        }
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
            val shouldDeleteRemoteBook = remoteBook.bookId in pendingDeletedBookIds &&
                pendingRemoteDeletionStore.matchesDeletedVersion(remoteBook.bookId, remoteBook.revision, remoteBook.updatedAt)
            val hasCurrentRemoteFiles = remoteManifest?.schemaVersion?.let { it >= CURRENT_MANIFEST_SCHEMA_VERSION } == true &&
                remoteBook.files.isNotEmpty() &&
                remoteBook.stateBundleFile != null

            if (shouldDeleteRemoteBook) {
                removedRemoteBookIds += remoteBook.bookId
            } else if (hasCurrentRemoteFiles && localBook != null &&
                localBook.book.syncStatus in setOf(SyncStatus.PENDING_UPLOAD, SyncStatus.CONFLICT) &&
                previousBaseById[localBook.book.id]?.let { base ->
                    base.metadataSnapshot != remoteBook.metadataSnapshot || base.coverHash != remoteBook.coverHash
                } != false
            ) {
                remoteDownloadActions += reconcileOfflineEdit(remoteBook, localBook, previousBaseById[localBook.book.id], counters)
            } else if (hasCurrentRemoteFiles) {
                planRemoteBookAction(
                    remoteBook = remoteBook,
                    local = localBook,
                    currentDeviceId = deviceId,
                    counters = counters,
                    pendingStatusUpdates = pendingStatusUpdates,
                )?.let(remoteDownloadActions::add)
            } else if (localBook == null) {
                counters.conflictedBooks += 1
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
            onFinished = { action, result, error ->
                downloadProgress += 1
                var committedBookId: String? = null
                var removedLocalBookId: String? = null
                if (error == null) {
                    counters.downloadedBooks += 1
                    committedBookId = action.remoteBook.bookId
                    if (result?.recoveredStateBundle == true) {
                        missingRemoteBookIds += action.remoteBook.bookId
                    }
                    action.duplicateBookToRemove?.let { duplicateBook ->
                        removeLocalDuplicateBook(duplicateBook)
                        removedLocalBookId = duplicateBook.book.id
                        pendingStatusUpdates.syncedBookIds += action.remoteBook.bookId
                    }
                } else {
                    counters.conflictedBooks += 1
                    action.currentBook?.book?.id?.let(pendingStatusUpdates.conflictedBookIds::add)
                    Log.e(
                        TAG,
                        "Skipping remote book id=${action.remoteBook.bookId} after download failure",
                        error,
                    )
                }
                onProgress(
                    ManualSyncProgress(
                        phase = ManualSyncPhase.DOWNLOADING,
                        current = downloadProgress,
                        total = remoteDownloadActions.size,
                        committedBookId = committedBookId,
                        removedLocalBookId = removedLocalBookId,
                    ),
                )
            },
        )

        val shouldRewriteRemoteMetadataBase = remoteManifest == null ||
            (remoteManifest.schemaVersion < CURRENT_MANIFEST_SCHEMA_VERSION) ||
            missingRemoteBookIds.isNotEmpty() ||
            removedRemoteBookIds.isNotEmpty() ||
            remoteBooks.values.any { it.autoMerged }

        val booksAfterRemote = synchronized(repositoryLock) {
            libraryRepository.getBooks().map { source -> snapshotLocalBook(source, uploadSnapshotDir) }
        }
        val remoteLayoutsByBookId = booksAfterRemote.associate { local ->
            val remote = remoteBooks[local.book.id]
            val reuse = remote != null && remote.originalFile.startsWith(RemoteManifestJournal.DATA_PREFIX) &&
                local.book.syncStatus == SyncStatus.SYNCED && local.book.revision == remote.revision &&
                local.book.updatedAt == remote.updatedAt && local.book.id !in missingRemoteBookIds &&
                local.localSyncFiles(remote.toLayout()).all { file -> file.hasSameSignatureAs(remote.files.firstOrNull { it.path == file.remotePath }) }
            local.book.id to if (reuse) remote!!.toLayout() else local.remoteLayout(UUID.randomUUID().toString())
        }
        val localManifestIndex = buildLocalManifestIndex(
            localBooks = booksAfterRemote,
            remoteLayoutsByBookId = remoteLayoutsByBookId,
            remoteBooks = remoteBooks,
            previousCache = previousManifestCache,
        )
        val expectedRemoteBooksById = localManifestIndex.booksById
        val shouldRewriteRemoteMetadata = shouldRewriteRemoteMetadataBase ||
            booksAfterRemote.any { book ->
                book.book.syncStatus != SyncStatus.CONFLICT &&
                    book.book.id !in pendingStatusUpdates.conflictedBookIds &&
                    remoteBooks[book.book.id]?.files?.isEmpty() == true
            }
        val shouldRefreshRemoteBookFolders = false
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
            remoteBooks = remoteBooks - missingRemoteBookIds,
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
        val cleanedRemoteBooks = false // Collection uses committed remote references, never the local library.
        val aiCoverPromptSync = syncAiCoverPrompts(
            remoteRecords = remoteManifest?.aiCoverPrompts.orEmpty(),
            syncedAt = syncedAt,
            deviceId = deviceId,
        )

        if (counters.uploadedBooks > 0 ||
            shouldRewriteRemoteMetadata ||
            cleanedRemoteBooks ||
            aiCoverPromptSync.shouldWriteRemote
        ) {
            onProgress(ManualSyncProgress(ManualSyncPhase.WRITING_MANIFEST))
            journal.publish(
                json.encodeToString(
                    LibraryManifestSnapshot(
                        schemaVersion = CURRENT_MANIFEST_SCHEMA_VERSION,
                        libraryId = libraryId(remoteManifest),
                        updatedAt = syncedAt,
                        updatedByDeviceId = deviceId,
                        aiCoverPrompts = aiCoverPromptSync.mergedRecords,
                        deviceName = android.os.Build.MODEL,
                        books = mergedManifestBooks(
                            remoteBooks = remoteBooks.mapValues { (id, book) -> if (id in removedRemoteBookIds) book.copy(deleted = true) else book } +
                                remoteManifest?.books.orEmpty().filter { it.deleted }.associateBy { it.bookId },
                            localBooks = booksAfterRemote,
                            remoteLayoutsByBookId = remoteLayoutsByBookId,
                            conflictedBookIds = pendingStatusUpdates.conflictedBookIds,
                            localManifestBooksById = expectedRemoteBooksById,
                        ),
                    ),
                ),
            )
        }

        if (pendingStatusUpdates.hasUpdates()) {
            onProgress(ManualSyncProgress(ManualSyncPhase.APPLYING_STATUS))
            pendingStatusUpdates.conflictedBookIds.forEach { bookId ->
                synchronized(repositoryLock) { libraryRepository.markSyncConflict(bookId) }
            }
            pendingStatusUpdates.syncedBookIds
                .filterNot { it in pendingStatusUpdates.conflictedBookIds }
                .forEach { bookId ->
                    libraryRepository.markSynced(
                        bookId, syncedAt,
                        expectedRevision = booksAfterRemote.firstOrNull { it.book.id == bookId }?.book?.revision,
                        expectedUpdatedAt = booksAfterRemote.firstOrNull { it.book.id == bookId }?.book?.updatedAt,
                    )
                }
        }

        if (shouldRefreshRemoteBookFolders) {
            preferences.edit()
                .putInt(KEY_SYNC_UPLOAD_STRATEGY_VERSION, CURRENT_SYNC_UPLOAD_STRATEGY_VERSION)
                .apply()
        }
        pendingRemoteDeletionStore.clear(removedRemoteBookIds + pendingDeletedBookIds.filter { it !in remoteBooks })
        writeLocalManifestCache(localManifestIndex.snapshot.copy(books = localManifestIndex.snapshot.books.mapNotNull { entry ->
            if (entry.bookId in pendingStatusUpdates.conflictedBookIds) previousManifestCache?.books?.firstOrNull { it.bookId == entry.bookId }
            else entry
        }))
        runCatching { journal.collectGarbage() }.onFailure { Log.w(TAG, "Deferred recovery history cleanup", it) }

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
        remoteRecords: List<AiCoverPromptRecord>,
        syncedAt: String,
        deviceId: String,
    ): AiCoverPromptSync {
        val mergedRecords = aiCoverPromptRepository.mergeAll(remoteRecords)
        val shouldWriteRemote = mergedRecords != remoteRecords.normalizedAiCoverPromptRecords()

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

    private fun snapshotLocalBook(source: LibraryBook, root: File, destination: File? = null): LibraryBook {
        val directory = (destination ?: safeChildDirectory(root, source.book.id)).apply { mkdirs() }
        val originalDirectory = File(source.book.originalFilePath).parentFile ?: error("Missing local book")
        fun copy(path: String?, name: String): String? = path?.let {
            val input = File(it)
            check(input.isFile) { "Missing local file: $name" }
            input.copyTo(File(directory, name), overwrite = false).apply { setLastModified(input.lastModified()) }.absolutePath
        }
        val original = checkNotNull(copy(source.book.originalFilePath, "original.epub"))
        check(File(original).sha256() == source.book.originalHashSha256) { "Local original EPUB checksum mismatch" }
        stateBundleSources(originalDirectory).forEach { entry ->
            entry.file.copyTo(File(directory, entry.entryName)).setLastModified(entry.file.lastModified())
        }
        return source.copy(book = source.book.copy(
            originalFilePath = original,
            preparedFilePath = copy(source.book.preparedFilePath, "prepared.epub"),
            coverImagePath = source.book.coverImagePath?.let { File(directory, "cover_original.jpg").absolutePath },
            coverThumbPath = source.book.coverThumbPath?.let { File(directory, "cover_thumb.jpg").absolutePath },
        ))
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

    private fun reconcileOfflineEdit(remote: ManifestBookSnapshot, local: LibraryBook,
        base: ManifestBookSnapshot?, counters: SyncCounters): RemoteDownloadAction = synchronized(repositoryLock) {
        val baseMetadata = base?.metadataSnapshot?.let { json.encodeToJsonElement(it) }
        val merged = if (baseMetadata != null && remote.metadataSnapshot != null) {
            val remoteJson = json.encodeToJsonElement(remote).jsonObject
            val localJson = JsonObject(remoteJson + mapOf(
                "metadataSnapshot" to json.encodeToJsonElement(PortableSnapshots.metadata(local.book, local.metadata)),
                "revision" to JsonPrimitive(local.book.revision), "updatedAt" to JsonPrimitive(local.book.updatedAt),
                "updatedByDeviceId" to JsonPrimitive(local.book.updatedByDeviceId),
                "coverHash" to (local.book.coverImagePath?.let(::File)?.takeIf { it.isFile }?.sha256()?.let(::JsonPrimitive) ?: JsonNull),
                "mergeBase" to baseMetadata,
            ))
            RemoteManifestJournal.mergeIndependentMetadata(listOf(localJson, JsonObject(remoteJson + ("mergeBase" to baseMetadata))))
        } else null
        if (merged != null) {
            RemoteDownloadAction(json.decodeFromJsonElement<ManifestBookSnapshot>(merged), local)
        } else {
            // Preserve the unsynchronized local branch as a normal, editable book
            // before bringing in the remote branch. Originals can share a hash.
            preserveLocalConflict(local)
            counters.conflictedBooks += 1
            RemoteDownloadAction(remote, local)
        }
    }

    private fun preserveLocalConflict(local: LibraryBook) {
        val recoveryId = UUID.nameUUIDFromBytes((local.book.id + local.book.updatedByDeviceId + local.book.revision + local.book.updatedAt).toByteArray()).toString()
        if (libraryRepository.getBook(recoveryId) == null) {
            val root = File(appContext.filesDir, "books")
            val destination = File(safeChildDirectory(root, recoveryId), "versions/${UUID.randomUUID()}")
            val recovery = snapshotLocalBook(local.copy(book = local.book.copy(id = recoveryId)), root, destination)
            val metadata = recovery.metadata.copy(bookId = recoveryId, title = recovery.metadata.title + " (recovered conflict)")
            val book = recovery.book.copy(revision = local.book.revision + 1, updatedAt = Clock.System.now().toString(),
                updatedByDeviceId = deviceIdProvider.getDeviceId(), lastSyncedAt = null, syncStatus = SyncStatus.LOCAL_ONLY)
            val directory = File(book.originalFilePath).parentFile!!
            val technical = json.decodeFromString<TechnicalSnapshot>(File(directory, "technical.json").readText()).toDomain().copy(bookId = recoveryId)
            writeRecoveredPortableSnapshots(directory, book, metadata, technical)
            libraryRepository.insertImportedBook(ImportedBookRecord(book, metadata, technical))
        }
    }

    private fun planRemoteBookAction(
        remoteBook: ManifestBookSnapshot,
        local: LibraryBook?,
        currentDeviceId: String,
        counters: SyncCounters,
        pendingStatusUpdates: PendingSyncStatusUpdates,
    ): RemoteDownloadAction? {
        if (local == null) {
            return RemoteDownloadAction(
                remoteBook = remoteBook,
                currentBook = null,
            )
        }

        // A synchronized local branch is already preserved in the journal. Apply
        // its causal result even when concurrent devices used the same revision.
        if (local.book.syncStatus == SyncStatus.SYNCED) {
            val changed = remoteBook.revision != local.book.revision ||
                remoteBook.updatedAt != local.book.updatedAt ||
                remoteBook.updatedByDeviceId != local.book.updatedByDeviceId
            return if (changed) RemoteDownloadAction(remoteBook, local) else null
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
    ): RemoteDownloadResult {
        val startedAtElapsedMillis = SystemClock.elapsedRealtime()
        Log.d(TAG, "downloadBook start id=${remoteBook.bookId}")
        val destinationDir = File(safeChildDirectory(File(appContext.filesDir, "books"), remoteBook.bookId), "versions/${UUID.randomUUID()}")
        val stagingDir = File(
            appContext.cacheDir,
            "sync-download/${remoteBook.bookId}-${UUID.randomUUID()}",
        ).apply { mkdirs() }

        return try {
            val hasStateBundle = remoteBook.stateBundleFile?.let { stateBundlePath ->
                store.copyToLocal(
                    stateBundlePath,
                    File(stagingDir, STATE_BUNDLE_FILE_NAME),
                )
                    ?.applyManifestModifiedTime(remoteBook, stateBundlePath)
                    ?.also { bundleFile ->
                        extractStateBundle(bundleFile, stagingDir)
                        bundleFile.delete()
                    } != null
            } == true

            val stagedOriginalFile = File(stagingDir, "original.epub")
            store.copyToLocal(remoteBook.originalFile, stagedOriginalFile)
                ?.applyManifestModifiedTime(remoteBook, remoteBook.originalFile)
                ?: throw MissingRemoteBookFileException(
                    bookId = remoteBook.bookId,
                    remotePath = remoteBook.originalFile,
                )
            val stagedPreparedFile = remoteBook.preparedFile?.let { path ->
                store.copyToLocal(path, File(stagingDir, "prepared.epub"))
                    ?.applyManifestModifiedTime(remoteBook, path)
            }

            val downloadedState = if (hasStateBundle) {
                readBundledBookState(stagingDir, remoteBook)
            } else {
                Log.w(
                    TAG,
                    "Missing remote state bundle id=${remoteBook.bookId}; recovering metadata from EPUB",
                )
                recoverBookStateFromEpub(
                    bookDir = stagingDir,
                    originalFile = stagedOriginalFile,
                    remoteBook = remoteBook,
                )
            }

            val destinationOriginalFile = File(destinationDir, "original.epub")
            val destinationPreparedFile = stagedPreparedFile?.let { File(destinationDir, "prepared.epub") }
            val destinationCoverFile = File(destinationDir, "cover_original.jpg")
                .takeIf { File(stagingDir, "cover_original.jpg").exists() }
            val destinationCoverThumbFile = File(destinationDir, "cover_thumb.jpg")
                .takeIf { File(stagingDir, "cover_thumb.jpg").exists() }
            val book = Book(
                id = remoteBook.bookId,
                originalFilePath = destinationOriginalFile.absolutePath,
                preparedFilePath = destinationPreparedFile?.absolutePath,
                coverImagePath = destinationCoverFile?.absolutePath,
                coverThumbPath = destinationCoverThumbFile?.absolutePath,
                originalHashSha256 = remoteBook.originalHashSha256,
                fileSizeBytes = stagedOriginalFile.length(),
                importedAt = currentBook?.book?.importedAt ?: syncedAt,
                updatedAt = remoteBook.updatedAt,
                lastPreparedAt = currentBook?.book?.lastPreparedAt
                    ?: destinationPreparedFile?.let { remoteBook.updatedAt },
                lastExportedAt = currentBook?.book?.lastExportedAt,
                lastSyncedAt = syncedAt,
                status = if (remoteBook.autoMerged) BookStatus.OUTDATED_PREPARED else downloadedState.status,
                syncStatus = SyncStatus.SYNCED,
                revision = remoteBook.revision,
                updatedByDeviceId = remoteBook.updatedByDeviceId,
                technicalStatus = downloadedState.technicalInfo.errors
                    .takeIf { it.isNotEmpty() }
                    ?.joinToString("; "),
            )
            val authoritativeMetadata = remoteBook.metadataSnapshot?.toDomain() ?: downloadedState.metadata
            val metadata = authoritativeMetadata.copy(bookId = remoteBook.bookId,
                title = if (remoteBook.conflictOriginId != null) authoritativeMetadata.title + " (recovered conflict)" else authoritativeMetadata.title)
            val technicalInfo = downloadedState.technicalInfo.copy(bookId = remoteBook.bookId)

            if (!hasStateBundle || remoteBook.autoMerged || remoteBook.conflictOriginId != null) {
                writeRecoveredPortableSnapshots(
                    bookDir = stagingDir,
                    book = book,
                    metadata = metadata,
                    technicalInfo = technicalInfo,
                )
            }
            commitDownloadedBook(
                stagingDir = stagingDir,
                destinationDir = destinationDir,
                expectedBook = currentBook,
                record = ImportedBookRecord(
                    book = book,
                    metadata = metadata,
                    technicalInfo = technicalInfo,
                ),
            )
            Log.d(
                TAG,
                "downloadBook end id=${remoteBook.bookId} recovered=${!hasStateBundle} " +
                    "durationMs=${SystemClock.elapsedRealtime() - startedAtElapsedMillis}",
            )
            RemoteDownloadResult(recoveredStateBundle = !hasStateBundle)
        } finally {
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
            }
        }
    }

    private fun readBundledBookState(
        bookDir: File,
        remoteBook: ManifestBookSnapshot,
    ): DownloadedBookState {
        val metadataText = File(bookDir, "metadata.json")
            .takeIf { file -> file.exists() }
            ?.readText(Charsets.UTF_8)
            ?: error("Missing bundled metadata for ${remoteBook.bookId}")
        val technicalText = File(bookDir, "technical.json")
            .takeIf { file -> file.exists() }
            ?.readText(Charsets.UTF_8)
            ?: error("Missing bundled technical snapshot for ${remoteBook.bookId}")
        val metadataSnapshot = json.decodeFromString<MetadataSnapshot>(metadataText)
        val technicalSnapshot = json.decodeFromString<TechnicalSnapshot>(technicalText)
        return DownloadedBookState(
            metadata = metadataSnapshot.toDomain(),
            technicalInfo = technicalSnapshot.toDomain(),
            status = metadataSnapshot.status.toBookStatus(),
        )
    }

    private fun recoverBookStateFromEpub(
        bookDir: File,
        originalFile: File,
        remoteBook: ManifestBookSnapshot,
    ): DownloadedBookState {
        val parsedWithCover = AndroidZipEpubArchive(originalFile).use { archive ->
            val parsed = epubParser.parse(archive)
            val coverBytes = parsed.coverPath?.let(archive::readBytes)
            val coverPaths = coverBytes?.let { bytes -> coverImageWriter.writeCover(bookDir, bytes) }
            ParsedRemoteEpub(parsed = parsed, hasCover = coverPaths != null)
        }
        val parsedMetadata = parsedWithCover.parsed.metadata
        val parsedTechnical = parsedWithCover.parsed.technicalInfo
        val title = parsedMetadata.title
            ?.takeIf { it.isNotBlank() }
            ?: remoteBook.originalFile
                .substringAfterLast('/')
                .substringBeforeLast(".epub")
                .ifBlank { "EPUB" }
        val authors = parsedMetadata.authors.filter { author -> author.isNotBlank() }
        val status = when {
            parsedTechnical.errors.isNotEmpty() -> BookStatus.ERROR
            !parsedWithCover.hasCover -> BookStatus.NEEDS_REVIEW
            else -> BookStatus.IMPORTED
        }
        return DownloadedBookState(
            metadata = BookMetadata(
                bookId = remoteBook.bookId,
                title = title,
                subtitle = null,
                authors = authors,
                sortAuthor = authors.firstOrNull(),
                language = parsedMetadata.language,
                publisher = parsedMetadata.publisher,
                publishedDate = parsedMetadata.publishedDate,
                description = parsedMetadata.description,
                tags = emptyList(),
                isbn = parsedMetadata.isbn,
                identifiers = parsedMetadata.identifiers.ifEmpty {
                    parsedMetadata.identifier?.let { identifier ->
                        mapOf("package" to identifier)
                    }.orEmpty()
                },
                seriesName = null,
                seriesIndex = null,
                source = MetadataSource.EPUB,
            ),
            technicalInfo = EpubTechnicalInfo(
                bookId = remoteBook.bookId,
                epubVersion = parsedTechnical.epubVersion,
                opfPath = parsedTechnical.opfPath,
                navPath = parsedTechnical.navPath,
                ncxPath = parsedTechnical.ncxPath,
                coverHref = parsedTechnical.coverHref,
                packageIdentifier = parsedTechnical.packageIdentifier,
                hasNav = parsedTechnical.hasNav,
                hasNcx = parsedTechnical.hasNcx,
                manifestItemCount = parsedTechnical.manifestItemCount,
                spineItemCount = parsedTechnical.spineItemCount,
                warnings = parsedTechnical.warnings,
                errors = parsedTechnical.errors,
            ),
            status = status,
        )
    }

    private fun writeRecoveredPortableSnapshots(
        bookDir: File,
        book: Book,
        metadata: BookMetadata,
        technicalInfo: EpubTechnicalInfo,
    ) {
        File(bookDir, "metadata.json").writeText(
            json.encodeToString(PortableSnapshots.metadata(book, metadata)),
            Charsets.UTF_8,
        )
        File(bookDir, "technical.json").writeText(
            json.encodeToString(PortableSnapshots.technical(technicalInfo)),
            Charsets.UTF_8,
        )
        File(bookDir, "changes.jsonl").writeText(
            """{"type":"recovered-from-remote-epub","at":"${book.lastSyncedAt}","bookId":"${book.id}"}""" +
                "\n",
            Charsets.UTF_8,
        )
    }

    private fun commitDownloadedBook(
        stagingDir: File,
        destinationDir: File,
        record: ImportedBookRecord,
        expectedBook: LibraryBook?,
    ) {
        synchronized(repositoryLock) {
            val current = libraryRepository.getBook(expectedBook?.book?.id ?: record.book.id)
            check(current?.book?.revision == expectedBook?.book?.revision &&
                current?.book?.updatedAt == expectedBook?.book?.updatedAt) { "Book changed during download; retry sync" }
            check(!destinationDir.exists()) { "Download destination already exists" }
            check(destinationDir.parentFile!!.let { it.isDirectory || it.mkdirs() })
            // Publish new files first. The database keeps pointing at the old, intact
            // version if the process dies before the transaction commits.
            if (!stagingDir.renameTo(destinationDir)) {
                check(stagingDir.copyRecursively(destinationDir, overwrite = false)) {
                    "Could not store downloaded book ${record.book.id}"
                }
            }
            libraryRepository.insertImportedBook(record)
        }
    }

    private fun extractStateBundle(
        bundleFile: File,
        destinationDir: File,
    ) {
        ZipInputStream(bundleFile.inputStream().buffered()).use { input ->
            val budget = ReadBudget(StorageLimits.EXPANDED_BYTES)
            val seen = HashSet<String>()
            while (true) {
                val entry = input.nextEntry ?: break
                val fileName = entry.name
                require(fileName in STATE_BUNDLE_ENTRY_NAMES && seen.add(fileName) && !entry.isDirectory) { "Invalid state bundle entry" }
                if (!entry.isDirectory && fileName in STATE_BUNDLE_ENTRY_NAMES) {
                    val destination = destinationDir.resolve(fileName)
                    destination.outputStream().buffered().use { output ->
                        input.copyBounded(output, StorageLimits.ENTRY_BYTES, budget)
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
        val epubSource = File(libraryBook.book.originalFilePath)

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
                val verification = File(localDir, "verify-${UUID.randomUUID()}")
                try {
                    check(store.copyToLocal(syncFile.remotePath, verification) != null &&
                        verification.length() == syncFile.source.length() && verification.sha256() == syncFile.source.sha256()) {
                        "Remote upload verification failed"
                    }
                } finally { verification.delete() }
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
        onFinished: (RemoteDownloadAction, RemoteDownloadResult?, Throwable?) -> Unit,
    ) {
        if (actions.isEmpty()) return

        val parallelism = store.maxConcurrentDownloads.coerceAtLeast(1).coerceAtMost(actions.size)
        if (parallelism == 1) {
            actions.forEach { action ->
                val attempt = runCatching {
                    retryRemoteBookOperation("download", action.remoteBook.bookId) {
                        downloadRemoteBook(
                            store = store,
                            remoteBook = action.remoteBook,
                            syncedAt = syncedAt,
                            currentBook = action.currentBook,
                        )
                    }
                }
                onFinished(action, attempt.getOrNull(), attempt.exceptionOrNull())
            }
            return
        }

        val executor = Executors.newFixedThreadPool(parallelism)
        val completionService = ExecutorCompletionService<RemoteDownloadOutcome>(executor)
        try {
            actions.forEach { action ->
                completionService.submit(
                    Callable {
                        val attempt = runCatching {
                            retryRemoteBookOperation("download", action.remoteBook.bookId) {
                                downloadRemoteBook(
                                    store = store,
                                    remoteBook = action.remoteBook,
                                    syncedAt = syncedAt,
                                    currentBook = action.currentBook,
                                )
                            }
                        }
                        RemoteDownloadOutcome(
                            action = action,
                            result = attempt.getOrNull(),
                            error = attempt.exceptionOrNull(),
                        )
                    },
                )
            }

            repeat(actions.size) {
                val outcome = completionService.take().get()
                onFinished(outcome.action, outcome.result, outcome.error)
            }
        } finally {
            executor.shutdownNow()
            while (!executor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                // Do not release the transaction or remove staging files while a worker still uses them.
            }
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
                retryRemoteBookOperation("upload", book.book.id) {
                    uploadBook(
                        store = store,
                        libraryBook = book,
                        layout = remoteLayoutsByBookId.getValue(book.book.id),
                        previousRemoteBook = remoteBooks[book.book.id],
                    )
                }
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
                        retryRemoteBookOperation("upload", book.book.id) {
                            uploadBook(
                                store = store,
                                libraryBook = book,
                                layout = remoteLayoutsByBookId.getValue(book.book.id),
                                previousRemoteBook = remoteBooks[book.book.id],
                            )
                        }
                        book.book.id
                    },
                )
            }

            repeat(books.size) {
                onUploaded(completionService.take().get())
            }
        } finally {
            executor.shutdownNow()
            while (!executor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                // Do not release the transaction or remove staging files while a worker still uses them.
            }
        }
    }

    private fun <T> retryRemoteBookOperation(
        operation: String,
        bookId: String,
        block: () -> T,
    ): T {
        var lastError: Throwable? = null
        repeat(REMOTE_OPERATION_MAX_ATTEMPTS) { attempt ->
            try {
                return block()
            } catch (error: Throwable) {
                lastError = error
                val shouldRetry = error.isRetryableRemoteFailure() &&
                    attempt < REMOTE_OPERATION_MAX_ATTEMPTS - 1
                if (!shouldRetry) throw error
                val delayMillis = REMOTE_RETRY_DELAYS_MS[attempt]
                Log.w(
                    TAG,
                    "$operation retry id=$bookId attempt=${attempt + 2}/$REMOTE_OPERATION_MAX_ATTEMPTS " +
                        "delayMs=$delayMillis type=${error::class.java.simpleName}",
                )
                Thread.sleep(delayMillis)
            }
        }
        throw checkNotNull(lastError)
    }

    private fun Throwable.isRetryableRemoteFailure(): Boolean {
        return generateSequence(this) { error -> error.cause }
            .any { error ->
                error is IOException ||
                    error is GoogleDriveHttpException && error.isRetryable
            }
    }

    private fun Throwable.missingRemoteBookFileOrNull(): MissingRemoteBookFileException? {
        return generateSequence(this) { error -> error.cause }
            .filterIsInstance<MissingRemoteBookFileException>()
            .firstOrNull()
    }

    private fun ManifestBookSnapshot.toLayout() = RemoteBookLayout(
        originalFile.parentRemotePath().remoteName(), originalFile.parentRemotePath(), originalFile.remoteName(),
        metadataFile, originalFile, preparedFile, coverFile, checkNotNull(stateBundleFile),
    )

    private fun LibraryManifestSnapshot.validate() {
        check(schemaVersion == CURRENT_MANIFEST_SCHEMA_VERSION) { "Unsupported library version" }
        check(books.map { it.bookId }.distinct().size == books.size) { "Duplicate book IDs" }
        books.forEach { book ->
            requireSafeBookId(book.bookId)
            (listOfNotNull(book.metadataFile, book.originalFile, book.preparedFile, book.coverFile, book.stateBundleFile) +
                book.files.map { it.path }).forEach { it.normalizedRemotePath() }
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
                localBook.canReuseRemoteManifest(remoteBook) && remoteBook?.originalFile == layout.epubFile -> {
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
        return File(appContext.filesDir, "sync/local-manifest-cache-$cacheScope.json")
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

        return merged.values.map { it.copy(autoMerged = false) }
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
            sha256 != null && sha256 == other.sha256
    }

    private fun ManifestBookSnapshot.fileSnapshot(path: String): ManifestFileSnapshot? {
        return files.firstOrNull { file -> file.path == path }
    }

    private fun File.applyManifestModifiedTime(
        remoteBook: ManifestBookSnapshot,
        remotePath: String,
    ): File {
        remoteBook.fileSnapshot(remotePath)?.let { expected ->
            check(length() == expected.sizeBytes) { "Downloaded file size mismatch" }
            expected.sha256?.let { check(sha256() == it) { "Downloaded file checksum mismatch" } }
        }
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
        val epubFileName = "original.epub"
        val folderPath = "${RemoteManifestJournal.DATA_PREFIX}$folderName"
        val epubFile = "$folderPath/$epubFileName"
        return RemoteBookLayout(
            folderName = folderName,
            folderPath = folderPath,
            epubFileName = epubFileName,
            metadataFile = "$folderPath/metadata.json",
            epubFile = epubFile,
            preparedFile = prepared?.let { "$folderPath/prepared.epub" },
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
            metadataSnapshot = PortableSnapshots.metadata(book, metadata),
            coverHash = book.coverImagePath?.let(::File)?.takeIf { it.isFile }?.sha256(),
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
        epubSource: File = File(book.originalFilePath),
    ): List<LocalSyncFile> {
        val epubFile = LocalSyncFile(epubSource, "${layout.folderPath}/${layout.epubFileName}", "application/epub+zip")
        val stateBundle = buildStateBundle(localDir, book.id)
        return listOfNotNull(
            epubFile,
            layout.preparedFile?.let { LocalSyncFile(File(checkNotNull(book.preparedFilePath)), it, "application/epub+zip") },
            LocalSyncFile(stateBundle, layout.stateBundleFile, STATE_BUNDLE_MIME_TYPE),
        ).onEach { check(it.source.isFile) { "Missing required local file" } }
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

        val budget = ReadBudget(StorageLimits.EXPANDED_BYTES)
        val bundleFile = File(safeChildDirectory(File(appContext.cacheDir, "sync-state"), bookId), STATE_BUNDLE_FILE_NAME)
        bundleFile.parentFile?.mkdirs()
        ZipOutputStream(bundleFile.outputStream().buffered()).use { output ->
            sources.forEach { source ->
                val entry = ZipEntry(source.entryName).apply {
                    time = source.file.lastModified().takeIf { it > 0L } ?: 0L
                }
                output.putNextEntry(entry)
                source.file.inputStream().buffered().use { input ->
                    input.copyBounded(output, StorageLimits.ENTRY_BYTES, budget)
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
        val aiCoverPrompts: List<AiCoverPromptRecord> = emptyList(),
        val deviceName: String? = null,
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
        val conflictOriginId: String? = null,
        val metadataSnapshot: MetadataSnapshot? = null,
        val coverHash: String? = null,
        val autoMerged: Boolean = false,
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
        val sha256: String? = null,
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

    private data class RemoteDownloadOutcome(
        val action: RemoteDownloadAction,
        val result: RemoteDownloadResult?,
        val error: Throwable?,
    )

    private data class RemoteDownloadResult(
        val recoveredStateBundle: Boolean,
    )

    private class MissingRemoteBookFileException(
        val bookId: String,
        val remotePath: String,
    ) : IllegalStateException("Missing remote file $remotePath for $bookId")

    private data class DownloadedBookState(
        val metadata: BookMetadata,
        val technicalInfo: EpubTechnicalInfo,
        val status: BookStatus,
    )

    private data class ParsedRemoteEpub(
        val parsed: EpubParseResult,
        val hasCover: Boolean,
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
                sha256 = source.sha256(),
            )
        }

        fun hasSameSignatureAs(remoteFile: ManifestFileSnapshot?): Boolean {
            val localFile = toManifestFileSnapshot()
            return remoteFile != null &&
                localFile.path == remoteFile.path &&
                localFile.mimeType == remoteFile.mimeType &&
                localFile.sizeBytes == remoteFile.sizeBytes &&
                localFile.sha256 == remoteFile.sha256
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
        const val CURRENT_MANIFEST_SCHEMA_VERSION = 4
        const val CURRENT_LOCAL_MANIFEST_CACHE_SCHEMA_VERSION = 1
        const val REMOTE_OPERATION_MAX_ATTEMPTS = 5
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
        val REMOTE_RETRY_DELAYS_MS = longArrayOf(1_000L, 3_000L, 7_000L, 15_000L)
        const val TAG = "FolioreSync"
    }
}

private fun List<AiCoverPromptRecord>.normalizedAiCoverPromptRecords(): List<AiCoverPromptRecord> {
    return groupBy { it.id }
        .values
        .map { records -> records.maxBy { it.selectedAtEpochMillis } }
        .sortedWith(compareBy<AiCoverPromptRecord> { it.selectedAtEpochMillis }.thenBy { it.id })
}
