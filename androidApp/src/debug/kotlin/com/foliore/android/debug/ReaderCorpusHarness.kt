package com.foliore.android.debug

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import android.os.SystemClock
import com.foliore.android.reader.EpubPublication
import com.foliore.android.storage.*
import com.foliore.shared.db.DatabaseDriverFactory
import com.foliore.shared.epub.EpubParser
import com.foliore.shared.library.LibraryRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

/** Optional local corpus QA. Each input gets a disposable database; the user's library is untouched. */
internal object ReaderCorpusHarness {
    fun run(context: Context) {
        val output = File(context.cacheDir, "reader-corpus").apply { mkdirs() }
        val report = File(output, "result.json")
        report.writeText("{\"status\":\"RUNNING\"}")
        val inputs = File(context.filesDir, "reader-corpus").listFiles().orEmpty()
            .filter { it.isFile && EbookFormat.from(it.name, null) != null }.sortedBy { it.name }
        require(inputs.isNotEmpty()) { "Copy EPUB/MOBI/AZW3 inputs into files/reader-corpus first" }
        val results = inputs.map { input ->
            val start = SystemClock.elapsedRealtime()
            val root = File(output, "work-${UUID.randomUUID()}")
            val isolated = CorpusContext(context, root)
            val driver = DatabaseDriverFactory(isolated).createDriver()
            var importMs = 0L
            var prepareMs = 0L
            val details = runCatching {
                val repository = LibraryRepository(driver)
                val device = DeviceIdProvider(isolated)
                val importer = AndroidEpubImporter(isolated, repository, device)
                val inputHash = input.sha256()
                val imported = runBlocking { importer.importFile(input) }
                check(imported is EpubImportResult.Imported) { "Import: $imported" }
                importMs = SystemClock.elapsedRealtime() - start
                val book = checkNotNull(repository.getBook(imported.bookId))
                check(book.metadata.title.isNotBlank())
                val original = File(book.book.originalFilePath)
                check(original.sha256() == book.book.originalHashSha256)
                val originals = AndroidOriginalFilesService(isolated, repository).list(imported.bookId)
                check(originals.size == 1)
                val archive = OriginalFileArchive(File(isolated.filesDir, OriginalFileArchive.DIRECTORY))
                check(archive.file(imported.bookId, originals.single().id)!!.sha256() == inputHash)
                val destination = File(output, input.name).apply { mkdirs() }
                original.copyTo(File(destination, "converted.epub"), true)
                var coverWidth = 0
                var coverHeight = 0
                book.book.coverImagePath?.let { path ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(path, bounds)
                    check(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid cover" }
                    coverWidth = bounds.outWidth; coverHeight = bounds.outHeight
                    File(path).copyTo(File(destination, "cover.jpg"), true)
                }
                val prepareStart = SystemClock.elapsedRealtime()
                val preparedResult = AndroidPreparedEpubService(isolated, repository, device).prepare(imported.bookId)
                check(preparedResult is PreparedEpubResult.Prepared) { "Prepare: $preparedResult" }
                prepareMs = SystemClock.elapsedRealtime() - prepareStart
                val prepared = File(checkNotNull(repository.getBook(imported.bookId)!!.book.preparedFilePath))
                prepared.copyTo(File(destination, "prepared.epub"), true)
                AndroidZipEpubArchive(prepared).use { check(EpubParser().parse(it).isReadable) }
                val sections = EpubPublication.open(prepared).use { publication ->
                    publication.sections.mapIndexed { index, section ->
                        val resource = publication.resource(section.href)
                        check(resource != null && resource.bytes.isNotEmpty()) { "Missing section ${section.href}" }
                        val text = publication.text(index)
                        buildJsonObject {
                            put("href", section.href); put("title", section.title)
                            put("characters", text.length); put("replacementCharacters", text.count { it == '\uFFFD' })
                            put("fixed", section.fixedLayout)
                        }
                    }
                }
                check(runBlocking { importer.importFile(input) } is EpubImportResult.Duplicate)
                check(repository.bookCount() == 1)
                check(input.sha256() == inputHash)
                check(File(isolated.cacheDir, "imports").listFiles().orEmpty().isEmpty())
                buildJsonObject {
                    put("title", book.metadata.title); put("language", book.metadata.language)
                    put("inputSha256", inputHash); put("convertedSha256", original.sha256())
                    put("inputBytes", input.length()); put("epubBytes", original.length())
                    put("coverWidth", coverWidth); put("coverHeight", coverHeight)
                    put("sections", JsonArray(sections))
                }
            }
            driver.close()
            root.deleteRecursively()
            buildJsonObject {
                put("file", input.name); put("status", if (details.isSuccess) "PASS" else "FAIL")
                put("importMs", importMs); put("prepareMs", prepareMs)
                put("elapsedMs", SystemClock.elapsedRealtime() - start)
                details.getOrNull()?.let { put("result", it) }
                details.exceptionOrNull()?.let { put("error", it.stackTraceToString()) }
            }.also { android.util.Log.i("FolioreCorpusQA", "${input.name}: ${it["status"]}") }
        }
        report.writeText(buildJsonObject {
            put("status", if (results.all { it["status"]?.jsonPrimitive?.content == "PASS" }) "PASS" else "FAIL")
            put("files", JsonArray(results))
        }.toString())
    }

    private class CorpusContext(base: Context, private val root: File) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        override fun getNoBackupFilesDir() = File(root, "no-backup").apply { mkdirs() }
        override fun getDatabasePath(name: String) = File(root, "databases/$name").apply { parentFile!!.mkdirs() }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = super.getSharedPreferences("qa-corpus-${root.name}-$name", mode)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)
    }
}
