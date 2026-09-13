package com.foliore.android.debug

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import com.foliore.android.storage.*
import com.foliore.shared.db.DatabaseDriverFactory
import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.library.LibraryRepository
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking

/** Uses isolated databases/files and a local fake remote. Never touches the user's catalog or Drive. */
internal object BillingLimitHarness {
    fun run(context: Context) {
        val report = File(context.cacheDir, "billing-limit-result.txt")
        report.writeText("RUNNING")
        val root = File(context.cacheDir, "qa-billing/${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val a = IsolatedContext(context, File(root, "a"))
            val b = IsolatedContext(context, File(root, "b"))
            val repo = LibraryRepository(DatabaseDriverFactory(a).createDriver())
            val restored = LibraryRepository(DatabaseDriverFactory(b).createDriver())
            var unlimited = false
            fun importer() = AndroidEpubImporter(a, repo, DeviceIdProvider(a), isUnlimited = { unlimited })
            val importer = importer()
            fun epub(number: Int, title: String = "Book $number"): File = File(root, "$number-${title.hashCode()}.epub").also { file ->
                ZipOutputStream(file.outputStream()).use { zip ->
                    fun put(name: String, value: String) { zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray()); zip.closeEntry() }
                    put("mimetype", "application/epub+zip")
                    put("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
                    put("OPS/book.opf", """<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">billing-test-$number</dc:identifier><dc:title>$title</dc:title><dc:creator>QA</dc:creator><dc:language>en</dc:language></metadata><manifest><item id="page" href="page.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="page"/></spine></package>""")
                    put("OPS/page.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>QA</title></head><body><p>Unique content $number</p></body></html>""")
                }
            }
            fun import(file: File) = runBlocking { importer.importFile(file) }
            val sources = (1..51).map { epub(it) }
            val digests = sources.associateWith { sha(it) }
            for (file in sources.take(50)) check(import(file) is EpubImportResult.Imported)
            check(repo.bookCount() == 50)
            check(import(sources.last()) == EpubImportResult.LimitReached)
            check(import(sources.first()) is EpubImportResult.Duplicate)
            val replacement = import(epub(1, "Replacement")) as EpubImportResult.NeedsReview
            check(importer.commit(replacement.pendingImport) is EpubImportResult.Imported)
            check(AndroidBookDeleter(a, repo).delete(replacement.pendingImport.existingBookId) is BookDeleteResult.Deleted)
            check(repo.bookCount() == 50)
            val failedReplacement = import(epub(2, "Failed replacement")) as EpubImportResult.NeedsReview
            importer.discard(failedReplacement.pendingImport)
            check(importer.commit(failedReplacement.pendingImport) is EpubImportResult.Failed)
            check(repo.getBook(failedReplacement.pendingImport.existingBookId) != null)
            check(repo.bookCount() == 50)
            AndroidBookDeleter(a, repo).delete(repo.getBooks().last().book.id)
            check(repo.bookCount() == 49)
            val pool = Executors.newFixedThreadPool(2)
            val latch = CountDownLatch(1)
            val concurrent = listOf(epub(70), epub(71)).map { file -> pool.submit<EpubImportResult> {
                latch.await(); runBlocking { importer().importFile(file) }
            } }
            latch.countDown()
            val outcomes = concurrent.map { it.get(30, TimeUnit.SECONDS) }
            pool.shutdownNow()
            check(outcomes.count { it is EpubImportResult.Imported } == 1)
            check(outcomes.count { it == EpubImportResult.LimitReached } == 1)
            check(repo.bookCount() == 50)
            unlimited = true
            for (number in 80..84) check(import(epub(number)) is EpubImportResult.Imported)
            check(repo.bookCount() == 55)
            unlimited = false
            check(import(epub(85)) == EpubImportResult.LimitReached)
            val book = repo.getBooks().first()
            val m = book.metadata
            val draft = BookMetadataDraft("Edited after refund", m.subtitle, m.authors, m.sortAuthor, m.language, m.publisher,
                m.publishedDate, m.description, m.tags, m.isbn, m.identifiers, m.seriesName, m.seriesIndex)
            check(AndroidBookEditor(a, repo, DeviceIdProvider(a)).updateMetadata(book.book.id, draft) is BookEditResult.Success)
            check(AndroidPreparedEpubService(a, repo, DeviceIdProvider(a)).prepare(book.book.id) is PreparedEpubResult.Prepared)
            val remote = LocalStore(File(root, "remote"))
            val syncA = PortableLibrarySyncEngine(a, repo, DeviceIdProvider(a), AiCoverPromptRepository(a))
            val syncB = PortableLibrarySyncEngine(b, restored, DeviceIdProvider(b), AiCoverPromptRepository(b))
            syncA.sync(remote)
            syncB.sync(remote)
            check(restored.bookCount() == 55) { "Restore incorrectly applied the free cap" }
            for (item in restored.getBooks()) check(sha(File(item.book.originalFilePath)) == item.book.originalHashSha256)
            check(digests.all { (file, hash) -> file.isFile && sha(file) == hash })
            check(File(a.cacheDir, "imports").listFiles().orEmpty().isEmpty()) { "Temporary rejected imports leaked" }
            report.writeText("PASS: 50 free; 51st rejected; exact duplicate; replacement at cap; failed replacement preserves original; deletion frees slot; concurrent imports bounded; unlimited >50; refund preserves 55 books; editing and preparation above cap; sync/restore all 55 with original hashes; source files intact; no leaked temporary imports\n")
        } catch (error: Throwable) { report.writeText("FAIL: ${error.stackTraceToString()}"); throw error }
    }
    private fun sha(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    private class IsolatedContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
        override fun getDatabasePath(name: String) = File(root, "databases/$name").apply { parentFile!!.mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = super.getSharedPreferences("qa-billing-${root.absolutePath.hashCode()}-$name", mode)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)
    }
    private class LocalStore(private val root: File) : RemoteLibraryStore {
        override val label = "Billing QA local remote"
        override fun listRootEntries() = root.listFiles().orEmpty().map { RemoteRootEntry(it.name, it.isDirectory, it.lastModified()) }
        override fun readText(path: String) = File(root, path).takeIf { it.isFile }?.readText()
        override fun writeText(path: String, text: String, mimeType: String) { File(root, path).apply { parentFile!!.mkdirs(); writeText(text) } }
        override fun replaceFile(path: String, source: File, mimeType: String) { File(root, path).also { it.parentFile!!.mkdirs(); source.copyTo(it, true) } }
        override fun copyToLocal(path: String, destination: File): File? = File(root, path).takeIf { it.isFile }?.let { destination.parentFile!!.mkdirs(); it.copyTo(destination, true) }
        override fun hasDirectory(path: String) = File(root, path).isDirectory
        override fun ensureDirectory(path: String) { File(root, path).mkdirs() }
        override fun deleteTree(path: String) { File(root, path).deleteRecursively() }
        override fun deleteUnexpectedChildren(directoryPath: String, expectedDirectoryNames: Set<String>): Boolean = error("Unexpected cleanup")
    }
}
