package com.foliore.android.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
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
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ManualSyncService(
    context: Context,
    private val libraryRepository: LibraryRepository,
    private val deviceIdProvider: DeviceIdProvider,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(SyncPreferenceKeys.PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val pendingRemoteDeletionStore = PendingRemoteDeletionStore(appContext)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun configureFolder(uri: Uri): ManualSyncResult {
        return runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            preferences.edit()
                .putString(KEY_SYNC_TREE_URI, uri.toString())
                .remove(KEY_LIBRARY_ROOT_URI)
                .remove(KEY_LIBRARY_ROOT_INITIALIZED)
                .apply()
            ManualSyncResult.Configured(folderLabel(uri) ?: DEFAULT_FOLDER_LABEL)
        }.getOrDefault(ManualSyncResult.Failed)
    }

    fun disconnect() {
        val uri = syncUri()
        if (uri != null) {
            runCatching {
                appContext.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        preferences.edit()
            .remove(KEY_SYNC_TREE_URI)
            .remove(KEY_LIBRARY_ROOT_URI)
            .remove(KEY_LIBRARY_ROOT_INITIALIZED)
            .apply()
    }

    fun folderLabel(): String? {
        val uri = syncUri() ?: return null
        return folderLabel(uri)
    }

    fun syncNow(onProgress: (ManualSyncProgress) -> Unit = {}): ManualSyncResult {
        return runCatching {
            onProgress(ManualSyncProgress(ManualSyncPhase.PREPARING))
            val treeUri = syncUri() ?: return ManualSyncResult.NotConfigured
            val root = DocumentFile.fromTreeUri(appContext, treeUri)
                ?.takeIf { it.exists() && it.canWrite() }
                ?: return ManualSyncResult.FolderUnavailable

            val documentCache = DocumentFileCache()
            val libraryRoot = root.resolveLibraryRoot(documentCache)
            val syncedAt = Clock.System.now().toString()
            val deviceId = deviceIdProvider.getDeviceId()
            val remoteManifest = libraryRoot.readManifest(documentCache)
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

            onProgress(
                ManualSyncProgress(
                    phase = ManualSyncPhase.CHECKING_REMOTE,
                    current = 0,
                    total = remoteBookList.size,
                ),
            )
            remoteBookList.forEachIndexed { index, remoteBook ->
                val localBook = localBooksById[remoteBook.bookId]
                // Before remote tombstones existed, a local delete could leave this device's
                // own upload behind. Keep remote-only books from other devices downloadable.
                val shouldDeleteRemoteBook = remoteBook.bookId in pendingDeletedBookIds ||
                    (localBook == null && remoteBook.updatedByDeviceId == deviceId)

                if (shouldDeleteRemoteBook) {
                    remoteBook.deleteRemoteFiles(libraryRoot, documentCache)
                    removedRemoteBookIds += remoteBook.bookId
                } else if (remoteBook.hasBookDirectory(libraryRoot, documentCache)) {
                    applyRemoteBook(
                        libraryRoot = libraryRoot,
                        documentCache = documentCache,
                        remoteBook = remoteBook,
                        local = localBook,
                        syncedAt = syncedAt,
                        currentDeviceId = deviceId,
                        counters = counters,
                        pendingStatusUpdates = pendingStatusUpdates,
                    )
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
            val shouldRewriteRemoteMetadata = remoteManifest == null ||
                missingRemoteBookIds.isNotEmpty() ||
                removedRemoteBookIds.isNotEmpty() ||
                !libraryRoot.hasDirectory("devices", documentCache) ||
                !libraryRoot.hasDirectory("snapshots", documentCache)

            val booksAfterRemote = libraryRepository.getBooks()
            val remoteLayoutsByBookId = booksAfterRemote.remoteLayoutsByBookId()
            val expectedRemoteBooksById = booksAfterRemote.associate { book ->
                val layout = remoteLayoutsByBookId.getValue(book.book.id)
                book.book.id to book.toManifestBook(layout)
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

            val booksRoot = if (booksAfterRemote.isNotEmpty() || remoteBooks.isNotEmpty()) {
                libraryRoot.ensureDirectory("books", documentCache)
            } else {
                null
            }
            booksAfterRemote.forEach { book ->
                if (book.book.syncStatus == SyncStatus.CONFLICT ||
                    book.book.id in pendingStatusUpdates.conflictedBookIds
                ) {
                    return@forEach
                }

                val shouldUpload = book.book.id in uploadCandidateIds

                if (shouldUpload) {
                    onProgress(
                        ManualSyncProgress(
                            phase = ManualSyncPhase.UPLOADING,
                            current = uploadProgress + 1,
                            total = uploadCandidateIds.size,
                        ),
                    )
                    uploadBook(
                        libraryRoot = libraryRoot,
                        booksRoot ?: error("Missing books folder for upload"),
                        documentCache,
                        book,
                        remoteLayoutsByBookId.getValue(book.book.id),
                        remoteBooks[book.book.id],
                    )
                    uploadProgress += 1
                    counters.uploadedBooks += 1
                    pendingStatusUpdates.syncedBookIds += book.book.id
                } else if (book.book.syncStatus != SyncStatus.SYNCED) {
                    pendingStatusUpdates.syncedBookIds += book.book.id
                }
            }
            val cleanedRemoteBooks = booksRoot?.deleteUnexpectedBookDirectories(
                expectedFolderNames = remoteLayoutsByBookId.values.mapTo(mutableSetOf()) { it.folderName },
                documentCache = documentCache,
            ) == true

            if (counters.uploadedBooks > 0 || shouldRewriteRemoteMetadata || cleanedRemoteBooks) {
                onProgress(ManualSyncProgress(ManualSyncPhase.WRITING_MANIFEST))
                val devicesRoot = libraryRoot.ensureDirectory("devices", documentCache)
                val snapshotsRoot = libraryRoot.ensureDirectory("snapshots", documentCache)
                devicesRoot.writeTextFile(
                    name = "$deviceId.json",
                    text = json.encodeToString(
                        DeviceSnapshot(
                            deviceId = deviceId,
                            deviceName = android.os.Build.MODEL ?: "Android",
                            platform = "android",
                            updatedAt = syncedAt,
                        ),
                    ),
                    documentCache = documentCache,
                )
                libraryRoot.writeTextFile(
                    name = "library.manifest.json",
                    text = json.encodeToString(
                        LibraryManifestSnapshot(
                            schemaVersion = 1,
                            libraryId = libraryId(remoteManifest),
                            updatedAt = syncedAt,
                            updatedByDeviceId = deviceId,
                            books = mergedManifestBooks(
                                remoteBooks = remoteBooks - removedRemoteBookIds,
                                localBooks = booksAfterRemote,
                                remoteLayoutsByBookId = remoteLayoutsByBookId,
                                conflictedBookIds = pendingStatusUpdates.conflictedBookIds,
                            ),
                        ),
                    ),
                    documentCache = documentCache,
                )
                snapshotsRoot.writeTextFile(
                    name = "last-sync.txt",
                    text = syncedAt,
                    mimeType = "text/plain",
                    documentCache = documentCache,
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

            ManualSyncResult.Synced(
                uploadedBooks = counters.uploadedBooks,
                downloadedBooks = counters.downloadedBooks,
                conflictedBooks = counters.conflictedBooks,
                folderLabel = folderLabel(treeUri) ?: DEFAULT_FOLDER_LABEL,
            )
        }.getOrElse { error ->
            Log.e(TAG, "Manual sync failed", error)
            ManualSyncResult.Failed
        }
    }

    private fun LibraryBook.shouldUploadTo(
        remoteBook: ManifestBookSnapshot?,
        forceUpload: Boolean,
        expectedRemoteBook: ManifestBookSnapshot?,
    ): Boolean {
        return forceUpload ||
            remoteBook == null ||
            expectedRemoteBook?.let { expected -> remoteBook.hasDifferentPathsThan(expected) } == true ||
            book.revision > remoteBook.revision ||
            book.syncStatus == SyncStatus.LOCAL_ONLY ||
            book.syncStatus == SyncStatus.PENDING_UPLOAD
    }

    private fun applyRemoteBook(
        libraryRoot: DocumentFile,
        documentCache: DocumentFileCache,
        remoteBook: ManifestBookSnapshot,
        local: LibraryBook?,
        syncedAt: String,
        currentDeviceId: String,
        counters: SyncCounters,
        pendingStatusUpdates: PendingSyncStatusUpdates,
    ) {
        if (local == null) {
            val duplicateBookId = libraryRepository.findBookIdByHash(remoteBook.originalHashSha256)
            if (duplicateBookId != null) {
                pendingStatusUpdates.syncedBookIds += duplicateBookId
                return
            }

            downloadRemoteBook(
                libraryRoot = libraryRoot,
                documentCache = documentCache,
                remoteBook = remoteBook,
                syncedAt = syncedAt,
                currentBook = null,
            )
            counters.downloadedBooks += 1
            return
        }

        when {
            local.book.syncStatus == SyncStatus.CONFLICT -> {
                counters.conflictedBooks += 1
            }
            remoteBook.revision > local.book.revision -> {
                if (local.book.syncStatus == SyncStatus.PENDING_UPLOAD &&
                    remoteBook.updatedByDeviceId != currentDeviceId
                ) {
                    pendingStatusUpdates.conflictedBookIds += local.book.id
                    counters.conflictedBooks += 1
                } else {
                    downloadRemoteBook(
                        libraryRoot = libraryRoot,
                        documentCache = documentCache,
                        remoteBook = remoteBook,
                        syncedAt = syncedAt,
                        currentBook = local,
                    )
                    counters.downloadedBooks += 1
                }
            }
            remoteBook.revision == local.book.revision -> {
                if (remoteBook.updatedAt != local.book.updatedAt &&
                    remoteBook.updatedByDeviceId != local.book.updatedByDeviceId
                ) {
                    pendingStatusUpdates.conflictedBookIds += local.book.id
                    counters.conflictedBooks += 1
                } else if (local.book.syncStatus != SyncStatus.SYNCED) {
                    pendingStatusUpdates.syncedBookIds += local.book.id
                }
            }
        }
    }

    private fun downloadRemoteBook(
        libraryRoot: DocumentFile,
        documentCache: DocumentFileCache,
        remoteBook: ManifestBookSnapshot,
        syncedAt: String,
        currentBook: LibraryBook?,
    ) {
        val remoteMetadataFile = libraryRoot.findRelativeFile(remoteBook.metadataFile, documentCache)
            ?: error("Missing remote metadata for ${remoteBook.bookId}")
        val remoteOriginalFile = libraryRoot.findRelativeFile(remoteBook.originalFile, documentCache)
            ?: error("Missing remote original EPUB for ${remoteBook.bookId}")
        val remoteTechnicalFile = libraryRoot.findRelativeFile(
            remoteBook.bookFileName("technical.json"),
            documentCache,
        )
            ?: error("Missing remote technical snapshot for ${remoteBook.bookId}")
        val metadataSnapshot = json.decodeFromString<MetadataSnapshot>(remoteMetadataFile.readText())
        val technicalSnapshot = json.decodeFromString<TechnicalSnapshot>(remoteTechnicalFile.readText())
        val bookDir = File(appContext.filesDir, "books/${remoteBook.bookId}")

        if (bookDir.exists()) {
            bookDir.deleteRecursively()
        }
        bookDir.mkdirs()

        val localOriginalFile = File(bookDir, "original.epub")
        remoteOriginalFile.copyToLocal(localOriginalFile)
        val localPreparedFile = remoteBook.preparedFile?.let { path ->
            libraryRoot.copyRelativeFileIfExists(path, File(bookDir, "prepared.epub"), documentCache)
        }
        val localCoverFile = remoteBook.coverFile?.let { path ->
            libraryRoot.copyRelativeFileIfExists(path, File(bookDir, "cover_original.jpg"), documentCache)
        }
        val localCoverThumbFile = libraryRoot.copyRelativeFileIfExists(
            remoteBook.bookFileName("cover_thumb.jpg"),
            File(bookDir, "cover_thumb.jpg"),
            documentCache,
        )

        remoteMetadataFile.copyToLocal(File(bookDir, "metadata.json"))
        remoteTechnicalFile.copyToLocal(File(bookDir, "technical.json"))
        libraryRoot.copyRelativeFileIfExists(
            remoteBook.bookFileName("changes.jsonl"),
            File(bookDir, "changes.jsonl"),
            documentCache,
        )

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

        libraryRepository.insertImportedBook(
            ImportedBookRecord(
                book = book,
                metadata = metadata,
                technicalInfo = technicalInfo,
            ),
        )
    }

    private fun uploadBook(
        libraryRoot: DocumentFile,
        booksRoot: DocumentFile,
        documentCache: DocumentFileCache,
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
        libraryRoot.deleteObsoleteBookFolders(
            bookId = libraryBook.book.id,
            activeFolderPath = layout.folderPath,
            previousRemoteBook = previousRemoteBook,
            documentCache = documentCache,
        )
        val bookDir = booksRoot.ensureCleanDirectory(layout.folderName, documentCache)

        bookDir.replaceFiles(
            files = listOf(
                LocalSyncFile(epubSource, layout.epubFileName, "application/epub+zip"),
                LocalSyncFile(localDir.resolve("cover_original.jpg"), "cover_original.jpg", "image/jpeg"),
                LocalSyncFile(localDir.resolve("cover_thumb.jpg"), "cover_thumb.jpg", "image/jpeg"),
                LocalSyncFile(localDir.resolve("metadata.json"), "metadata.json", "application/json"),
                LocalSyncFile(localDir.resolve("technical.json"), "technical.json", "application/json"),
                LocalSyncFile(localDir.resolve("changes.jsonl"), "changes.jsonl", "application/json"),
            ),
            documentCache = documentCache,
        )
    }

    private fun DocumentFile.writeTextFile(
        name: String,
        text: String,
        mimeType: String = "application/json",
        documentCache: DocumentFileCache,
    ) {
        val destination = replaceFile(name, mimeType, documentCache)
        val outputStream = appContext.contentResolver.openOutputStream(destination.uri, "wt")
            ?: error("Cannot open $name for writing")
        outputStream.use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
        }
    }

    private fun DocumentFile.replaceFiles(
        files: List<LocalSyncFile>,
        documentCache: DocumentFileCache,
    ) {
        val existingFiles = files.filter { it.source.exists() }
        if (existingFiles.isEmpty()) return

        val names = existingFiles.mapTo(mutableSetOf()) { it.name }
        documentCache.childrenOf(this)
            .filter { file -> file.name in names && !file.isDirectory }
            .forEach { existing ->
                if (!existing.delete()) error("Cannot replace ${existing.name}")
            }
        documentCache.invalidate(this)

        existingFiles.forEach { syncFile ->
            val destination = createFile(syncFile.mimeType, syncFile.name)
                ?: error("Cannot create ${syncFile.name}")
            val outputStream = appContext.contentResolver.openOutputStream(destination.uri, "wt")
                ?: error("Cannot open ${syncFile.name} for writing")
            outputStream.use { output ->
                syncFile.source.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
        }
        documentCache.invalidate(this)
    }

    private fun DocumentFile.ensureCleanDirectory(
        name: String,
        documentCache: DocumentFileCache,
    ): DocumentFile {
        val directory = ensureDirectory(name, documentCache)
        directory.deleteChildren(documentCache)
        return directory
    }

    private fun DocumentFile.deleteChildren(documentCache: DocumentFileCache) {
        documentCache.childrenOf(this).forEach { child ->
            child.deleteTree(documentCache)
        }
        documentCache.invalidate(this)
    }

    private fun DocumentFile.deleteUnexpectedBookDirectories(
        expectedFolderNames: Set<String>,
        documentCache: DocumentFileCache,
    ): Boolean {
        var changed = false
        documentCache.childrenOf(this)
            .filter { child -> child.name !in expectedFolderNames || child.isFile }
            .forEach { obsolete ->
                obsolete.deleteTree(documentCache)
                changed = true
            }
        if (changed) documentCache.invalidate(this)
        return changed
    }

    private fun DocumentFile.resolveLibraryRoot(documentCache: DocumentFileCache): DocumentFile {
        val nestedLibraryRoots = documentCache.directoriesNamed(this, LIBRARY_ROOT_NAME)
        val selfIsLibraryRoot = name == LIBRARY_ROOT_NAME || libraryRootStructureScore(documentCache) > 0

        if (selfIsLibraryRoot) {
            if (nestedLibraryRoots.isNotEmpty()) {
                nestedLibraryRoots.forEach { duplicate -> duplicate.deleteTree(documentCache) }
                documentCache.invalidate(this)
            }
            markLibraryRootInitialized(this)
            return this
        }

        if (nestedLibraryRoots.isNotEmpty()) {
            val canonical = nestedLibraryRoots.bestLibraryRoot(documentCache)
                ?: error("Cannot resolve $LIBRARY_ROOT_NAME")
            nestedLibraryRoots
                .filterNot { duplicate -> duplicate.uri == canonical.uri }
                .forEach { duplicate -> duplicate.deleteTree(documentCache) }
            documentCache.invalidate(this)
            markLibraryRootInitialized(canonical)
            return canonical
        }

        val created = createDirectory(LIBRARY_ROOT_NAME) ?: error("Cannot create $LIBRARY_ROOT_NAME")
        documentCache.invalidate(this)
        markLibraryRootInitialized(created)
        return created
    }

    private fun DocumentFile.libraryRootStructureScore(documentCache: DocumentFileCache): Int {
        var score = 0
        if (documentCache.filesNamed(this, "library.manifest.json").isNotEmpty()) score += 1_000
        if (documentCache.directoriesNamed(this, "books").isNotEmpty()) score += 200
        if (documentCache.directoriesNamed(this, "devices").isNotEmpty()) score += 50
        if (documentCache.directoriesNamed(this, "snapshots").isNotEmpty()) score += 50
        return score
    }

    private fun DocumentFile.libraryRootScore(documentCache: DocumentFileCache): Int {
        return libraryRootStructureScore(documentCache) + documentCache.childrenOf(this).size.coerceAtMost(40)
    }

    private fun List<DocumentFile>.bestLibraryRoot(documentCache: DocumentFileCache): DocumentFile? {
        val storedRootUri = preferences.getString(KEY_LIBRARY_ROOT_URI, null)
        return maxWithOrNull(
            compareBy<DocumentFile> { candidate -> candidate.libraryRootScore(documentCache) }
                .thenBy { candidate -> if (candidate.uri.toString() == storedRootUri) 1 else 0 }
                .thenBy { candidate -> candidate.lastModified() }
                .thenBy { candidate -> candidate.uri.toString() },
        )
    }

    private fun markLibraryRootInitialized(libraryRoot: DocumentFile) {
        preferences.edit()
            .putBoolean(KEY_LIBRARY_ROOT_INITIALIZED, true)
            .putString(KEY_LIBRARY_ROOT_URI, libraryRoot.uri.toString())
            .apply()
    }

    private fun DocumentFile.ensureDirectory(
        name: String,
        documentCache: DocumentFileCache,
    ): DocumentFile {
        findUniqueDirectory(name, documentCache)?.let { existing -> return existing }
        val created = createDirectory(name) ?: error("Cannot create $name")
        documentCache.invalidate(this)
        return created
    }

    private fun DocumentFile.hasDirectory(
        name: String,
        documentCache: DocumentFileCache,
    ): Boolean {
        return findUniqueDirectory(name, documentCache) != null
    }

    private fun DocumentFile.replaceFile(
        name: String,
        mimeType: String,
        documentCache: DocumentFileCache,
    ): DocumentFile {
        documentCache.childrenOf(this)
            .filter { it.name == name && !it.isDirectory }
            .forEach { existing ->
                if (!existing.delete()) error("Cannot replace $name")
            }
        documentCache.invalidate(this)
        val created = createFile(mimeType, name) ?: error("Cannot create $name")
        documentCache.invalidate(this)
        return created
    }

    private fun DocumentFile.readManifest(documentCache: DocumentFileCache): LibraryManifestSnapshot? {
        return findUniqueFile("library.manifest.json", documentCache)?.let { manifestFile ->
            json.decodeFromString<LibraryManifestSnapshot>(manifestFile.readText())
        }
    }

    private fun DocumentFile.findRelativeFile(
        path: String,
        documentCache: DocumentFileCache,
    ): DocumentFile? {
        val segments = path.split('/')
            .filter { it.isNotBlank() }
        return segments.foldIndexed(this as DocumentFile?) { index, current, segment ->
            if (current == null) {
                null
            } else if (index == segments.lastIndex) {
                current.findUniqueFile(segment, documentCache)
            } else {
                current.findUniqueDirectory(segment, documentCache)
            }
        }
    }

    private fun DocumentFile.findRelativeDirectory(
        path: String,
        documentCache: DocumentFileCache,
    ): DocumentFile? {
        val segments = path.split('/')
            .filter { it.isNotBlank() }
        return segments.fold(this as DocumentFile?) { current, segment ->
            current?.findUniqueDirectory(segment, documentCache)
        }
    }

    private fun DocumentFile.deleteObsoleteBookFolders(
        bookId: String,
        activeFolderPath: String,
        previousRemoteBook: ManifestBookSnapshot?,
        documentCache: DocumentFileCache,
    ) {
        listOfNotNull(
            previousRemoteBook?.bookFolderPath(),
            "books/$bookId",
        )
            .distinct()
            .filterNot { path -> path == activeFolderPath }
            .forEach { path ->
                findRelativeDirectory(path, documentCache)?.deleteTree(documentCache)
            }
    }

    private fun DocumentFile.copyRelativeFileIfExists(
        path: String,
        destination: File,
        documentCache: DocumentFileCache,
    ): File? {
        val source = findRelativeFile(path, documentCache) ?: return null
        source.copyToLocal(destination)
        return destination.takeIf { it.exists() }
    }

    private fun DocumentFile.copyToLocal(destination: File) {
        destination.parentFile?.mkdirs()
        val inputStream = appContext.contentResolver.openInputStream(uri)
            ?: error("Cannot open ${name ?: uri} for reading")
        inputStream.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun DocumentFile.readText(): String {
        val inputStream = appContext.contentResolver.openInputStream(uri)
            ?: error("Cannot open ${name ?: uri} for reading")
        return inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun syncUri(): Uri? {
        return preferences.getString(KEY_SYNC_TREE_URI, null)?.let(Uri::parse)
    }

    private fun folderLabel(uri: Uri): String? {
        return DocumentFile.fromTreeUri(appContext, uri)?.name
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
    ): List<ManifestBookSnapshot> {
        val merged = linkedMapOf<String, ManifestBookSnapshot>()
        localBooks
            .filter { it.book.syncStatus == SyncStatus.CONFLICT || it.book.id in conflictedBookIds }
            .mapNotNull { remoteBooks[it.book.id] }
            .forEach { remoteBook -> merged[remoteBook.bookId] = remoteBook }

        localBooks
            .filterNot { it.book.syncStatus == SyncStatus.CONFLICT || it.book.id in conflictedBookIds }
            .map { localBook ->
                localBook.toManifestBook(remoteLayoutsByBookId.getValue(localBook.book.id))
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

    private fun ManifestBookSnapshot.hasBookDirectory(
        libraryRoot: DocumentFile,
        documentCache: DocumentFileCache,
    ): Boolean {
        return libraryRoot.findRelativeDirectory(bookFolderPath(), documentCache) != null
    }

    private fun ManifestBookSnapshot.deleteRemoteFiles(
        libraryRoot: DocumentFile,
        documentCache: DocumentFileCache,
    ) {
        listOf(bookFolderPath(), "books/$bookId")
            .distinct()
            .forEach { path ->
                libraryRoot.findRelativeDirectory(path, documentCache)?.deleteTree(documentCache)
            }
    }

    private fun ManifestBookSnapshot.hasDifferentPathsThan(expected: ManifestBookSnapshot): Boolean {
        return metadataFile != expected.metadataFile ||
            originalFile != expected.originalFile ||
            preparedFile != expected.preparedFile ||
            coverFile != expected.coverFile
    }

    private fun DocumentFile.findUniqueFile(
        name: String,
        documentCache: DocumentFileCache,
    ): DocumentFile? {
        val matches = documentCache.filesNamed(this, name)
        val canonical = documentCache.bestFile(matches) ?: return null
        if (matches.size > 1) {
            matches
                .filterNot { it.uri == canonical.uri }
                .forEach { duplicate ->
                    if (!duplicate.delete()) error("Cannot delete duplicate file $name")
                }
            documentCache.invalidate(this)
        }
        return canonical
    }

    private fun DocumentFile.findUniqueDirectory(
        name: String,
        documentCache: DocumentFileCache,
    ): DocumentFile? {
        val matches = documentCache.directoriesNamed(this, name)
        val canonical = documentCache.bestDirectory(matches) ?: return null
        if (matches.size > 1) {
            matches
                .filterNot { it.uri == canonical.uri }
                .forEach { duplicate ->
                    duplicate.deleteTree(documentCache)
                }
            documentCache.invalidate(this)
            documentCache.invalidate(canonical)
        }
        return canonical
    }

    private fun DocumentFile.deleteTree(documentCache: DocumentFileCache) {
        if (isDirectory || !isFile) {
            documentCache.childrenOf(this).forEach { child ->
                child.deleteTree(documentCache)
            }
        }
        documentCache.invalidate(this)
        if (!delete()) error("Cannot delete duplicate ${name ?: uri}")
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
        )
    }

    private fun LibraryBook.toManifestBook(layout: RemoteBookLayout): ManifestBookSnapshot {
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
        )
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
    )

    @Serializable
    private data class DeviceSnapshot(
        val deviceId: String,
        val deviceName: String,
        val platform: String,
        val updatedAt: String,
    )

    private data class SyncCounters(
        var uploadedBooks: Int = 0,
        var downloadedBooks: Int = 0,
        var conflictedBooks: Int = 0,
    )

    private data class LocalSyncFile(
        val source: File,
        val name: String,
        val mimeType: String,
    )

    private data class RemoteBookLayout(
        val folderName: String,
        val folderPath: String,
        val epubFileName: String,
        val metadataFile: String,
        val epubFile: String,
        val preparedFile: String?,
        val coverFile: String?,
    )

    private class DocumentFileCache {
        private val childrenByDirectoryUri = mutableMapOf<String, List<DocumentFile>>()

        fun childrenOf(directory: DocumentFile): List<DocumentFile> {
            return childrenByDirectoryUri.getOrPut(directory.uri.toString()) {
                directory.listFiles().toList()
            }
        }

        fun invalidate(directory: DocumentFile) {
            childrenByDirectoryUri.remove(directory.uri.toString())
        }

        fun filesNamed(directory: DocumentFile, name: String): List<DocumentFile> {
            return childrenOf(directory)
                .filter { file -> file.name == name && !file.isDirectory }
        }

        fun directoriesNamed(directory: DocumentFile, name: String): List<DocumentFile> {
            return childrenOf(directory)
                .filter { file -> file.name == name }
                .filter { file -> file.isDirectory || !file.isFile }
        }

        fun bestFile(files: List<DocumentFile>): DocumentFile? {
            return files.bestDocumentFile()
        }

        fun bestDirectory(directories: List<DocumentFile>): DocumentFile? {
            if (directories.size <= 1) return directories.firstOrNull()
            return directories.maxWithOrNull(
                compareBy<DocumentFile> { file -> childrenOf(file).size }
                    .thenBy { file -> file.lastModified() }
                    .thenBy { file -> file.uri.toString() },
            )
        }

        private fun List<DocumentFile>.bestDocumentFile(): DocumentFile? {
            return maxWithOrNull(
                compareBy<DocumentFile> { file -> file.lastModified() }
                    .thenBy { file -> file.uri.toString() },
            )
        }
    }

    private data class PendingSyncStatusUpdates(
        val syncedBookIds: LinkedHashSet<String> = linkedSetOf(),
        val conflictedBookIds: LinkedHashSet<String> = linkedSetOf(),
    ) {
        fun hasUpdates(): Boolean = syncedBookIds.isNotEmpty() || conflictedBookIds.isNotEmpty()
    }

    private companion object {
        const val KEY_SYNC_TREE_URI = "sync_tree_uri"
        const val KEY_LIBRARY_ID = "library_id"
        const val KEY_LIBRARY_ROOT_INITIALIZED = "library_root_initialized"
        const val KEY_LIBRARY_ROOT_URI = "library_root_uri"
        const val KEY_SYNC_UPLOAD_STRATEGY_VERSION = "sync_upload_strategy_version"
        const val CURRENT_SYNC_UPLOAD_STRATEGY_VERSION = 2
        const val LIBRARY_ROOT_NAME = "Foliore Library"
        const val DEFAULT_FOLDER_LABEL = "Foliore Library"
        const val TAG = "ManualSyncService"
    }
}
