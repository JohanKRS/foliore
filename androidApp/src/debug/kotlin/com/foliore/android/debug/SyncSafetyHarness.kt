package com.foliore.android.debug

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import com.foliore.android.di.AppContainer
import com.foliore.android.storage.*
import com.foliore.shared.domain.BookMetadataDraft
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking

/** Emulator-only fault injection. Every database, preference and file is isolated from the user's library. */
internal object SyncSafetyHarness {
    fun run(context: Context) {
        File(context.cacheDir, "sync-safety-result.txt").writeText("RUNNING\n")
        val session = UUID.randomUUID().toString()
        val root = File(context.cacheDir, "qa-sync/$session").apply { mkdirs() }
        val covers = File(root, "cover-controls").apply { mkdirs() }
        val bitmap = android.graphics.Bitmap.createBitmap(16, 24, android.graphics.Bitmap.Config.ARGB_8888)
        for (format in listOf(android.graphics.Bitmap.CompressFormat.PNG, android.graphics.Bitmap.CompressFormat.JPEG)) {
            val bytes = java.io.ByteArrayOutputStream().also { bitmap.compress(format, 90, it) }.toByteArray()
            check(CoverImageWriter().writeCover(covers, bytes) != null) { "Valid raster cover rejected" }
            if (format == android.graphics.Bitmap.CompressFormat.PNG) {
                val huge = bytes.copyOf()
                java.nio.ByteBuffer.wrap(huge).putInt(16, 10000).putInt(20, 10000)
                val crc = java.util.zip.CRC32().apply { update(huge, 12, 17) }
                java.nio.ByteBuffer.wrap(huge).putInt(29, crc.value.toInt())
                check(runCatching { CoverImageWriter().writeCover(covers, huge) }.getOrNull() == null) { "Oversized pixels accepted" }
            }
        }
        bitmap.recycle()
        val remote = LocalStore(File(root, "remote").apply { mkdirs() })
        fun device(name: String): Pair<Context, AppContainer> {
            val isolated = IsolatedContext(context.applicationContext, File(root, name), "$session-$name")
            return isolated to AppContainer(isolated)
        }
        val (contextA, a) = device("a")
        val (contextB, b) = device("b")
        fun engine(c: Context, app: AppContainer) = PortableLibrarySyncEngine(c, app.libraryRepository, DeviceIdProvider(c), app.aiCoverPromptRepository)
        val engineA = engine(contextA, a)
        val engineB = engine(contextB, b)
        val epub = File(root, "test.epub")
        ZipOutputStream(epub.outputStream()).use { zip ->
            fun put(name: String, text: String) { zip.putNextEntry(ZipEntry(name)); zip.write(text.toByteArray()); zip.closeEntry() }
            put("mimetype", "application/epub+zip")
            put("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            put("OPS/book.opf", """<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">qa-book</dc:identifier><dc:title>QA Original</dc:title><dc:creator>QA Author</dc:creator><dc:language>en</dc:language></metadata><manifest><item id="page" href="page.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="page"/></spine></package>""")
            put("OPS/page.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>QA</title></head><body><p>Preserve this original.</p></body></html>""")
        }
        val imported = runBlocking { a.epubImporter.importFile(epub) } as EpubImportResult.Imported
        val id = imported.bookId
        check(engineA.sync(remote).uploadedBooks == 1)
        check(engineB.sync(remote).downloadedBooks == 1)
        val before = remote.bytes()
        val metadata = a.libraryRepository.getBook(id)!!.metadata
        val draft = BookMetadataDraft("QA Edited \$5 \\ path", metadata.subtitle, metadata.authors, metadata.sortAuthor,
            metadata.language, metadata.publisher, metadata.publishedDate, metadata.description, metadata.tags,
            metadata.isbn, metadata.identifiers, metadata.seriesName, metadata.seriesIndex)
        check(a.bookEditor.updateMetadata(id, draft) is BookEditResult.Success)
        check(a.preparedEpubService.prepare(id) is PreparedEpubResult.Prepared)
        remote.failNextFileWrite = true
        check(runCatching { engineA.sync(remote) }.isFailure)
        before.forEach { (path, bytes) -> check(remote.bytes()[path]?.contentEquals(bytes) == true) { "Previous backup changed: $path" } }
        check(b.libraryRepository.getBook(id)!!.metadata.title == "QA Original")
        check(engineA.sync(remote).uploadedBooks == 1)
        check(engineB.sync(remote).downloadedBooks == 1)
        val restored = b.libraryRepository.getBook(id)!!
        check(restored.metadata.title == draft.title)
        check(File(restored.book.originalFilePath).sha256() == epub.sha256())
        check(restored.book.preparedFilePath != restored.book.originalFilePath)
        check(engineA.sync(remote).uploadedBooks == 0) { "Unchanged sync must not upload again" }
        check(a.bookEditor.updateMetadata(id, draft.copy(title = "Offline title")) is BookEditResult.Success)
        check(b.bookEditor.updateMetadata(id, draft.copy(authors = listOf("Offline author"))) is BookEditResult.Success)
        engineA.sync(remote)
        engineB.sync(remote)
        engineA.sync(remote)
        for (app in listOf(a, b)) {
            val merged = app.libraryRepository.getBook(id)!!
            check(merged.metadata.title == "Offline title") { "Merged title lost" }
            check(merged.metadata.authors == listOf("Offline author")) { "Merged author lost" }
        }
        val shared = draft.copy(title = "Offline title", authors = listOf("Offline author"))
        check(a.bookEditor.updateMetadata(id, shared.copy(title = "Conflicting A")) is BookEditResult.Success)
        check(b.bookEditor.updateMetadata(id, shared.copy(title = "Conflicting B")) is BookEditResult.Success)
        engineA.sync(remote)
        engineB.sync(remote)
        engineA.sync(remote)
        for (app in listOf(a, b)) {
            val books = app.libraryRepository.getBooks()
            check(books.size == 2) { "Conflict branch missing: ${books.map { it.metadata.title }}" }
            check(books.any { it.metadata.title == "Conflicting A" })
            check(books.any { it.metadata.title.startsWith("Conflicting B") })
        }
        check(a.bookEditor.updateMetadata(id, shared.copy(title = "Concurrent C")) is BookEditResult.Success)
        check(b.bookEditor.updateMetadata(id, shared.copy(title = "Concurrent D")) is BookEditResult.Success)
        remote.publicationBarrier = java.util.concurrent.CyclicBarrier(2)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit<ManualSyncResult.Synced> { engineA.sync(remote) }
            val second = pool.submit<ManualSyncResult.Synced> { engineB.sync(remote) }
            first.get(30, java.util.concurrent.TimeUnit.SECONDS)
            second.get(30, java.util.concurrent.TimeUnit.SECONDS)
        } finally { remote.publicationBarrier = null; pool.shutdownNow() }
        repeat(2) { engineA.sync(remote); engineB.sync(remote) }
        val titlesA = a.libraryRepository.getBooks().map { it.metadata.title }.sorted()
        val titlesB = b.libraryRepository.getBooks().map { it.metadata.title }.sorted()
        check(titlesA == titlesB && titlesA.size == 3) { "Concurrent publication did not converge: $titlesA / $titlesB" }
        check(titlesA.any { it.startsWith("Concurrent C") } && titlesA.any { it.startsWith("Concurrent D") })
        check(engineA.sync(remote).uploadedBooks == 0 && engineB.sync(remote).uploadedBooks == 0)
        check(b.bookEditor.updateMetadata(id, shared.copy(title = "Offline edit during deletion")) is BookEditResult.Success)
        check(a.bookDeleter.delete(id) is BookDeleteResult.Deleted)
        engineA.sync(remote)
        engineB.sync(remote)
        check(b.libraryRepository.getBook(id) == null) { "Explicit deletion was not propagated" }
        engineA.sync(remote)
        for (app in listOf(a, b)) check(app.libraryRepository.getBooks().any { it.metadata.title.startsWith("Offline edit during deletion") })
        val deletionA = PendingRemoteDeletionStore(contextA, "drive:account-a")
        val deletionB = PendingRemoteDeletionStore(contextA, "drive:account-b")
        deletionA.markDeleted("scope-test", 1, "now")
        check("scope-test" !in deletionB.pendingBookIds())
        check(deletionA.matchesDeletedVersion("scope-test", 1, "now"))
        check(!deletionA.matchesDeletedVersion("scope-test", 2, "later"))
        check(remote.bytes().any { it.key.endsWith("original.epub") }) { "Deleted book must retain its recovery copy" }
        File(context.cacheDir, "sync-safety-result.txt").writeText("PASS: import, upload, restore, injected failure, retry, exact original, prepared copy, no-op sync, offline field merge, conflicting edits preserved on both devices, simultaneous publication converges, deletion preserves offline edit, account-scoped deletion, retention\n")
    }

    private class LocalStore(private val root: File) : RemoteLibraryStore {
        override val label = "Isolated QA"
        var failNextFileWrite = false
        @Volatile var publicationBarrier: java.util.concurrent.CyclicBarrier? = null
        override fun listRootEntries() = root.listFiles().orEmpty().map { RemoteRootEntry(it.name, it.isDirectory, it.lastModified()) }
        override fun readText(path: String) = File(root, path).takeIf { it.isFile }?.readText()
        override fun writeText(path: String, text: String, mimeType: String) {
            File(root, path).apply { parentFile!!.mkdirs(); writeText(text) }
            if (path.startsWith(RemoteManifestJournal.RECORD_PREFIX)) publicationBarrier?.await(20, java.util.concurrent.TimeUnit.SECONDS)
        }
        override fun replaceFile(path: String, source: File, mimeType: String) {
            if (failNextFileWrite) { failNextFileWrite = false; error("Injected upload failure") }
            File(root, path).apply { parentFile!!.mkdirs(); source.copyTo(this, true) }
        }
        override fun copyToLocal(path: String, destination: File): File? = File(root, path).takeIf { it.isFile }?.let { destination.parentFile!!.mkdirs(); it.copyTo(destination, true) }
        override fun hasDirectory(path: String) = File(root, path).isDirectory
        override fun ensureDirectory(path: String) { File(root, path).mkdirs() }
        override fun deleteTree(path: String) { File(root, path).deleteRecursively() }
        override fun deleteUnexpectedChildren(directoryPath: String, expectedDirectoryNames: Set<String>): Boolean = error("Unsafe cleanup must not run")
        fun bytes() = root.walkTopDown().filter { it.isFile }.associate { it.relativeTo(root).path to it.readBytes() }
    }

    private class IsolatedContext(base: Context, private val root: File, private val prefix: String) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        override fun getDatabasePath(name: String) = File(root, "databases/$name").apply { parentFile!!.mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = super.getSharedPreferences("qa-$prefix-$name", mode)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
            SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)
    }
}
