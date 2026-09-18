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
    private val gmailEmailSender: GmailEmailSender = GmailEmailSender(),
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

    fun sendToKindleGmail(bookId: String, account: String, recipient: String, token: String): KindleSendResult {
        return runCatching {
            val exported = exportPreparedEpub(bookId) as? ExportPreparedResult.Ready ?: return KindleSendResult.FAILED
            gmailEmailSender.send(token, account, recipient,
                appContext.getString(R.string.kindle_email_subject, exported.file.nameWithoutExtension),
                appContext.getString(R.string.kindle_email_body), exported.file)
        }.getOrDefault(KindleSendResult.FAILED)
    }

    suspend fun sendBatchToKindle(
        bookIds: List<String>,
        credentials: KindleEmailCredentials? = null,
        account: String? = null,
        recipient: String? = null,
        token: String? = null,
        onProgress: (Boolean, Int, Int) -> Unit,
    ): KindleBatchReport {
        val operationDir = File(appContext.cacheDir, "kindle-send-${java.util.UUID.randomUUID()}")
        check(operationDir.mkdirs())
        val names = mutableSetOf<String>()
        var sequence = 0
        try {
            return KindleBatchSender(if (credentials == null) KindleMessageLimits.GMAIL_BATCH else KindleMessageLimits.SMTP_BATCH).send(
                bookIds = bookIds,
                prepare = { bookId ->
                    val directory = File(operationDir, (++sequence).toString()).apply { mkdirs() }
                    val exported = exportPreparedEpub(bookId, directory) as? ExportPreparedResult.Ready
                    exported?.file?.let { file ->
                        var name = file.name
                        var suffix = 1
                        while (!names.add(name)) { name = "${file.nameWithoutExtension} (${++suffix}).epub" }
                        if (name == file.name) file else File(directory, name).also { check(file.renameTo(it)) }
                    }
                },
                deliver = { files ->
                    val subject = if (files.size == 1) appContext.getString(R.string.kindle_email_subject, files.single().nameWithoutExtension)
                        else appContext.getString(R.string.kindle_batch_subject, files.size)
                    val body = appContext.getString(R.string.kindle_email_body)
                    if (credentials != null) smtpEmailSender.sendEpubs(credentials, subject, body, files)
                    else gmailEmailSender.send(requireNotNull(token), requireNotNull(account), requireNotNull(recipient), subject, body, files)
                },
                onProgress = onProgress,
            )
        } finally { operationDir.deleteRecursively() }
    }

    private fun exportPreparedEpub(bookId: String, destinationDir: File? = null): ExportPreparedResult = synchronized(SyncOperationCoordinator.localDataLock) {
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
        val exportDir = destinationDir ?: File(appContext.filesDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, current.metadata.exportFileName())
        prepared.copyTo(exportFile, overwrite = true)

        libraryRepository.markExported(
            bookId = bookId,
            exportedAt = Clock.System.now().toString(),
        )

        val uri = if (destinationDir != null) android.net.Uri.fromFile(exportFile) else FileProvider.getUriForFile(
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
                val updatedOpf = EpubMetadataEditor.update(
                    opfXml = opfXml,
                    metadata = metadata,
                    coverItemId = COVER_ITEM_ID,
                    coverHref = coverHref,
                    needsCoverItem = parsed.coverPath == null && coverFile != null,
                    modifiedAt = kotlinx.datetime.Instant.fromEpochSeconds(Clock.System.now().epochSeconds).toString(),
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
