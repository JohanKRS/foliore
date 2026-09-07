package com.foliore.android.storage

import android.content.Context
import android.content.ClipData
import android.content.Intent
import androidx.core.content.FileProvider
import com.foliore.android.R
import com.foliore.shared.data.PortableSnapshots
import com.foliore.shared.domain.BookMetadata
import com.foliore.shared.domain.BookStatus
import com.foliore.shared.domain.LibraryBook
import com.foliore.shared.epub.EpubParser
import com.foliore.shared.library.LibraryRepository
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidPreparedEpubService(
    context: Context,
    private val libraryRepository: LibraryRepository,
    private val deviceIdProvider: DeviceIdProvider,
    private val parser: EpubParser = EpubParser(),
    private val smtpEmailSender: SmtpEmailSender = SmtpEmailSender(),
) {
    private val appContext = context.applicationContext
    private val json = Json { prettyPrint = true }

    fun prepare(bookId: String): PreparedEpubResult = synchronized(SyncOperationCoordinator.localDataLock) {
        return runCatching {
            val current = libraryRepository.getBook(bookId) ?: return PreparedEpubResult.NotFound
            val original = File(current.book.originalFilePath)
            if (!original.exists()) return PreparedEpubResult.Failed

            val bookDir = original.parentFile ?: return PreparedEpubResult.Failed
            val prepared = File(bookDir, "prepared.epub")
            val temp = File(bookDir, "prepared.tmp.epub")

            writePreparedEpub(
                source = original,
                destination = temp,
                metadata = current.metadata,
                coverFile = current.book.coverImagePath?.let(::File)?.takeIf { it.exists() },
            )
            java.nio.file.Files.move(temp.toPath(), prepared.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)

            val updated = libraryRepository.markPrepared(
                bookId = bookId,
                preparedFilePath = prepared.absolutePath,
                updatedAt = Clock.System.now().toString(),
                updatedByDeviceId = deviceIdProvider.getDeviceId(),
            ) ?: return PreparedEpubResult.NotFound

            writePortableMetadata(updated, changeType = "prepared_epub")
            PreparedEpubResult.Prepared(bookId)
        }.getOrDefault(PreparedEpubResult.Failed)
    }

    fun share(bookId: String): PreparedEpubResult {
        return runCatching {
            val exported = when (val exportResult = exportPreparedEpub(bookId)) {
                ExportPreparedResult.Failed -> return PreparedEpubResult.Failed
                ExportPreparedResult.NotFound -> return PreparedEpubResult.NotFound
                is ExportPreparedResult.Ready -> exportResult
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = EPUB_MIME_TYPE
                clipData = ClipData.newUri(appContext.contentResolver, exported.file.name, exported.uri)
                putExtra(Intent.EXTRA_STREAM, exported.uri)
                putExtra(Intent.EXTRA_TITLE, exported.file.name)
                putExtra(Intent.EXTRA_SUBJECT, exported.file.name)
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(EPUB_MIME_TYPE, COMPATIBLE_SHARE_MIME_TYPE))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            PreparedEpubResult.ShareIntentReady(shareIntent)
        }.getOrDefault(PreparedEpubResult.Failed)
    }

    fun sendToKindleEmail(
        bookId: String,
        credentials: KindleEmailCredentials,
    ): PreparedEpubResult {
        return runCatching {
            val exported = when (val exportResult = exportPreparedEpub(bookId)) {
                ExportPreparedResult.Failed -> return PreparedEpubResult.Failed
                ExportPreparedResult.NotFound -> return PreparedEpubResult.NotFound
                is ExportPreparedResult.Ready -> exportResult
            }
            val sent = smtpEmailSender.sendEpub(
                credentials = credentials,
                subject = appContext.getString(R.string.kindle_email_subject, exported.file.nameWithoutExtension),
                body = appContext.getString(R.string.kindle_email_body),
                attachment = exported.file,
            )
            if (sent) PreparedEpubResult.Sent else PreparedEpubResult.Failed
        }.getOrDefault(PreparedEpubResult.Failed)
    }

    private fun exportPreparedEpub(bookId: String): ExportPreparedResult = synchronized(SyncOperationCoordinator.localDataLock) {
        var current = libraryRepository.getBook(bookId) ?: return ExportPreparedResult.NotFound
        val preparedPath = current.book.preparedFilePath
        val needsPrepare = preparedPath == null ||
            current.book.status == BookStatus.OUTDATED_PREPARED ||
            !File(preparedPath).exists()

        if (needsPrepare) {
            when (prepare(bookId)) {
                is PreparedEpubResult.Prepared -> Unit
                PreparedEpubResult.NotFound -> return ExportPreparedResult.NotFound
                else -> return ExportPreparedResult.Failed
            }
            current = libraryRepository.getBook(bookId) ?: return ExportPreparedResult.NotFound
        }

        val prepared = current.book.preparedFilePath?.let(::File)?.takeIf { it.exists() }
            ?: return ExportPreparedResult.Failed
        val exportDir = File(appContext.filesDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, current.metadata.exportFileName())
        prepared.copyTo(exportFile, overwrite = true)

        libraryRepository.markExported(
            bookId = bookId,
            exportedAt = Clock.System.now().toString(),
        )

        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            exportFile,
        )
        return ExportPreparedResult.Ready(file = exportFile, uri = uri)
    }

    private fun writePreparedEpub(
        source: File,
        destination: File,
        metadata: BookMetadata,
        coverFile: File?,
    ) {
        if (destination.exists()) destination.delete()

        ZipFile(source).use { zip ->
            StorageLimits.inspect(zip)
            val budget = ReadBudget(StorageLimits.EXPANDED_BYTES)
            AndroidZipEpubArchive(source).use { archive ->
                val parsed = parser.parse(archive)
                if (!parsed.isReadable) error("Unreadable EPUB")

                val opfEntryName = parsed.technicalInfo.opfPath
                val opfXml = archive.readText(opfEntryName) ?: error("Missing OPF")
                val previousCoverEntry = parsed.coverPath
                val coverEntryName = if (coverFile != null) defaultCoverPath(opfEntryName).removeSuffix(".jpg") + "-${java.util.UUID.randomUUID()}.jpg"
                    else previousCoverEntry ?: defaultCoverPath(opfEntryName)
                val coverHref = relativeHref(opfEntryName, coverEntryName)
                val updatedOpf = updateOpf(
                    opfXml = opfXml,
                    metadata = metadata,
                    coverItemId = COVER_ITEM_ID,
                    coverHref = coverHref,
                    needsCoverItem = parsed.coverPath == null && coverFile != null,
                )
                val coverBytes = coverFile?.inputStream()?.use { it.readBounded(StorageLimits.ENTRY_BYTES) }
                var wroteCover = false

                ZipOutputStream(FileOutputStream(destination)).use { output ->
                    writeMimetypeFirst(zip, output)

                    zip.entries().asSequence().forEach { entry ->
                        if (entry.name == "mimetype" || entry.isDirectory || (coverFile != null && entry.name == previousCoverEntry)) return@forEach

                        val data = when (entry.name) {
                            opfEntryName -> updatedOpf.toByteArray(Charsets.UTF_8)
                            coverEntryName -> {
                                wroteCover = coverBytes != null
                                coverBytes ?: zip.getInputStream(entry).use { it.readBounded(StorageLimits.ENTRY_BYTES, budget) }
                            }
                            else -> zip.getInputStream(entry).use { it.readBounded(StorageLimits.ENTRY_BYTES, budget) }
                        }
                        val rewritten = if (coverFile != null && previousCoverEntry != null &&
                            entry.name.substringAfterLast('.').lowercase() in setOf("opf", "xhtml", "html", "htm", "svg", "xml", "ncx", "css")) {
                            EpubCoverReferences.rewrite(data.toString(Charsets.UTF_8), entry.name, previousCoverEntry, coverEntryName).toByteArray(Charsets.UTF_8)
                        } else data
                        output.writeDeflated(entry.name, rewritten)
                    }

                    if (coverBytes != null && !wroteCover) {
                        output.writeDeflated(coverEntryName, coverBytes)
                    }
                }
            }
        }
    }

    private fun writeMimetypeFirst(zip: ZipFile, output: ZipOutputStream) {
        val budget = ReadBudget(128)
        val mimetypeBytes = zip.getEntry("mimetype")?.let { entry ->
            zip.getInputStream(entry).use { it.readBounded(StorageLimits.ENTRY_BYTES, budget) }
        } ?: EPUB_MIME_TYPE.toByteArray(Charsets.UTF_8)

        val crc = CRC32().apply { update(mimetypeBytes) }
        val entry = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED
            size = mimetypeBytes.size.toLong()
            compressedSize = mimetypeBytes.size.toLong()
            this.crc = crc.value
        }
        output.putNextEntry(entry)
        output.write(mimetypeBytes)
        output.closeEntry()
    }

    private fun ZipOutputStream.writeDeflated(name: String, data: ByteArray) {
        putNextEntry(ZipEntry(name).apply { method = ZipEntry.DEFLATED })
        write(data)
        closeEntry()
    }

    private fun updateOpf(
        opfXml: String,
        metadata: BookMetadata,
        coverItemId: String,
        coverHref: String,
        needsCoverItem: Boolean,
    ): String {
        var updated = opfXml.ensureDcNamespace()
        updated = updated.upsertSingleDcTag("title", metadata.title)
        updated = updated.replaceRepeatedDcTag("creator", metadata.authors.ifEmpty { listOf(metadata.title) })
        updated = updated.upsertSingleDcTag("language", metadata.language ?: "und")
        updated = updated.upsertOptionalDcTag("publisher", metadata.publisher)
        updated = updated.upsertOptionalDcTag("date", metadata.publishedDate)
        updated = updated.upsertOptionalDcTag("description", metadata.description)
        updated = updated.upsertModifiedMeta()

        if (needsCoverItem) {
            updated = updated.addCoverManifestItem(coverItemId, coverHref)
            updated = updated.addLegacyCoverMeta(coverItemId)
        }

        return updated
    }

    private fun String.ensureDcNamespace(): String {
        if (contains("xmlns:dc=")) return this
        return replaceFirst(
            Regex("<package\\b", RegexOption.IGNORE_CASE),
            "<package xmlns:dc=\"http://purl.org/dc/elements/1.1/\"",
        )
    }

    private fun String.upsertSingleDcTag(localName: String, value: String): String {
        val escaped = value.escapeXml()
        val regex = Regex(
            "<(?:dc:)?$localName\\b[^>]*>.*?</(?:dc:)?$localName>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val replacement = "<dc:$localName>$escaped</dc:$localName>"
        return if (regex.containsMatchIn(this)) {
            replaceFirst(regex, Regex.escapeReplacement(replacement))
        } else {
            insertIntoMetadata(replacement)
        }
    }

    private fun String.upsertOptionalDcTag(localName: String, value: String?): String {
        val regex = Regex(
            "<(?:dc:)?$localName\\b[^>]*>.*?</(?:dc:)?$localName>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val clean = value?.trim()
        if (clean.isNullOrEmpty()) return replace(regex, "")
        return upsertSingleDcTag(localName, clean)
    }

    private fun String.replaceRepeatedDcTag(localName: String, values: List<String>): String {
        val regex = Regex(
            "<(?:dc:)?$localName\\b[^>]*>.*?</(?:dc:)?$localName>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val replacements = values
            .map { "<dc:$localName>${it.escapeXml()}</dc:$localName>" }
            .joinToString("\n")
        val withoutExisting = replace(regex, "")
        return withoutExisting.insertIntoMetadata(replacements)
    }

    private fun String.upsertModifiedMeta(): String {
        val now = Clock.System.now().toString()
        val regex = Regex(
            "<meta\\s+property=[\"']dcterms:modified[\"'][^>]*>.*?</meta>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val replacement = "<meta property=\"dcterms:modified\">$now</meta>"
        return if (regex.containsMatchIn(this)) {
            replaceFirst(regex, Regex.escapeReplacement(replacement))
        } else {
            insertIntoMetadata(replacement)
        }
    }

    private fun String.addCoverManifestItem(itemId: String, href: String): String {
        if (contains("id=\"$itemId\"") || contains("id='$itemId'")) return this
        val item = "<item id=\"$itemId\" href=\"${href.escapeXml()}\" media-type=\"image/jpeg\" properties=\"cover-image\"/>"
        return replaceFirst(
            Regex("</manifest>", RegexOption.IGNORE_CASE),
            Regex.escapeReplacement("$item\n</manifest>"),
        )
    }

    private fun String.addLegacyCoverMeta(itemId: String): String {
        if (contains("name=\"cover\"", ignoreCase = true) || contains("name='cover'", ignoreCase = true)) {
            return this
        }
        return insertIntoMetadata("<meta name=\"cover\" content=\"$itemId\"/>")
    }

    private fun String.insertIntoMetadata(fragment: String): String {
        return replaceFirst(
            Regex("</metadata>", RegexOption.IGNORE_CASE),
            Regex.escapeReplacement("$fragment\n</metadata>"),
        )
    }

    private fun String.escapeXml(): String {
        return replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun defaultCoverPath(opfPath: String): String {
        val base = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
        return if (base.isBlank()) "foliore-cover.jpg" else "$base/foliore-cover.jpg"
    }

    private fun relativeHref(opfPath: String, entryPath: String): String {
        return EpubCoverReferences.relative(opfPath, entryPath)
    }

    private fun BookMetadata.exportFileName(): String {
        val author = authors.firstOrNull()?.sanitizeFilePart()
        val title = title.sanitizeFilePart().ifBlank { "Foliore EPUB" }
        val stem = if (author.isNullOrBlank()) title else "$author - $title"
        return "$stem.epub"
    }

    private fun String.sanitizeFilePart(): String {
        return trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .take(96)
            .trim()
    }

    private fun writePortableMetadata(
        libraryBook: LibraryBook,
        changeType: String,
    ) {
        val bookDir = File(libraryBook.book.originalFilePath).parentFile ?: return
        File(bookDir, "metadata.json").writeText(
            json.encodeToString(
                PortableSnapshots.metadata(
                    book = libraryBook.book,
                    metadata = libraryBook.metadata,
                ),
            ),
            Charsets.UTF_8,
        )
        File(bookDir, "changes.jsonl").appendText(
            """{"type":"$changeType","at":"${libraryBook.book.updatedAt}","bookId":"${libraryBook.book.id}","revision":${libraryBook.book.revision}}""" + "\n",
            Charsets.UTF_8,
        )
    }

    private companion object {
        const val EPUB_MIME_TYPE = "application/epub+zip"
        const val COMPATIBLE_SHARE_MIME_TYPE = "application/octet-stream"
        const val COVER_ITEM_ID = "foliore-cover-image"
    }

    private sealed interface ExportPreparedResult {
        data class Ready(
            val file: File,
            val uri: android.net.Uri,
        ) : ExportPreparedResult

        data object NotFound : ExportPreparedResult
        data object Failed : ExportPreparedResult
    }
}
