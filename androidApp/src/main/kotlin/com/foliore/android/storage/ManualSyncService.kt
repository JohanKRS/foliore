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
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
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
            preferences.edit().putString(KEY_SYNC_TREE_URI, uri.toString()).apply()
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
        preferences.edit().remove(KEY_SYNC_TREE_URI).apply()
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

            val libraryRoot = root.ensureDirectory(LIBRARY_ROOT_NAME)
            val booksRoot = libraryRoot.ensureDirectory("books")
            val devicesRoot = libraryRoot.ensureDirectory("devices")
            val snapshotsRoot = libraryRoot.ensureDirectory("snapshots")
            val syncedAt = Clock.System.now().toString()
            val deviceId = deviceIdProvider.getDeviceId()
            val remoteManifest = libraryRoot.readManifest()
            val remoteBooks = remoteManifest?.books
                ?.filterNot { it.deleted }
                ?.associateBy { it.bookId }
                .orEmpty()
            val counters = SyncCounters()
            val pendingStatusUpdates = PendingSyncStatusUpdates()
            val localBooks = libraryRepository.getBooks()
            val localBooksById = localBooks.associateBy { it.book.id }
            val remoteBookList = remoteBooks.values.toList()

            onProgress(
                ManualSyncProgress(
                    phase = ManualSyncPhase.CHECKING_REMOTE,
                    current = 0,
                    total = remoteBookList.size,
                ),
            )
            remoteBookList.forEachIndexed { index, remoteBook ->
                applyRemoteBook(
                    libraryRoot = libraryRoot,
                    remoteBook = remoteBook,
                    local = localBooksById[remoteBook.bookId],
                    syncedAt = syncedAt,
                    currentDeviceId = deviceId,
                    counters = counters,
                    pendingStatusUpdates = pendingStatusUpdates,
                )
                onProgress(
                    ManualSyncProgress(
                        phase = ManualSyncPhase.CHECKING_REMOTE,
                        current = index + 1,
                        total = remoteBookList.size,
                    ),
                )
            }

            val booksAfterRemote = libraryRepository.getBooks()
            val uploadCandidateIds = booksAfterRemote
                .filterNot { book ->
                    book.book.syncStatus == SyncStatus.CONFLICT ||
                        book.book.id in pendingStatusUpdates.conflictedBookIds
                }
                .filter { book ->
                    book.shouldUploadTo(remoteBooks[book.book.id])
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
                    uploadBook(booksRoot, book)
                    uploadProgress += 1
                    counters.uploadedBooks += 1
                    pendingStatusUpdates.syncedBookIds += book.book.id
                } else if (book.book.syncStatus != SyncStatus.SYNCED) {
                    pendingStatusUpdates.syncedBookIds += book.book.id
                }
            }

            onProgress(ManualSyncProgress(ManualSyncPhase.WRITING_MANIFEST))
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
                            remoteBooks = remoteBooks,
                            localBooks = booksAfterRemote,
                            conflictedBookIds = pendingStatusUpdates.conflictedBookIds,
                        ),
                    ),
                ),
            )
            snapshotsRoot.writeTextFile(
                name = "last-sync.txt",
                text = syncedAt,
                mimeType = "text/plain",
            )

            onProgress(ManualSyncProgress(ManualSyncPhase.APPLYING_STATUS))
            pendingStatusUpdates.conflictedBookIds.forEach { bookId ->
                libraryRepository.markSyncConflict(bookId)
            }
            pendingStatusUpdates.syncedBookIds
                .filterNot { it in pendingStatusUpdates.conflictedBookIds }
                .forEach { bookId ->
                    libraryRepository.markSynced(bookId, syncedAt)
                }

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

    private fun LibraryBook.shouldUploadTo(remoteBook: ManifestBookSnapshot?): Boolean {
        return remoteBook == null ||
            book.revision > remoteBook.revision ||
            book.syncStatus == SyncStatus.LOCAL_ONLY ||
            book.syncStatus == SyncStatus.PENDING_UPLOAD
    }

    private fun applyRemoteBook(
        libraryRoot: DocumentFile,
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
        remoteBook: ManifestBookSnapshot,
        syncedAt: String,
        currentBook: LibraryBook?,
    ) {
        val remoteMetadataFile = libraryRoot.findRelativeFile(remoteBook.metadataFile)
            ?: error("Missing remote metadata for ${remoteBook.bookId}")
        val remoteOriginalFile = libraryRoot.findRelativeFile(remoteBook.originalFile)
            ?: error("Missing remote original EPUB for ${remoteBook.bookId}")
        val remoteTechnicalFile = libraryRoot.findRelativeFile(remoteBook.bookFileName("technical.json"))
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
            libraryRoot.copyRelativeFileIfExists(path, File(bookDir, "prepared.epub"))
        }
        val localCoverFile = remoteBook.coverFile?.let { path ->
            libraryRoot.copyRelativeFileIfExists(path, File(bookDir, "cover_original.jpg"))
        }
        val localCoverThumbFile = libraryRoot.copyRelativeFileIfExists(
            remoteBook.bookFileName("cover_thumb.jpg"),
            File(bookDir, "cover_thumb.jpg"),
        )

        remoteMetadataFile.copyToLocal(File(bookDir, "metadata.json"))
        remoteTechnicalFile.copyToLocal(File(bookDir, "technical.json"))
        libraryRoot.copyRelativeFileIfExists(
            remoteBook.bookFileName("changes.jsonl"),
            File(bookDir, "changes.jsonl"),
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

    private fun uploadBook(booksRoot: DocumentFile, libraryBook: LibraryBook) {
        val bookDir = booksRoot.ensureDirectory(libraryBook.book.id)
        val localDir = File(libraryBook.book.originalFilePath).parentFile
            ?: error("Missing local directory for ${libraryBook.book.id}")

        copyFileIfExists(localDir.resolve("original.epub"), bookDir, "original.epub", "application/epub+zip")
        copyFileIfExists(localDir.resolve("prepared.epub"), bookDir, "prepared.epub", "application/epub+zip")
        copyFileIfExists(localDir.resolve("cover_original.jpg"), bookDir, "cover_original.jpg", "image/jpeg")
        copyFileIfExists(localDir.resolve("cover_thumb.jpg"), bookDir, "cover_thumb.jpg", "image/jpeg")
        copyFileIfExists(localDir.resolve("metadata.json"), bookDir, "metadata.json", "application/json")
        copyFileIfExists(localDir.resolve("technical.json"), bookDir, "technical.json", "application/json")
        copyFileIfExists(localDir.resolve("changes.jsonl"), bookDir, "changes.jsonl", "application/json")
    }

    private fun copyFileIfExists(
        source: File,
        destinationDir: DocumentFile,
        name: String,
        mimeType: String,
    ) {
        if (!source.exists()) return
        val destination = destinationDir.replaceFile(name, mimeType)
        val outputStream = appContext.contentResolver.openOutputStream(destination.uri, "wt")
            ?: error("Cannot open $name for writing")
        outputStream.use { output ->
            source.inputStream().use { input ->
                input.copyTo(output)
            }
        }
    }

    private fun DocumentFile.writeTextFile(
        name: String,
        text: String,
        mimeType: String = "application/json",
    ) {
        val destination = replaceFile(name, mimeType)
        val outputStream = appContext.contentResolver.openOutputStream(destination.uri, "wt")
            ?: error("Cannot open $name for writing")
        outputStream.use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
        }
    }

    private fun DocumentFile.ensureDirectory(name: String): DocumentFile {
        return findFile(name)?.takeIf { it.isDirectory } ?: createDirectory(name) ?: error("Cannot create $name")
    }

    private fun DocumentFile.replaceFile(name: String, mimeType: String): DocumentFile {
        findFile(name)?.let { existing ->
            if (!existing.delete()) error("Cannot replace $name")
        }
        return createFile(mimeType, name) ?: error("Cannot create $name")
    }

    private fun DocumentFile.readManifest(): LibraryManifestSnapshot? {
        return findFile("library.manifest.json")?.let { manifestFile ->
            json.decodeFromString<LibraryManifestSnapshot>(manifestFile.readText())
        }
    }

    private fun DocumentFile.findRelativeFile(path: String): DocumentFile? {
        return path.split('/')
            .filter { it.isNotBlank() }
            .fold(this as DocumentFile?) { current, segment -> current?.findFile(segment) }
    }

    private fun DocumentFile.copyRelativeFileIfExists(
        path: String,
        destination: File,
    ): File? {
        val source = findRelativeFile(path) ?: return null
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
        conflictedBookIds: Set<String>,
    ): List<ManifestBookSnapshot> {
        val merged = linkedMapOf<String, ManifestBookSnapshot>()
        localBooks
            .filter { it.book.syncStatus == SyncStatus.CONFLICT || it.book.id in conflictedBookIds }
            .mapNotNull { remoteBooks[it.book.id] }
            .forEach { remoteBook -> merged[remoteBook.bookId] = remoteBook }

        localBooks
            .filterNot { it.book.syncStatus == SyncStatus.CONFLICT || it.book.id in conflictedBookIds }
            .map { it.toManifestBook() }
            .forEach { localBook -> merged[localBook.bookId] = localBook }

        return merged.values.toList()
    }

    private fun ManifestBookSnapshot.bookFileName(fileName: String): String {
        return "books/$bookId/$fileName"
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

    private fun LibraryBook.toManifestBook(): ManifestBookSnapshot {
        val prepared = book.preparedFilePath?.let { File(it) }?.takeIf { it.exists() }
        val cover = book.coverImagePath?.let { File(it) }?.takeIf { it.exists() }
        return ManifestBookSnapshot(
            bookId = book.id,
            revision = book.revision,
            updatedAt = book.updatedAt,
            updatedByDeviceId = book.updatedByDeviceId,
            originalHashSha256 = book.originalHashSha256,
            metadataFile = "books/${book.id}/metadata.json",
            originalFile = "books/${book.id}/original.epub",
            preparedFile = prepared?.let { "books/${book.id}/prepared.epub" },
            coverFile = cover?.let { "books/${book.id}/cover_original.jpg" },
            deleted = false,
        )
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

    private data class PendingSyncStatusUpdates(
        val syncedBookIds: LinkedHashSet<String> = linkedSetOf(),
        val conflictedBookIds: LinkedHashSet<String> = linkedSetOf(),
    )

    private companion object {
        const val PREFERENCES_NAME = "foliore_sync"
        const val KEY_SYNC_TREE_URI = "sync_tree_uri"
        const val KEY_LIBRARY_ID = "library_id"
        const val LIBRARY_ROOT_NAME = "Foliore Library"
        const val DEFAULT_FOLDER_LABEL = "Foliore Library"
        const val TAG = "ManualSyncService"
    }
}
