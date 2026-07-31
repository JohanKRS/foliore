package com.foliore.android.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.foliore.shared.data.PortableSnapshots
import com.foliore.shared.domain.Book
import com.foliore.shared.domain.BookMetadata
import com.foliore.shared.domain.BookStatus
import com.foliore.shared.domain.EpubTechnicalInfo
import com.foliore.shared.domain.ImportedBookRecord
import com.foliore.shared.domain.LibraryBook
import com.foliore.shared.domain.MetadataSource
import com.foliore.shared.domain.SyncStatus
import com.foliore.shared.epub.EpubParser
import com.foliore.shared.library.LibraryRepository
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidEpubImporter(
    context: Context,
    private val libraryRepository: LibraryRepository,
    private val deviceIdProvider: DeviceIdProvider,
    private val parser: EpubParser = EpubParser(),
    private val coverImageWriter: CoverImageWriter = CoverImageWriter(),
) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver
    private val json = Json { prettyPrint = true }

    suspend fun import(uri: Uri): EpubImportResult = withContext(Dispatchers.IO) {
        val displayName = displayName(uri)
        val mimeType = contentResolver.getType(uri)

        if (!isLikelyEpub(displayName, mimeType)) {
            return@withContext EpubImportResult.Failed(ImportFailureReason.NOT_EPUB)
        }

        val copied = runCatching { copyToTemp(uri) }.getOrElse {
            return@withContext EpubImportResult.Failed(ImportFailureReason.CANNOT_COPY)
        }

        inspectAndImport(displayName, copied)
    }

    suspend fun importFile(source: File): EpubImportResult = withContext(Dispatchers.IO) {
        val displayName = source.name
        if (!isLikelyEpub(displayName, mimeType = null)) {
            return@withContext EpubImportResult.Failed(ImportFailureReason.NOT_EPUB)
        }

        val copied = runCatching { copyToTemp(source) }.getOrElse {
            return@withContext EpubImportResult.Failed(ImportFailureReason.CANNOT_COPY)
        }

        inspectAndImport(displayName, copied)
    }

    fun commit(pendingImport: PendingEpubImport): EpubImportResult {
        val copied = CopiedEpub(
            file = File(pendingImport.tempFilePath),
            sha256 = pendingImport.sha256,
        )
        if (!copied.file.isPendingImportFile() || !copied.file.exists()) {
            return EpubImportResult.Failed(ImportFailureReason.CANNOT_COPY)
        }

        val exactDuplicateBookId = libraryRepository.findBookIdByHash(copied.sha256)
        if (exactDuplicateBookId != null) {
            discard(pendingImport)
            return EpubImportResult.Duplicate(exactDuplicateBookId)
        }

        return storeCopied(
            displayName = pendingImport.displayName,
            copied = copied,
            contentFingerprint = pendingImport.contentFingerprint,
        )
    }

    fun discard(pendingImport: PendingEpubImport) {
        File(pendingImport.tempFilePath)
            .takeIf { it.isPendingImportFile() }
            ?.delete()
    }

    private fun inspectAndImport(
        displayName: String,
        copied: CopiedEpub,
    ): EpubImportResult {
        val duplicateBookId = libraryRepository.findBookIdByHash(copied.sha256)
        if (duplicateBookId != null) {
            copied.file.delete()
            return EpubImportResult.Duplicate(duplicateBookId)
        }

        val parsed = runCatching {
            AndroidZipEpubArchive(copied.file).use(parser::parse)
        }.getOrNull()
        val contentFingerprint = parsed
            ?.takeIf { it.isReadable }
            ?.let { parseResult ->
                EpubContentFingerprint.calculate(
                    file = copied.file,
                    opfPath = parseResult.technicalInfo.opfPath,
                    coverPath = parseResult.coverPath,
                    navPath = parseResult.technicalInfo.navPath,
                    ncxPath = parseResult.technicalInfo.ncxPath,
                )
            }
        val contentDuplicate = contentFingerprint?.let(::findBookByContentFingerprint)
        if (contentDuplicate != null) {
            val incomingTitle = parsed.metadata.title
                ?.takeIf { it.isNotBlank() }
                ?: displayName.titleFromFileName(contentDuplicate.book.id)
            return EpubImportResult.NeedsReview(
                PendingEpubImport(
                    token = UUID.randomUUID().toString(),
                    existingBookId = contentDuplicate.book.id,
                    existingTitle = contentDuplicate.metadata.title,
                    existingAuthors = contentDuplicate.metadata.authors,
                    incomingTitle = incomingTitle,
                    incomingAuthors = parsed.metadata.authors.filter { it.isNotBlank() },
                    displayName = displayName,
                    tempFilePath = copied.file.absolutePath,
                    sha256 = copied.sha256,
                    contentFingerprint = contentFingerprint,
                ),
            )
        }

        return storeCopied(displayName, copied, contentFingerprint)
    }

    private fun storeCopied(
        displayName: String,
        copied: CopiedEpub,
        contentFingerprint: String?,
    ): EpubImportResult {

        val bookId = UUID.randomUUID().toString()
        val bookDir = File(appContext.filesDir, "books/$bookId").apply { mkdirs() }
        val originalFile = File(bookDir, "original.epub")

        return try {
            copied.file.copyTo(originalFile, overwrite = true)
            copied.file.delete()

            val parseResult = AndroidZipEpubArchive(originalFile).use { archive ->
                val parsed = parser.parse(archive)
                val coverBytes = parsed.coverPath?.let { archive.readBytes(it) }
                val coverPaths = coverBytes?.let { coverImageWriter.writeCover(bookDir, it) }
                ParsedWithCover(parsed, coverPaths)
            }

            val now = Clock.System.now().toString()
            val parsedMetadata = parseResult.parsed.metadata
            val parsedTechnical = parseResult.parsed.technicalInfo
            val title = parsedMetadata.title?.takeIf { it.isNotBlank() } ?: displayName.titleFromFileName(bookId)
            val authors = parsedMetadata.authors.filter { it.isNotBlank() }
            val status = when {
                parsedTechnical.errors.isNotEmpty() -> BookStatus.ERROR
                parseResult.coverPaths == null -> BookStatus.NEEDS_REVIEW
                else -> BookStatus.IMPORTED
            }

            val book = Book(
                id = bookId,
                originalFilePath = originalFile.absolutePath,
                preparedFilePath = null,
                coverImagePath = parseResult.coverPaths?.coverImagePath,
                coverThumbPath = parseResult.coverPaths?.coverThumbPath,
                originalHashSha256 = copied.sha256,
                fileSizeBytes = originalFile.length(),
                importedAt = now,
                updatedAt = now,
                lastPreparedAt = null,
                lastExportedAt = null,
                lastSyncedAt = null,
                status = status,
                syncStatus = SyncStatus.LOCAL_ONLY,
                revision = 1,
                updatedByDeviceId = deviceIdProvider.getDeviceId(),
                technicalStatus = parsedTechnical.errors.takeIf { it.isNotEmpty() }?.joinToString("; "),
            )
            val metadata = BookMetadata(
                bookId = bookId,
                title = title,
                subtitle = null,
                authors = authors,
                sortAuthor = authors.firstOrNull(),
                language = parsedMetadata.language,
                publisher = parsedMetadata.publisher,
                publishedDate = parsedMetadata.publishedDate,
                description = parsedMetadata.description,
                tags = emptyList(),
                isbn = parsedMetadata.isbn,
                identifiers = parsedMetadata.identifiers.ifEmpty {
                    parsedMetadata.identifier?.let { mapOf("package" to it) }.orEmpty()
                },
                seriesName = null,
                seriesIndex = null,
                source = MetadataSource.EPUB,
            )
            val technicalInfo = EpubTechnicalInfo(
                bookId = bookId,
                epubVersion = parsedTechnical.epubVersion,
                opfPath = parsedTechnical.opfPath,
                navPath = parsedTechnical.navPath,
                ncxPath = parsedTechnical.ncxPath,
                coverHref = parsedTechnical.coverHref,
                packageIdentifier = parsedTechnical.packageIdentifier,
                hasNav = parsedTechnical.hasNav,
                hasNcx = parsedTechnical.hasNcx,
                manifestItemCount = parsedTechnical.manifestItemCount,
                spineItemCount = parsedTechnical.spineItemCount,
                warnings = parsedTechnical.warnings,
                errors = parsedTechnical.errors,
            )

            writePortableSnapshots(bookDir, book, metadata, technicalInfo)
            contentFingerprint?.let { fingerprint ->
                bookDir.resolve(CONTENT_FINGERPRINT_FILE).writeText(fingerprint, Charsets.UTF_8)
            }
            libraryRepository.insertImportedBook(ImportedBookRecord(book, metadata, technicalInfo))

            EpubImportResult.Imported(bookId)
        } catch (_: Exception) {
            copied.file.delete()
            bookDir.deleteRecursively()
            EpubImportResult.Failed(ImportFailureReason.CANNOT_OPEN)
        }
    }

    private fun findBookByContentFingerprint(fingerprint: String): LibraryBook? {
        return libraryRepository.getBooks().firstOrNull { book ->
            contentFingerprint(book) == fingerprint
        }
    }

    private fun contentFingerprint(book: LibraryBook): String? {
        val originalFile = File(book.book.originalFilePath).takeIf { it.isFile } ?: return null
        val cacheFile = originalFile.parentFile?.resolve(CONTENT_FINGERPRINT_FILE)
        cacheFile?.takeIf { it.isFile }?.readText(Charsets.UTF_8)?.trim()?.let { cached ->
            if (cached.length == SHA_256_HEX_LENGTH) return cached
        }

        val parsed = runCatching {
            AndroidZipEpubArchive(originalFile).use(parser::parse)
        }.getOrNull()?.takeIf { it.isReadable } ?: return null
        val calculated = EpubContentFingerprint.calculate(
            file = originalFile,
            opfPath = parsed.technicalInfo.opfPath,
            coverPath = parsed.coverPath,
            navPath = parsed.technicalInfo.navPath,
            ncxPath = parsed.technicalInfo.ncxPath,
        ) ?: return null
        runCatching { cacheFile?.writeText(calculated, Charsets.UTF_8) }
        return calculated
    }

    private fun copyToTemp(uri: Uri): CopiedEpub {
        val input = contentResolver.openInputStream(uri) ?: error("Cannot open URI")
        return copyToTemp(input)
    }

    private fun copyToTemp(source: File): CopiedEpub {
        return copyToTemp(source.inputStream())
    }

    private fun copyToTemp(input: InputStream): CopiedEpub {
        val importsDir = File(appContext.cacheDir, "imports").apply { mkdirs() }
        val tempFile = File(importsDir, "${UUID.randomUUID()}.epub")
        val digest = MessageDigest.getInstance("SHA-256")
        var bytesCopied = 0L

        input.use { source ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = source.read(buffer)
                    if (read == -1) break
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                    bytesCopied += read
                }
            }
        }

        if (bytesCopied == 0L) {
            tempFile.delete()
            error("Empty EPUB")
        }

        return CopiedEpub(
            file = tempFile,
            sha256 = digest.digest().toHexString(),
        )
    }

    private fun writePortableSnapshots(
        bookDir: File,
        book: Book,
        metadata: BookMetadata,
        technicalInfo: EpubTechnicalInfo,
    ) {
        File(bookDir, "metadata.json").writeText(
            json.encodeToString(PortableSnapshots.metadata(book, metadata)),
            Charsets.UTF_8,
        )
        File(bookDir, "technical.json").writeText(
            json.encodeToString(PortableSnapshots.technical(technicalInfo)),
            Charsets.UTF_8,
        )
        File(bookDir, "changes.jsonl").appendText(
            """{"type":"imported","at":"${book.importedAt}","bookId":"${book.id}"}""" + "\n",
            Charsets.UTF_8,
        )
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(index)
                }
            }
        }
        return uri.lastPathSegment ?: "book.epub"
    }

    private fun isLikelyEpub(displayName: String, mimeType: String?): Boolean {
        return displayName.endsWith(".epub", ignoreCase = true) || mimeType == "application/epub+zip"
    }

    private fun String.titleFromFileName(bookId: String): String {
        return substringAfterLast('/')
            .removeSuffix(".epub")
            .removeSuffix(".EPUB")
            .ifBlank { bookId }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private data class CopiedEpub(
        val file: File,
        val sha256: String,
    )

    private data class ParsedWithCover(
        val parsed: com.foliore.shared.epub.EpubParseResult,
        val coverPaths: CoverImagePaths?,
    )

    private fun File.isPendingImportFile(): Boolean {
        val importsDir = File(appContext.cacheDir, "imports").canonicalFile
        val candidate = runCatching { canonicalFile }.getOrNull() ?: return false
        return candidate.parentFile == importsDir && candidate.extension.equals("epub", ignoreCase = true)
    }

    private companion object {
        const val CONTENT_FINGERPRINT_FILE = ".content-fingerprint-v1"
        const val SHA_256_HEX_LENGTH = 64
    }
}
