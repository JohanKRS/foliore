package com.foliore.android.storage

import android.content.Context
import com.foliore.shared.library.LibraryRepository
import java.io.File

class AndroidBookDeleter(
    context: Context,
    private val libraryRepository: LibraryRepository,
) {
    private val appContext = context.applicationContext

    fun delete(bookId: String): BookDeleteResult {
        val deleted = libraryRepository.deleteBook(bookId) ?: return BookDeleteResult.NotFound
        val bookDir = File(appContext.filesDir, "books/$bookId")

        return runCatching {
            if (bookDir.isSafeBookDirectory(bookId) && bookDir.exists()) {
                check(bookDir.deleteRecursively()) { "Cannot delete local book files" }
            }
            BookDeleteResult.Deleted(deleted.metadata.title)
        }.getOrDefault(BookDeleteResult.Failed)
    }

    private fun File.isSafeBookDirectory(bookId: String): Boolean {
        val booksRoot = File(appContext.filesDir, "books").canonicalFile
        val target = canonicalFile
        return target.name == bookId && target.parentFile == booksRoot
    }
}
