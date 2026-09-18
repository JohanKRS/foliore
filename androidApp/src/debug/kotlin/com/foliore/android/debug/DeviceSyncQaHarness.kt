package com.foliore.android.debug

import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.foliore.android.di.AppContainer
import com.foliore.android.storage.*
import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.domain.LibraryBook
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

/** Local QA commands only; excluded from release. Credentials never leave the app process. */
internal object DeviceSyncQaHarness {
    fun run(context: Context, intent: Intent) {
        val run = intent.getStringExtra("qaRun") ?: UUID.randomUUID().toString()
        require(run.matches(Regex("[a-zA-Z0-9_-]{1,80}")))
        val out = File(context.cacheDir, "device-qa/$run.json").apply { parentFile!!.mkdirs() }
        out.writeText("{\"status\":\"RUNNING\"}")
        val start = SystemClock.elapsedRealtime()
        val uid = Process.myUid()
        val rx = TrafficStats.getUidRxBytes(uid)
        val tx = TrafficStats.getUidTxBytes(uid)
        val app = AppContainer(context.applicationContext)
        val events = mutableListOf<JsonElement>()
        val action = intent.getStringExtra("qaAction")!!
        fun event(progress: ManualSyncProgress) = synchronized(events) {
            events.add(buildJsonObject {
                put("ms", SystemClock.elapsedRealtime() - start); put("phase", progress.phase.name)
                put("current", progress.current); put("total", progress.total)
            })
            Log.d("FolioreDeviceQA", "$run phase=${progress.phase} current=${progress.current} total=${progress.total}")
        }
        val result = runCatching {
            when (action) {
                "inspect" -> snapshot(app, intent.getBooleanExtra("qaHashes", false))
                "schedule" -> {
                    app.syncWorkScheduler.enqueueAutoSync()
                    buildJsonObject { put("enqueued", true) }
                }
                "workers" -> {
                    val work = androidx.work.WorkManager.getInstance(context).getWorkInfosForUniqueWork("foliore_auto_sync_once").get()
                    buildJsonObject { put("workers", JsonArray(work.map { buildJsonObject {
                        put("id", it.id.toString()); put("state", it.state.name); put("attempts", it.runAttemptCount)
                    } })) }
                }
                "sync" -> summary(app.syncService.syncNow(allowGoogleDriveLibraryRecreation = intent.getBooleanExtra("qaRecreate", false), onProgress = ::event))
                "drive-performance-fixture", "drive-fixture-cleanup" -> synchronized(SyncOperationCoordinator.remoteLock) {
                    drivePerformanceFixture(context, app, action == "drive-fixture-cleanup")
                }
                "drive-batch-fixture" -> synchronized(SyncOperationCoordinator.remoteLock) { driveBatchFixture(app) }
                "remove-invalid" -> {
                    val invalid = app.libraryRepository.getBooks().filter { File(it.book.originalFilePath).isFile && File(it.book.originalFilePath).sha256() != it.book.originalHashSha256 }
                    buildJsonObject { put("removed", JsonArray(invalid.map { book ->
                        check(app.bookDeleter.delete(book.book.id) is BookDeleteResult.Deleted)
                        JsonPrimitive(book.book.id)
                    })) }
                }
                "create" -> {
                    val id = createBook(context, app)
                    buildJsonObject { put("bookId", id) }
                }
                "edit", "delete" -> {
                    val id = intent.getStringExtra("qaBookId")!!
                    val book = checkNotNull(app.libraryRepository.getBook(id))
                    require(book.metadata.title.startsWith("[QA Sync]")) { "Destructive QA commands are restricted to marked QA books" }
                    val result = if (action == "delete") app.bookDeleter.delete(id).toString() else {
                        val value = intent.getStringExtra("qaValue")!!
                        val draft = book.draft()
                        val updated = when (intent.getStringExtra("qaField")) {
                            "title" -> draft.copy(title = "[QA Sync] $value")
                            "authors" -> draft.copy(authors = listOf(value))
                            "description" -> draft.copy(description = value)
                            else -> error("Unsupported QA field")
                        }
                        app.bookEditor.updateMetadata(id, updated).toString()
                    }
                    buildJsonObject { put("result", result) }
                }
                "fault", "remote", "cleanup-qa-abandoned" -> {
                    require(app.syncService.activeProvider() == SyncProvider.GOOGLE_DRIVE_APPDATA)
                    val auth = app.googleDriveAuthService.authorize()
                    check(auth is GoogleDriveAuthorization.Authorized) { "Drive authorization required" }
                    val store = GoogleDriveAppDataRemoteLibraryStore(GoogleDriveApiClient(auth.accessToken),
                        deletionScope = "drive:" + app.syncSettingsRepository.googleDriveAccountName().orEmpty())
                    if (action == "cleanup-qa-abandoned") {
                        val requested = Json.parseToJsonElement(intent.getStringExtra("qaPaths")!!).jsonArray.map { it.jsonPrimitive.content }
                        require(requested.size <= 100 && requested.all { it.matches(Regex("foliore-v4-data-[0-9a-f-]{36}")) })
                        synchronized(SyncOperationCoordinator.remoteLock) {
                            val entries = store.listRootEntries()
                            val published = entries.filter { it.name.startsWith("foliore-v4-commit-") }.mapNotNull { marker ->
                                val digest = store.readText(marker.name).orEmpty()
                                if (!digest.matches(Regex("[0-9a-f]{64}"))) return@mapNotNull null
                                val recordPath = marker.name.replace("foliore-v4-commit-", "foliore-v4-record-")
                                val text = checkNotNull(store.readText(recordPath))
                                val actual = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
                                check(actual == digest)
                                text
                            }
                            check(requested.none { path -> published.any { "$path/" in it } }) { "Cannot remove referenced data" }
                            val existing = entries.filter { it.isDirectory && it.name in requested }.map { it.name }
                            val pool = java.util.concurrent.Executors.newFixedThreadPool(3)
                            try { existing.map { path -> pool.submit { store.deleteTree(path) } }.forEach { it.get() } }
                            finally { pool.shutdownNow() }
                            buildJsonObject { put("removedDirectories", JsonArray(existing.map(::JsonPrimitive))) }
                        }
                    } else if (action == "remote") {
                        val entries = store.listRootEntries()
                        val manifest = RemoteManifestJournal(store).read()?.let(Json::parseToJsonElement)
                        buildJsonObject {
                            put("entries", JsonArray(entries.map { buildJsonObject { put("name", it.name); put("directory", it.isDirectory) } }))
                            put("manifest", manifest ?: JsonNull)
                        }
                    } else {
                        val fault = FaultStore(store, intent.getStringExtra("qaFault")!!)
                        val engine = PortableLibrarySyncEngine(context, app.libraryRepository, DeviceIdProvider(context), app.aiCoverPromptRepository)
                        synchronized(SyncOperationCoordinator.remoteLock) { summary(engine.sync(fault, ::event)) }
                    }
                }
                else -> error("Unknown QA action")
            }
        }
        out.writeText(buildJsonObject {
            put("run", run); put("action", action); put("status", if (result.isSuccess) "PASS" else "ERROR")
            put("elapsedMs", SystemClock.elapsedRealtime() - start)
            put("appRxBytes", TrafficStats.getUidRxBytes(uid) - rx); put("appTxBytes", TrafficStats.getUidTxBytes(uid) - tx)
            put("pssKb", Debug.getPss())
            result.getOrNull()?.let { put("result", it) }
            result.exceptionOrNull()?.let { put("errorType", it.javaClass.simpleName); put("error", it.message) }
            put("events", JsonArray(synchronized(events) { events.toList() }))
        }.toString())
        Log.d("FolioreDeviceQA", "$run completed success=${result.isSuccess}")
    }

