package com.foliore.android.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import com.foliore.shared.data.PortableSnapshots
import com.foliore.shared.domain.BatchMetadataPatch
import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.domain.LibraryBook
import com.foliore.shared.library.LibraryRepository
import com.foliore.shared.epub.EpubParser
import java.io.File
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidBookEditor(
    context: Context,
    private val libraryRepository: LibraryRepository,
    private val deviceIdProvider: DeviceIdProvider,
    private val coverImageWriter: CoverImageWriter = CoverImageWriter(),
    private val epubParser: EpubParser = EpubParser(),
) {
    private val appContext = context.applicationContext
    private val json = Json { prettyPrint = true }

    fun updateMetadata(bookId: String, draft: BookMetadataDraft): BookEditResult {
        if (!draft.isValid()) return BookEditResult.InvalidTitle

        return runCatching {
            val updated = libraryRepository.updateMetadata(
                bookId = bookId,
                draft = draft,
                updatedAt = Clock.System.now().toString(),
                updatedByDeviceId = deviceIdProvider.getDeviceId(),
            ) ?: return BookEditResult.NotFound

            writePortableMetadata(updated, changeType = "metadata_updated")
            BookEditResult.Success(bookId)
        }.getOrDefault(BookEditResult.Failed)
    }

    fun updateMetadataBatch(
        bookIds: List<String>,
        patch: BatchMetadataPatch,
    ): BatchBookEditResult {
        if (bookIds.isEmpty()) return BatchBookEditResult.EmptySelection
        if (!patch.hasChanges()) return BatchBookEditResult.NoChanges

        return runCatching {
            val updatedBooks = libraryRepository.updateMetadataBatch(
                bookIds = bookIds,
                patch = patch,
                updatedAt = Clock.System.now().toString(),
                updatedByDeviceId = deviceIdProvider.getDeviceId(),
            )
            if (updatedBooks.isEmpty()) {
                return BatchBookEditResult.Failed
            }

            updatedBooks.forEach { updated ->
                writePortableMetadata(updated, changeType = "metadata_batch_updated")
            }
            BatchBookEditResult.Success(updatedBooks.size)
        }.getOrDefault(BatchBookEditResult.Failed)
    }

    fun replaceCover(bookId: String, imageUri: Uri): BookEditResult {
        return runCatching {
            val current = libraryRepository.getBook(bookId) ?: return BookEditResult.NotFound
            val imageBytes = appContext.contentResolver.openInputStream(imageUri)?.use { input ->
                input.readBytes()
            } ?: return BookEditResult.Failed

            val bookDir = File(current.book.originalFilePath).parentFile ?: return BookEditResult.Failed
            val paths = coverImageWriter.writeCover(bookDir, imageBytes) ?: return BookEditResult.Failed
            val updated = libraryRepository.updateCover(
                bookId = bookId,
                coverImagePath = paths.coverImagePath,
                coverThumbPath = paths.coverThumbPath,
                updatedAt = Clock.System.now().toString(),
                updatedByDeviceId = deviceIdProvider.getDeviceId(),
            ) ?: return BookEditResult.NotFound

            writePortableMetadata(updated, changeType = "cover_updated")
            BookEditResult.Success(bookId)
        }.getOrDefault(BookEditResult.Failed)
    }

    fun hasCompleteReadableCover(book: LibraryBook): Boolean {
        val thumb = book.book.coverThumbPath?.let(::File)
        val original = book.book.coverImagePath?.let(::File)
        return thumb != null &&
            original != null &&
            coverImageWriter.isReadableCover(thumb) &&
            coverImageWriter.isReadableCover(original)
    }

    fun recoverMissingCover(bookId: String): BookEditResult {
        return runCatching {
            val current = libraryRepository.getBook(bookId) ?: return BookEditResult.NotFound
            val originalFile = File(current.book.originalFilePath)
            if (!originalFile.exists()) return BookEditResult.Failed
            val bookDir = originalFile.parentFile ?: return BookEditResult.Failed

            val coverSource = AndroidZipEpubArchive(originalFile).use { archive ->
                val parsed = epubParser.parse(archive)
                val coverPath = parsed.coverPath
                if (coverPath == null) {
                    Log.d(
                        COVER_LOG_TAG,
                        "cover recovery bookId=$bookId opf=${parsed.technicalInfo.opfPath} " +
                            "result=no_cover_declaration",
                    )
                    return BookEditResult.Failed
                }
                val bytes = archive.readBytes(coverPath)
                if (bytes == null) {
                    Log.d(
                        COVER_LOG_TAG,
                        "cover recovery bookId=$bookId path=$coverPath result=missing_cover_entry",
                    )
                    return BookEditResult.Failed
                }
                CoverSource(path = coverPath, bytes = bytes)
            }
            val format = coverImageWriter.detectedFormat(coverSource.bytes)
            val paths = coverImageWriter.writeCover(bookDir, coverSource.bytes)
            Log.d(
                COVER_LOG_TAG,
                "cover recovery bookId=$bookId path=${coverSource.path} format=$format " +
                    "bytes=${coverSource.bytes.size} decoded=${paths != null}",
            )
            if (paths == null) return BookEditResult.Failed

            val updated = libraryRepository.updateCover(
                bookId = bookId,
                coverImagePath = paths.coverImagePath,
                coverThumbPath = paths.coverThumbPath,
                updatedAt = Clock.System.now().toString(),
                updatedByDeviceId = deviceIdProvider.getDeviceId(),
            ) ?: return BookEditResult.NotFound
            writePortableMetadata(updated, changeType = "cover_recovered")
            BookEditResult.Success(bookId)
        }.onFailure { error ->
            Log.w(COVER_LOG_TAG, "cover recovery failed bookId=$bookId", error)
        }.getOrDefault(BookEditResult.Failed)
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

    private data class CoverSource(
        val path: String,
        val bytes: ByteArray,
    )

    private companion object {
        const val COVER_LOG_TAG = "FolioreCover"
    }
}
