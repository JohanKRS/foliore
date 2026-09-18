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
        val (ackContext, ackApp) = device("lost-ack")
        val ackRemote = LocalStore(File(root, "lost-ack-remote").apply { mkdirs() })
        val ackId = (runBlocking { ackApp.epubImporter.importFile(epub) } as EpubImportResult.Imported).bookId
        check(ackApp.preparedEpubService.prepare(ackId) is PreparedEpubResult.Prepared)
        ackRemote.fault = "after-commit"
        check(runCatching { engine(ackContext, ackApp).sync(ackRemote) }.isFailure)
        val ackRetry = engine(ackContext, ackApp).sync(ackRemote)
        check(ackRetry.conflictedBooks == 0 && ackApp.libraryRepository.bookCount() == 1) {
            "A lost publication acknowledgement created a duplicate conflict"
        }
        // A locally merged version is not backed up until its own publication commits.
        val (mergeContextA, mergeA) = device("merge-retry-a")
        val (mergeContextB, mergeB) = device("merge-retry-b")
        val mergeRemote = LocalStore(File(root, "merge-retry-remote").apply { mkdirs() })
        val mergeId = (runBlocking { mergeA.epubImporter.importFile(epub) } as EpubImportResult.Imported).bookId
        engine(mergeContextA, mergeA).sync(mergeRemote)
        engine(mergeContextB, mergeB).sync(mergeRemote)
        for (stage in listOf("partial-upload", "record", "commit")) {
            fun mergeDraft(app: AppContainer) = app.libraryRepository.getBook(mergeId)!!.metadata.let { m ->
                BookMetadataDraft(m.title, m.subtitle, m.authors, m.sortAuthor, m.language, m.publisher,
                    m.publishedDate, m.description, m.tags, m.isbn, m.identifiers, m.seriesName, m.seriesIndex)
            }
            check(mergeA.bookEditor.updateMetadata(mergeId, mergeDraft(mergeA).copy(title = "Remote $stage")) is BookEditResult.Success)
            check(mergeB.bookEditor.updateMetadata(mergeId, mergeDraft(mergeB).copy(authors = listOf("Local $stage"))) is BookEditResult.Success)
            engine(mergeContextA, mergeA).sync(mergeRemote)
            mergeRemote.fault = stage
            check(runCatching { engine(mergeContextB, mergeB).sync(mergeRemote) }.isFailure)
            val interrupted = mergeB.libraryRepository.getBook(mergeId)!!
            check(interrupted.metadata.title == "Remote $stage" && interrupted.metadata.authors == listOf("Local $stage"))
            check(interrupted.book.syncStatus == com.foliore.shared.domain.SyncStatus.PENDING_UPLOAD) {
                "Unpublished merge marked synced after $stage"
            }
            engine(mergeContextB, mergeB).sync(mergeRemote)
            engine(mergeContextA, mergeA).sync(mergeRemote)
            for (app in listOf(mergeA, mergeB)) {
                val recovered = app.libraryRepository.getBook(mergeId)!!
                check(recovered.metadata.title == "Remote $stage" && recovered.metadata.authors == listOf("Local $stage")) {
                    "Interrupted merge lost metadata after $stage"
                }
                check(File(recovered.book.originalFilePath).sha256() == epub.sha256())
            }
        }
        val id = imported.bookId
        check(engineA.sync(remote).uploadedBooks == 1)
        check(engineB.sync(remote).downloadedBooks == 1)
        // Reading state travels in the journal without changing book revisions or re-uploading EPUBs.
        val readingA = com.foliore.android.reader.ReadingStateRepository(contextA)
        val readingB = com.foliore.android.reader.ReadingStateRepository(contextB)
        val contentHash = a.libraryRepository.getBook(id)!!.book.originalHashSha256
        val revisionBeforeReading = a.libraryRepository.getBook(id)!!.book.revision
        val point = com.foliore.android.reader.ReadingLocator("OPS/page.xhtml", .7, 5, "Preserve")
        readingA.savePosition(id, contentHash, point)
        readingA.addBookmark(id, contentHash, point, "A bookmark")
        remote.uploadedPaths.clear()
        check(engineA.sync(remote).uploadedBooks == 0)
        check(remote.uploadedPaths.isEmpty()) { "Reading uploaded book payloads" }
        engineB.sync(remote)
        check(readingB.position(id, contentHash)?.locator == point)
        check(readingB.bookmarks(id, contentHash).size == 1)
        // Deliberately moving backwards is a newer reading choice, not a lower-priority progress value.
        val rewind = point.copy(progression = .2, textOffset = 1)
        readingB.savePosition(id, contentHash, rewind)
        readingB.deleteBookmark(readingB.bookmarks(id, contentHash).single().id)
        remote.fault = "record"
        check(runCatching { engineB.sync(remote) }.isFailure)
        check(readingB.position(id, contentHash)?.locator == rewind)
        engineB.sync(remote)
        engineA.sync(remote)
        check(readingA.position(id, contentHash)?.locator == rewind)
        check(readingA.bookmarks(id, contentHash).isEmpty())
        check(a.libraryRepository.getBook(id)!!.book.revision == revisionBeforeReading)
        val unchangedReadingRemote = remote.bytes()
        engineA.sync(remote)
        check(remote.bytes().keys == unchangedReadingRemote.keys) { "No-op reading sync republished a journal" }
        val before = remote.bytes()
        val metadata = a.libraryRepository.getBook(id)!!.metadata
        val draft = BookMetadataDraft("QA Edited \$5 \\ path", metadata.subtitle, metadata.authors, metadata.sortAuthor,
            metadata.language, metadata.publisher, metadata.publishedDate, metadata.description, metadata.tags,
            metadata.isbn, metadata.identifiers, metadata.seriesName, metadata.seriesIndex)
        check(a.bookEditor.updateMetadata(id, draft) is BookEditResult.Success)
        check(a.preparedEpubService.prepare(id) is PreparedEpubResult.Prepared)
        remote.uploadedPaths.clear()
        remote.failNextFileWrite = true
        check(runCatching { engineA.sync(remote) }.isFailure)
        before.forEach { (path, bytes) -> check(remote.bytes()[path]?.contentEquals(bytes) == true) { "Previous backup changed: $path" } }
        check(b.libraryRepository.getBook(id)!!.metadata.title == "QA Original")
        check(engineA.sync(remote).uploadedBooks == 1)
        check(remote.uploadedPaths.none { it.endsWith("/original.epub") }) { "Metadata edit uploaded unchanged original" }
        check(remote.uploadedPaths.any { it.endsWith("/prepared.epub") }) { "New prepared EPUB was skipped" }
        remote.downloadedPaths.clear()
        check(engineB.sync(remote).downloadedBooks == 1)
        check(remote.downloadedPaths.none { it.endsWith("/original.epub") }) { "Metadata edit downloaded unchanged original" }
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
        check(readingA.state().positions.none { it.bookId == id } && readingA.state().bookmarks.none { it.bookId == id }) { "Sync restored reading data for a deleted book" }
        engineB.sync(remote)
        check(readingB.state().positions.none { it.bookId == id } && readingB.state().bookmarks.none { it.bookId == id }) { "Remote deletion retained reading data" }
        check(b.libraryRepository.getBook(id) == null) { "Explicit deletion was not propagated" }
        check(!File(contextB.filesDir, "books/$id").exists()) { "Remote deletion left local book versions behind" }
        // Simulate files left behind when a process died after removing the database row.
        File(contextB.filesDir, "books/$id/versions/interrupted").apply { mkdirs() }
        engineB.sync(remote)
        check(!File(contextB.filesDir, "books/$id").exists()) { "Interrupted deletion cleanup did not resume" }
        engineA.sync(remote)
        for (app in listOf(a, b)) {
            val recovered = app.libraryRepository.getBooks().first { it.metadata.title.startsWith("Offline edit during deletion") }
            check(File(recovered.book.originalFilePath).sha256() == epub.sha256())
        }
        val deletionA = PendingRemoteDeletionStore(contextA, "drive:account-a")
        val deletionB = PendingRemoteDeletionStore(contextA, "drive:account-b")
        deletionA.markDeleted("scope-test", 1, "now")
        check("scope-test" !in deletionB.pendingBookIds())
        check(deletionA.matchesDeletedVersion("scope-test", 1, "now"))
        check(!deletionA.matchesDeletedVersion("scope-test", 2, "later"))
        check(remote.bytes().any { it.key.endsWith("original.epub") }) { "Deleted book must retain its recovery copy" }
        val faultBookId = a.libraryRepository.getBooks().first().book.id
        fun currentDraft() = a.libraryRepository.getBook(faultBookId)!!.metadata.let { m ->
            BookMetadataDraft(m.title, m.subtitle, m.authors, m.sortAuthor, m.language, m.publisher, m.publishedDate,
                m.description, m.tags, m.isbn, m.identifiers, m.seriesName, m.seriesIndex)
        }
        for (stage in listOf("partial-upload", "record", "commit")) {
            val previous = RemoteManifestJournal(remote).read()
            val previousB = b.libraryRepository.getBook(faultBookId)!!.metadata.title
            val edit = currentDraft().copy(title = "Fault recovery $stage")
            check(a.bookEditor.updateMetadata(faultBookId, edit) is BookEditResult.Success)
            remote.fault = stage
            check(runCatching { engineA.sync(remote) }.isFailure) { "Fault not exercised: $stage" }
            check(remote.fault == null)
            check(RemoteManifestJournal(remote).read() == previous) { "Incomplete $stage changed published state" }
            check(b.libraryRepository.getBook(faultBookId)!!.metadata.title == previousB)
            engineA.sync(remote)
            engineB.sync(remote)
            check(b.libraryRepository.getBook(faultBookId)!!.metadata.title == edit.title)
        }
        val beforeDownload = b.libraryRepository.getBook(faultBookId)!!
        val originalBeforeDownload = File(beforeDownload.book.originalFilePath).sha256()
        check(a.bookEditor.updateMetadata(faultBookId, currentDraft().copy(title = "After corrupt download")) is BookEditResult.Success)
        engineA.sync(remote)
        remote.fault = "download"
        val failedDownload = runCatching { engineB.sync(remote) }
        check(failedDownload.isFailure) { "Corrupt download was reported as successful sync" }
        check(remote.fault == null)
        check(b.libraryRepository.getBook(faultBookId)!!.metadata.title == beforeDownload.metadata.title)
        check(b.libraryRepository.getBook(faultBookId)!!.book.syncStatus == beforeDownload.book.syncStatus) { "Transfer failure changed editing conflict state" }
        check(File(beforeDownload.book.originalFilePath).sha256() == originalBeforeDownload)
        engineB.sync(remote)
        check(b.libraryRepository.getBook(faultBookId)!!.metadata.title == "After corrupt download")
        val original = File(a.libraryRepository.getBook(faultBookId)!!.book.originalFilePath)
        val originalBytes = original.readBytes()
        try {
            original.appendText("Injected corruption")
            val attempt = runCatching { engineA.sync(remote) }
            val current = a.libraryRepository.getBook(faultBookId)!!.book
            check(attempt.isFailure || File(current.originalFilePath).sha256() == current.originalHashSha256) { "Corrupt local bytes were accepted without repair" }
        } finally { original.writeBytes(originalBytes) }
        check(a.bookEditor.updateMetadata(faultBookId, currentDraft().copy(title = "Snapshot before in-flight edit")) is BookEditResult.Success)
        remote.onNextFileWrite = {
            check(a.bookEditor.updateMetadata(faultBookId, currentDraft().copy(title = "New local edit during upload")) is BookEditResult.Success)
        }
        engineA.sync(remote)
        val inFlight = a.libraryRepository.getBook(faultBookId)!!
        check(inFlight.metadata.title == "New local edit during upload")
        check(inFlight.book.syncStatus != com.foliore.shared.domain.SyncStatus.SYNCED) { "In-flight local edit marked synced" }
        engineA.sync(remote)
        engineB.sync(remote)
        check(b.libraryRepository.getBook(faultBookId)!!.metadata.title == "New local edit during upload")
        File(context.cacheDir, "sync-safety-result.txt").writeText("PASS: reading position/bookmarks round trip, rewind, interrupted reading publication/retry, reading without EPUB upload or metadata revision, import, upload, restore, injected failure, retry, exact original, prepared copy, no-op sync, offline field merge, conflicting edits preserved on both devices, simultaneous publication converges, deletion preserves offline edit, account-scoped deletion, retention, partial upload, partial record, partial commit, corrupt download rollback, local corruption rejected or recovered, in-flight local edit preserved\n")
    }

    private class LocalStore(private val root: File) : RemoteLibraryStore {
        val uploadedPaths = java.util.Collections.synchronizedList(mutableListOf<String>())
        val downloadedPaths = java.util.Collections.synchronizedList(mutableListOf<String>())
        override val label = "Isolated QA"
        var failNextFileWrite = false
        var fault: String? = null
        var onNextFileWrite: (() -> Unit)? = null
        @Synchronized private fun consume(mode: String): Boolean = (fault == mode).also { if (it) fault = null }
        @Volatile var publicationBarrier: java.util.concurrent.CyclicBarrier? = null
        override fun listRootEntries() = root.listFiles().orEmpty().map { RemoteRootEntry(it.name, it.isDirectory, it.lastModified()) }
        override fun readText(path: String) = File(root, path).takeIf { it.isFile }?.readText()
        override fun writeText(path: String, text: String, mimeType: String) {
            val inject = path.startsWith(RemoteManifestJournal.RECORD_PREFIX) && consume("record") || path.startsWith("foliore-v4-commit-") && consume("commit")
            File(root, path).apply { parentFile!!.mkdirs(); writeText(if (inject) "{" else text) }
            if (inject) error("Injected incomplete publication")
            if (path.startsWith(RemoteManifestJournal.COMMIT_PREFIX) && consume("after-commit")) error("Injected lost publication acknowledgement")
            if (path.startsWith(RemoteManifestJournal.RECORD_PREFIX)) publicationBarrier?.await(20, java.util.concurrent.TimeUnit.SECONDS)
        }
        override fun replaceFile(path: String, source: File, mimeType: String) {
            uploadedPaths += path
            if (failNextFileWrite) { failNextFileWrite = false; error("Injected upload failure") }
            onNextFileWrite?.also { onNextFileWrite = null }?.invoke()
            File(root, path).apply {
                parentFile!!.mkdirs()
                if (consume("partial-upload")) { writeBytes(source.readBytes().take(32).toByteArray()); error("Injected partial payload") }
                source.copyTo(this, true)
            }
        }
        override fun copyToLocal(path: String, destination: File): File? = File(root, path).takeIf { it.isFile }?.let {
            downloadedPaths += path
            destination.parentFile!!.mkdirs()
            it.copyTo(destination, true).also { if (consume("download")) it.writeText("Injected corrupt download") }
        }
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