    private fun snapshot(app: AppContainer, hashes: Boolean) = buildJsonObject {
        val books = app.libraryRepository.getBooks()
        put("count", books.size); put("provider", app.syncService.activeProvider().name)
        put("autoSync", app.syncService.autoSyncEnabled()); put("wifiOnly", app.syncService.autoSyncWifiOnly())
        put("books", JsonArray(books.map { book -> buildJsonObject {
            val b = book.book; val m = book.metadata
            put("id", b.id); put("title", m.title); put("authors", JsonArray(m.authors.map(::JsonPrimitive)))
            put("language", m.language); put("publisher", m.publisher); put("publishedDate", m.publishedDate)
            put("seriesName", m.seriesName); put("seriesIndex", m.seriesIndex)
            put("identifiers", JsonObject(m.identifiers.mapValues { JsonPrimitive(it.value) }))
            put("coverPath", b.coverImagePath)
            put("description", m.description); put("revision", b.revision); put("syncStatus", b.syncStatus.name)
            put("updatedAt", b.updatedAt); put("storedHash", b.originalHashSha256); put("size", b.fileSizeBytes)
            put("originalExists", File(b.originalFilePath).isFile)
            if (hashes && File(b.originalFilePath).isFile) put("actualHash", File(b.originalFilePath).sha256())
        } }))
    }

