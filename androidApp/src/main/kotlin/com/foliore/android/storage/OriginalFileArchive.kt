package com.foliore.android.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class OriginalBookFile(val id: String, val name: String, val format: String, val sizeBytes: Long)

internal data class OriginalFileSource(val file: File, val displayName: String)

/** Local archives are independent of the EPUB used for editing and of downloaded sync versions. */
internal class OriginalFileArchive(private val root: File) {
    private val json = Json { ignoreUnknownKeys = true }

    fun isInitialized(bookId: String) = File(directory(bookId), INDEX).isFile

    fun initialize(bookId: String, sources: List<OriginalFileSource>) {
        if (isInitialized(bookId)) return
        check(root.isDirectory || root.mkdirs())
        val destination = directory(bookId)
        check(!destination.exists()) { "Incomplete original archive" }
        val staging = File(root, ".${requireSafeBookId(bookId)}-${UUID.randomUUID()}")
        check(staging.mkdir())
        try {
            val originals = sources.map { source ->
                require(source.file.isFile && source.file.length() <= StorageLimits.EPUB_BYTES)
                val extension = source.file.extension.lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,16}")) } ?: "bin"
                val id = "${UUID.randomUUID()}.$extension"
                source.file.inputStream().use { input ->
                    File(staging, id).outputStream().use { input.copyBounded(it, StorageLimits.EPUB_BYTES) }
                }
                ArchivedOriginal(id, source.displayName.substringAfterLast('/').substringAfterLast('\\').take(240).ifBlank { "original.$extension" })
            }
            File(staging, INDEX).writeText(json.encodeToString(originals))
            Files.move(staging.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally { staging.deleteRecursively() }
    }

    fun list(bookId: String): List<OriginalBookFile> = index(bookId).mapNotNull { entry ->
        file(bookId, entry.id)?.let { OriginalBookFile(entry.id, entry.name, it.extension.uppercase(), it.length()) }
    }

    fun file(bookId: String, id: String): File? {
        if (!id.matches(Regex("[0-9a-f-]{36}\\.[a-z0-9]{1,16}"))) return null
        if (index(bookId).none { it.id == id }) return null
        val directory = directory(bookId).canonicalFile
        return File(directory, id).takeIf { it.isFile && it.canonicalFile.parentFile == directory }
    }

    fun delete(bookId: String, id: String): Boolean {
        val file = file(bookId, id) ?: return false
        // Retain the index even when empty, so migration/sync cannot recreate deleted originals.
        return file.delete()
    }

    fun deleteBook(bookId: String) {
        val directory = directory(bookId)
        check(!directory.exists() || directory.deleteRecursively()) { "Cannot delete original archive" }
    }

    fun copyBook(fromBookId: String, toBookId: String) {
        if (!isInitialized(fromBookId) || isInitialized(toBookId)) return
        initialize(toBookId, list(fromBookId).map {
            OriginalFileSource(checkNotNull(file(fromBookId, it.id)), it.name)
        })
    }

    private fun index(bookId: String): List<ArchivedOriginal> {
        val index = File(directory(bookId), INDEX)
        if (!index.isFile) return emptyList()
        return json.decodeFromString(index.inputStream().use { it.readBounded(128 * 1024).toString(Charsets.UTF_8) })
    }

    private fun directory(bookId: String) = safeChildDirectory(root, bookId)

    @Serializable private data class ArchivedOriginal(val id: String, val name: String)

    companion object { const val DIRECTORY = "originals"; private const val INDEX = "index.json" }
}
