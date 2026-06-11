package com.foliore.android.storage

import android.content.Context
import android.net.Uri
import com.foliore.shared.data.PortableSnapshots
import com.foliore.shared.domain.BookMetadataDraft
import com.foliore.shared.domain.LibraryBook
import com.foliore.shared.library.LibraryRepository
import java.io.File
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidBookEditor(
    context: Context,
    private val libraryRepository: LibraryRepository,
    private val deviceIdProvider: DeviceIdProvider,
    private val coverImageWriter: CoverImageWriter = CoverImageWriter(),
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
}

