package com.foliore.android.storage

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.foliore.shared.library.LibraryRepository
import java.io.File

class AndroidOriginalFilesService(context: Context, private val repository: LibraryRepository) {
    private val context = context.applicationContext
    private val archive = OriginalFileArchive(File(this.context.filesDir, OriginalFileArchive.DIRECTORY))

    fun list(bookId: String): List<OriginalBookFile> = synchronized(SyncOperationCoordinator.localDataLock) {
        ensureArchive(bookId)
        archive.list(bookId)
    }

    fun share(bookId: String, id: String): Intent = synchronized(SyncOperationCoordinator.localDataLock) {
        val entry = list(bookId).first { it.id == id }
        val file = checkNotNull(archive.file(bookId, id))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file, entry.name)
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType(entry)
            clipData = ClipData.newUri(context.contentResolver, entry.name, uri)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, entry.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun save(bookId: String, id: String, destination: Uri) = synchronized(SyncOperationCoordinator.localDataLock) {
        ensureArchive(bookId)
        val file = checkNotNull(archive.file(bookId, id))
        file.inputStream().use { input ->
            checkNotNull(context.contentResolver.openOutputStream(destination, "wt")).use {
                input.copyBounded(it, StorageLimits.EPUB_BYTES)
            }
        }
    }

    fun delete(bookId: String, id: String): List<OriginalBookFile> = synchronized(SyncOperationCoordinator.localDataLock) {
        ensureArchive(bookId)
        check(archive.delete(bookId, id)) { "Cannot delete original" }
        archive.list(bookId)
    }

    private fun ensureArchive(bookId: String) {
        val book = checkNotNull(repository.getBook(bookId))
        if (archive.isInitialized(bookId)) return
        val bookRoot = safeChildDirectory(File(context.filesDir, "books"), bookId).canonicalFile
        val epub = File(book.book.originalFilePath).canonicalFile
        require(epub.toPath().startsWith(bookRoot.toPath()))
        // Older conversions kept source.* beside the EPUB. Check the import directory too,
        // since sync may have moved the active EPUB into a version subdirectory.
        val sources = listOfNotNull(bookRoot, epub.parentFile).distinct().flatMap { directory ->
            directory.listFiles().orEmpty().filter {
                it.isFile && it.name.matches(Regex("source\\.[A-Za-z0-9]{1,16}")) && it.canonicalFile.parentFile == directory
            }
        }.distinctBy { it.canonicalPath }
        val originals = sources.ifEmpty { listOf(epub).filter { it.isFile } }
        archive.initialize(bookId, originals.map { OriginalFileSource(it, "${book.metadata.title}.${it.extension}") })
        sources.forEach { check(it.delete()) { "Cannot remove migrated original" } }
    }

    companion object {
        fun mimeType(file: OriginalBookFile): String = EbookFormat.from("original.${file.format}", null)?.mimeTypes?.first()
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.format.lowercase()) ?: "application/octet-stream"
    }
}
