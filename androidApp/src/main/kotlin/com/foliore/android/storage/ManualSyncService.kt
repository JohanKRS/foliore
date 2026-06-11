package com.foliore.android.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.foliore.shared.domain.LibraryBook
import com.foliore.shared.library.LibraryRepository
import java.io.File
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ManualSyncService(
    context: Context,
    private val libraryRepository: LibraryRepository,
    private val deviceIdProvider: DeviceIdProvider,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val json = Json { prettyPrint = true }

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

    fun syncNow(): ManualSyncResult {
        return runCatching {
            val treeUri = syncUri() ?: return ManualSyncResult.NotConfigured
            val root = DocumentFile.fromTreeUri(appContext, treeUri)
                ?.takeIf { it.exists() && it.canWrite() }
                ?: return ManualSyncResult.FolderUnavailable

            val libraryRoot = root.ensureDirectory(LIBRARY_ROOT_NAME)
            val booksRoot = libraryRoot.ensureDirectory("books")
            val devicesRoot = libraryRoot.ensureDirectory("devices")
            val snapshotsRoot = libraryRoot.ensureDirectory("snapshots")
            val books = libraryRepository.getBooks()
            val syncedAt = Clock.System.now().toString()
            val deviceId = deviceIdProvider.getDeviceId()

            books.forEach { book ->
                uploadBook(booksRoot, book)
                libraryRepository.markSynced(book.book.id, syncedAt)
            }

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
                        libraryId = libraryId(),
                        updatedAt = syncedAt,
                        updatedByDeviceId = deviceId,
                        books = books.map { it.toManifestBook() },
                    ),
                ),
            )
            snapshotsRoot.writeTextFile(
                name = "last-sync.txt",
                text = syncedAt,
                mimeType = "text/plain",
            )

            ManualSyncResult.Synced(
                uploadedBooks = books.size,
                folderLabel = folderLabel(treeUri) ?: DEFAULT_FOLDER_LABEL,
            )
        }.getOrDefault(ManualSyncResult.Failed)
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

    private fun syncUri(): Uri? {
        return preferences.getString(KEY_SYNC_TREE_URI, null)?.let(Uri::parse)
    }

    private fun folderLabel(uri: Uri): String? {
        return DocumentFile.fromTreeUri(appContext, uri)?.name
    }

    private fun libraryId(): String {
        val existing = preferences.getString(KEY_LIBRARY_ID, null)
        if (existing != null) return existing
        val created = java.util.UUID.randomUUID().toString()
        preferences.edit().putString(KEY_LIBRARY_ID, created).apply()
        return created
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

    private companion object {
        const val PREFERENCES_NAME = "foliore_sync"
        const val KEY_SYNC_TREE_URI = "sync_tree_uri"
        const val KEY_LIBRARY_ID = "library_id"
        const val LIBRARY_ROOT_NAME = "Foliore Library"
        const val DEFAULT_FOLDER_LABEL = "Foliore Library"
    }
}