    private fun drivePerformanceFixture(context: Context, app: AppContainer, cleanupOnly: Boolean): JsonObject {
        val auth = app.googleDriveAuthService.authorize()
        check(auth is GoogleDriveAuthorization.Authorized)
        val drive = GoogleDriveApiClient(auth.accessToken)
        val store = GoogleDriveAppDataRemoteLibraryStore(drive)
        if (cleanupOnly) {
            val candidates = drive.listAppDataFiles().filter {
                (it.name.startsWith("foliore-qa-performance-") || it.name in setOf("branch-0", "branch-1", "file-0.txt", "file-1.txt", "file-2.txt", "sentinel.txt", "recursive", "cascade")) &&
                    it.modifiedTime.orEmpty().startsWith("2026-09-11")
            }
            Log.d("FolioreDeviceQA", "Fixture leftovers=" + Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(DriveFileMetadata.serializer()), candidates))
            candidates.sortedBy { it.mimeType == GoogleDriveApiClient.FOLDER_MIME_TYPE }.forEach { drive.deleteFile(it.id) }
            check(drive.listAppDataFiles().none { item -> candidates.any { it.id == item.id } })
            return buildJsonObject { put("removedFixtureObjects", candidates.size) }
        }
        val prefix = "foliore-qa-performance-${UUID.randomUUID()}"
        val root = drive.createFolder(GoogleDriveApiClient.APP_DATA_ROOT_ID, prefix)
        val file = File(context.cacheDir, "$prefix.bin")
        val results = mutableListOf<JsonElement>()
        val created = mutableListOf(root)
        try {
            val sentinel = drive.uploadText(root.id, "sentinel.txt", "text/plain", "keep")
            created += sentinel
            fun timed(name: String, block: () -> Unit) {
                val start = SystemClock.elapsedRealtime(); val requests = drive.requestCount.get()
                block()
                results += buildJsonObject { put("name", name); put("ms", SystemClock.elapsedRealtime() - start)
                    put("requests", drive.requestCount.get() - requests) }
                Log.d("FolioreDeviceQA", results.last().toString())
            }
            fun populate(name: String): String {
                val folder = drive.createFolder(root.id, name)
                created += folder
                repeat(2) { branch ->
                    val child = drive.createFolder(folder.id, "branch-$branch")
                    created += child
                    repeat(3) { created += drive.uploadText(child.id, "file-$it.txt", "text/plain", "test $branch/$it") }
                }
                return folder.id
            }
            fun recursiveDelete(id: String) {
                drive.listChildren(id).forEach { child ->
                    if (child.mimeType == GoogleDriveApiClient.FOLDER_MIME_TYPE) recursiveDelete(child.id)
                    else drive.deleteFile(child.id)
                }
                drive.deleteFile(id)
            }
            val old = populate("recursive")
            timed("recursive-delete-9-items") { recursiveDelete(old) }
            val fast = populate("cascade")
            val descendants = drive.listChildren(fast).flatMap { child -> listOf(child.id) + drive.listChildren(child.id).map { it.id } }
            // Prime exactly the lookup caches used during real garbage collection.
            store.hasDirectory("$prefix/cascade/branch-0")
            timed("cascade-delete-9-items") { store.deleteTree("$prefix/cascade") }
            var remaining = drive.listAppDataFiles().map { it.id }.toSet()
            val visibilityStart = SystemClock.elapsedRealtime()
            repeat(15) {
                if ((descendants + fast).any { it in remaining }) {
                    val directRead = runCatching { drive.readText(descendants.last()) }.fold({ "readable" }, { (it as? GoogleDriveHttpException)?.statusCode?.toString() ?: it.javaClass.simpleName })
                    Log.d("FolioreDeviceQA", "Cascade remaining=${(descendants + fast).count { it in remaining }} directRead=$directRead waitMs=${SystemClock.elapsedRealtime() - visibilityStart}")
                    Thread.sleep(1000)
                    remaining = drive.listAppDataFiles().map { it.id }.toSet()
                }
            }
            results += buildJsonObject { put("name", "cascade-listing-convergence"); put("ms", SystemClock.elapsedRealtime() - visibilityStart); put("remaining", (descendants + fast).count { it in remaining }) }
            check((descendants + fast).none { it in remaining }) { "Cascade left ${(descendants + fast).count { it in remaining }} of 9 fixture objects" }
            check(sentinel.id in remaining && drive.readText(sentinel.id) == "keep") { "Cascade removed sibling sentinel" }
            check(!store.hasDirectory("$prefix/cascade/branch-0")) { "Deleted path remains in folder cache" }
            // Exercise multipart and resumable uploads; all bytes stay in an isolated QA folder.
            for (sizeMiB in listOf(1, 6)) {
                file.outputStream().use { output ->
                    val random = java.util.Random(42); val bytes = ByteArray(1024 * 1024)
                    repeat(sizeMiB) { random.nextBytes(bytes); output.write(bytes) }
                }
                val hash = file.sha256()
                var uploaded: DriveFileMetadata? = null
                timed("upload-$sizeMiB-MiB") { uploaded = drive.uploadFile(root.id, "$sizeMiB.bin", "application/octet-stream", file); created += uploaded!! }
                timed("server-verify-$sizeMiB-MiB") { check(uploaded!!.verifiesUpload(file.length(), hash)) { "Server omitted SHA256 for $sizeMiB MiB" } }
                timed("download-verify-$sizeMiB-MiB") {
                    drive.downloadFile(uploaded!!.id, file); check(file.sha256() == hash)
                }
            }
        } finally {
            file.delete()
            created.asReversed().forEach { item ->
                runCatching { drive.deleteFile(item.id) }.onFailure { Log.e("FolioreDeviceQA", "Fixture cleanup failed id=${item.id}", it) }
            }
        }
        check(drive.listChildren(GoogleDriveApiClient.APP_DATA_ROOT_ID).none { it.id == root.id })
        return buildJsonObject { put("measurements", JsonArray(results)); put("sentinelPreserved", true); put("fixtureRemoved", true) }
    }

    private fun driveBatchFixture(app: AppContainer): JsonObject {
        val auth = app.googleDriveAuthService.authorize()
        check(auth is GoogleDriveAuthorization.Authorized)
        val drive = GoogleDriveApiClient(auth.accessToken)
        val store = GoogleDriveAppDataRemoteLibraryStore(drive)
        val prefix = "foliore-qa-performance-${UUID.randomUUID()}"
        val root = drive.createFolder(GoogleDriveApiClient.APP_DATA_ROOT_ID, prefix)
        val created = mutableListOf(root.id)
        try {
            val sentinel = drive.uploadText(root.id, "sentinel.txt", "text/plain", "keep")
            created += sentinel.id
            val files = (0 until 8).map { drive.uploadText(root.id, "batch-$it.txt", "text/plain", "test").also { created += it.id } }
            check(store.readText("$prefix/batch-0.txt") == "test")
            drive.deleteFile(files.first().id)
            val start = SystemClock.elapsedRealtime(); val requests = drive.requestCount.get()
            store.deleteTrees(files.map { "$prefix/${it.name}" })
            val elapsed = SystemClock.elapsedRealtime() - start; val calls = drive.requestCount.get() - requests
            check(drive.listChildren(root.id).none { item -> files.any { it.id == item.id } }) { "Batch left undeleted fixture files" }
            check(drive.readText(sentinel.id) == "keep")
            return buildJsonObject { put("targets", 8); put("alreadyDeleted", 1); put("ms", elapsed)
                put("requests", calls); put("allDeleted", true); put("sentinelPreserved", true) }
        } finally {
            drive.deleteFiles(created.asReversed())
        }
    }
    private fun summary(result: ManualSyncResult) = buildJsonObject {
        put("type", result.javaClass.simpleName)
        if (result is ManualSyncResult.Synced) {
            put("uploaded", result.uploadedBooks); put("downloaded", result.downloadedBooks); put("conflicts", result.conflictedBooks)
        }
    }
    private fun LibraryBook.draft() = metadata.let { m -> BookMetadataDraft(m.title, m.subtitle, m.authors, m.sortAuthor, m.language,
        m.publisher, m.publishedDate, m.description, m.tags, m.isbn, m.identifiers, m.seriesName, m.seriesIndex) }

    private fun createBook(context: Context, app: AppContainer): String {
        val file = File(context.cacheDir, "qa-${UUID.randomUUID()}.epub")
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                fun put(path: String, text: String) { zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry() }
                put("mimetype", "application/epub+zip")
                put("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="OPS/book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
                put("OPS/book.opf", """<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">${UUID.randomUUID()}</dc:identifier><dc:title>[QA Sync] Baseline</dc:title><dc:creator>QA Author</dc:creator><dc:language>en</dc:language></metadata><manifest><item id="page" href="page.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="page"/></spine></package>""")
                put("OPS/page.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>QA Sync</title></head><body><p>Sync QA ${UUID.randomUUID()}</p></body></html>""")
            }
            return (runBlocking { app.epubImporter.importFile(file) } as EpubImportResult.Imported).bookId
        } finally { file.delete() }
    }

    private class FaultStore(private val store: RemoteLibraryStore, private val mode: String) : RemoteLibraryStore by store {
        private val fired = AtomicBoolean(false)
        override fun listRootEntries(): List<RemoteRootEntry> {
            if (mode == "quota" && fired.compareAndSet(false, true)) throw GoogleDriveHttpException(403, "storageQuotaExceeded (injected)")
            if (mode == "unavailable" && fired.compareAndSet(false, true)) throw GoogleDriveHttpException(503, "Injected provider outage")
            return store.listRootEntries()
        }
        override fun replaceFile(path: String, source: File, mimeType: String) {
            if (mode == "upload" && fired.compareAndSet(false, true)) {
                val partial = File.createTempFile("qa-partial", ".epub", source.parentFile)
                try {
                    partial.outputStream().use { output -> source.inputStream().use { input ->
                        val prefix = ByteArray(128)
                        val count = input.read(prefix)
                        if (count > 0) output.write(prefix, 0, count)
                    } }
                    store.replaceFile(path, partial, mimeType)
                    throw IOException("Injected interruption after a partial upload")
                } finally { partial.delete() }
            }
            store.replaceFile(path, source, mimeType)
        }
        override fun writeText(path: String, text: String, mimeType: String) {
            if (mode == "commit" && path.startsWith("foliore-v4-commit-") && fired.compareAndSet(false, true)) {
                store.writeText(path, "{", mimeType)
                throw IOException("Injected interrupted commit marker")
            }
            store.writeText(path, text, mimeType)
        }
        override fun copyToLocal(path: String, destination: File): File? {
            val result = store.copyToLocal(path, destination)
            if (mode == "download" && result != null && fired.compareAndSet(false, true)) {
                java.io.RandomAccessFile(destination, "rw").use { it.setLength(128) }
            }
            return result
        }
    }
}
