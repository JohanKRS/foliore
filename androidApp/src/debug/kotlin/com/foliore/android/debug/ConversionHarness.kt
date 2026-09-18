package com.foliore.android.debug

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import com.foliore.android.storage.*
import com.foliore.shared.db.DatabaseDriverFactory
import com.foliore.shared.epub.EpubParser
import com.foliore.shared.library.LibraryRepository
import java.io.File
import java.nio.ByteBuffer
import java.util.UUID
import kotlinx.coroutines.runBlocking

/** Full Android importer/cover/storage/preparation regression in a disposable local database. */
internal object ConversionHarness {
    fun run(context: Context) {
        val report = File(context.cacheDir, "conversion-result.txt").apply { writeText("RUNNING") }
        val root = File(context.cacheDir, "qa-conversion-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val fixtures = File(context.filesDir, "conversion-fixtures")
            val checks = mutableListOf<String>()
            for (name in listOf("legacy.mobi", "kindle.azw3", "hybrid.mobi", "source.epub")) {
                val isolated = IsolatedContext(context, File(root, name))
                val repository = LibraryRepository(DatabaseDriverFactory(isolated).createDriver())
                val device = DeviceIdProvider(isolated)
                val importer = AndroidEpubImporter(isolated, repository, device, { true })
                val originals = AndroidOriginalFilesService(isolated, repository)
                val archive = OriginalFileArchive(File(isolated.filesDir, OriginalFileArchive.DIRECTORY))
                val source = File(fixtures, name)
                val hash = source.sha256()
                val imported = runBlocking { importer.importFile(source) } as EpubImportResult.Imported
                val book = checkNotNull(repository.getBook(imported.bookId))
                check(book.metadata.title == "Conversão Foliore")
                check(book.book.coverImagePath != null && File(book.book.coverImagePath!!).isFile)
                val original = File(book.book.originalFilePath)
                check(original.sha256() == book.book.originalHashSha256)
                var savedOriginal = originals.list(imported.bookId).single()
                check(savedOriginal.name == name)
                check(archive.file(imported.bookId, savedOriginal.id)!!.sha256() == hash)
                // Simulate a pre-archive installation and migrate it lazily on opening the popup.
                archive.deleteBook(imported.bookId)
                val legacySource = if (source.extension != "epub") File(original.parentFile, "source.${source.extension}").also { source.copyTo(it) } else null
                savedOriginal = originals.list(imported.bookId).single()
                check(archive.file(imported.bookId, savedOriginal.id)!!.sha256() == hash)
                check(legacySource == null || !legacySource.exists())
                check(runBlocking { importer.importFile(source) } is EpubImportResult.Duplicate)
                check(repository.bookCount() == 1)
                check(AndroidPreparedEpubService(isolated, repository, device).prepare(imported.bookId) is PreparedEpubResult.Prepared)
                val prepared = File(checkNotNull(repository.getBook(imported.bookId)!!.book.preparedFilePath))
                AndroidZipEpubArchive(prepared).use { check(EpubParser().parse(it).isReadable) }
                // A renamed metadata copy has the same content and exercises pending-source cleanup.
                if (source.extension == "mobi") {
                    val changed = File(root, "changed-$name")
                    val bytes = source.readBytes()
                    val title = "Conversão Foliore".toByteArray()
                    for (index in 0..bytes.size - title.size) {
                        if (title.indices.all { bytes[index + it] == title[it] }) bytes[index] = 'D'.code.toByte()
                    }
                    changed.writeBytes(bytes)
                    val review = runBlocking { importer.importFile(changed) } as EpubImportResult.NeedsReview
                    importer.discard(review.pendingImport)
                    check(repository.bookCount() == 1)
                    val again = runBlocking { importer.importFile(changed) } as EpubImportResult.NeedsReview
                    val replacement = importer.commit(again.pendingImport) as EpubImportResult.Imported
                    val replacementOriginal = originals.list(replacement.bookId).single()
                    check(archive.file(replacement.bookId, replacementOriginal.id)!!.sha256() == changed.sha256())
                    check(AndroidBookDeleter(isolated, repository).delete(replacement.bookId) is BookDeleteResult.Deleted)
                    check(!archive.isInitialized(replacement.bookId))
                }
                // Deleting any format must preserve the working EPUB and all book actions.
                check(originals.delete(imported.bookId, savedOriginal.id).isEmpty())
                check(AndroidOriginalFilesService(isolated, repository).list(imported.bookId).isEmpty())
                check(original.sha256() == book.book.originalHashSha256)
                check(AndroidPreparedEpubService(isolated, repository, device).prepare(imported.bookId) is PreparedEpubResult.Prepared)
                check(File(isolated.cacheDir, "imports").listFiles().orEmpty().isEmpty())
                check(source.sha256() == hash)
                checks += "$name: imported, metadata/cover, source archived, duplicate, prepared EPUB, delete original/reload/re-prepare, cleanup"
            }
            val protected = File(root, "protected.mobi")
            val bytes = File(fixtures, "legacy.mobi").readBytes()
            val record = ByteBuffer.wrap(bytes, 78, 4).int
            bytes[record + 12] = 0; bytes[record + 13] = 2
            protected.writeBytes(bytes)
            val isolated = IsolatedContext(context, File(root, "rejections"))
            val repository = LibraryRepository(DatabaseDriverFactory(isolated).createDriver())
            val importer = AndroidEpubImporter(isolated, repository, DeviceIdProvider(isolated), { true })
            check(runBlocking { importer.importFile(protected) } == EpubImportResult.Failed(ImportFailureReason.DRM_PROTECTED))
            val invalid = File(root, "invalid.azw3").apply { writeText("Not a book") }
            check(runBlocking { importer.importFile(invalid) } == EpubImportResult.Failed(ImportFailureReason.CONVERSION_FAILED))
            check(repository.bookCount() == 0)
            check(File(isolated.cacheDir, "imports").listFiles().orEmpty().isEmpty())
            checks += "DRM/corrupt files rejected, no rows or temporary files"
            report.writeText("PASS\n" + checks.joinToString("\n"))
        } catch (error: Throwable) {
            report.writeText("FAIL\n${error.stackTraceToString()}")
            throw error
        } finally { root.deleteRecursively() }
    }

    private class IsolatedContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
        override fun getDatabasePath(name: String) = File(root, "databases/$name").apply { parentFile!!.mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = super.getSharedPreferences("qa-conversion-${root.name}-$name", mode)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)
    }
}
