package com.foliore.android.storage

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.File

internal class DocumentFileRemoteLibraryStore(
    private val context: Context,
    private val root: DocumentFile,
    override val deletionScope: String? = null,
    override val label: String,
) : RemoteLibraryStore {
    private val appContext = context.applicationContext
    private val documentCache = DocumentFileCache()

    override fun listRootEntries(): List<RemoteRootEntry> {
        documentCache.invalidate(root)
        val entries = documentCache.childrenOf(root)
        check(entries.groupBy { it.name?.lowercase() }.none { it.value.size > 1 }) { "Ambiguous sync objects" }
        return entries.map { RemoteRootEntry(checkNotNull(it.name), it.isDirectory, it.lastModified()) }
    }

    override fun readText(path: String): String? {
        return root.findRelativeFile(path, documentCache)?.readText()
    }

    override fun writeText(
        path: String,
        text: String,
        mimeType: String,
    ) {
        val destination = root.replaceRelativeFile(path, mimeType, documentCache)
        val outputStream = appContext.contentResolver.openOutputStream(destination.uri, "wt")
            ?: error("Cannot open $path for writing")
        outputStream.use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
        }
    }

    override fun replaceFile(
        path: String,
        source: File,
        mimeType: String,
    ) {
        check(source.isFile) { "Missing upload source" }
        val destination = root.replaceRelativeFile(path, mimeType, documentCache)
        val outputStream = appContext.contentResolver.openOutputStream(destination.uri, "wt")
            ?: error("Cannot open $path for writing")
        outputStream.use { output ->
            source.inputStream().use { input ->
                input.copyBounded(output, StorageLimits.EPUB_BYTES)
            }
        }
    }

    override fun copyToLocal(
        path: String,
        destination: File,
    ): File? {
        val source = root.findRelativeFile(path, documentCache) ?: return null
        source.copyToLocal(destination)
        return destination.takeIf { it.exists() }
    }

    override fun hasDirectory(path: String): Boolean {
        return path.normalizedRemotePath().isBlank() || root.findRelativeDirectory(path, documentCache) != null
    }

    override fun ensureDirectory(path: String) {
        root.ensureRelativeDirectory(path, documentCache)
    }

    override fun deleteTree(path: String) {
        val normalized = path.normalizedRemotePath()
        if (normalized.isBlank()) return
        root.findRelativeDirectory(normalized, documentCache)?.deleteTree(documentCache)
        root.findRelativeFile(normalized, documentCache)?.deleteTree(documentCache)
    }

    override fun deleteUnexpectedChildren(
        directoryPath: String,
        expectedDirectoryNames: Set<String>,
    ): Boolean {
        val directory = root.findRelativeDirectory(directoryPath, documentCache) ?: return false
        var changed = false
        documentCache.childrenOf(directory)
            .filter { child -> child.name !in expectedDirectoryNames || child.isFile }
            .forEach { obsolete ->
                obsolete.deleteTree(documentCache)
                changed = true
            }
        if (changed) documentCache.invalidate(directory)
        return changed
    }

    private fun DocumentFile.ensureRelativeDirectory(
        path: String,
        documentCache: DocumentFileCache,
    ): DocumentFile {
        val segments = path.normalizedRemotePath()
            .split('/')
            .filter { it.isNotBlank() }
        return segments.fold(this) { current, segment ->
            current.ensureDirectory(segment, documentCache)
        }
    }

    private fun DocumentFile.replaceRelativeFile(
        path: String,
        mimeType: String,
        documentCache: DocumentFileCache,
    ): DocumentFile {
        val parent = ensureRelativeDirectory(path.parentRemotePath(), documentCache)
        return parent.replaceFile(path.remoteName(), mimeType, documentCache)
    }

    private fun DocumentFile.ensureDirectory(
        name: String,
        documentCache: DocumentFileCache,
    ): DocumentFile {
        findUniqueDirectory(name, documentCache)?.let { existing -> return existing }
        val created = createDirectory(name) ?: error("Cannot create $name")
        documentCache.invalidate(this)
        return created
    }

    private fun DocumentFile.replaceFile(
        name: String,
        mimeType: String,
        documentCache: DocumentFileCache,
    ): DocumentFile {
        documentCache.childrenOf(this)
            .filter { it.name.matchesDocumentName(name) && !it.isDirectory }
            .forEach { existing ->
                if (!existing.delete()) error("Cannot replace $name")
            }
        documentCache.invalidate(this)
        val created = (createFile(mimeType, name) ?: error("Cannot create $name")).withExactSyncName(name)
        documentCache.invalidate(this)
        return created
    }

    private fun DocumentFile.findRelativeFile(
        path: String,
        documentCache: DocumentFileCache,
    ): DocumentFile? {
        val segments = path.normalizedRemotePath()
            .split('/')
            .filter { it.isNotBlank() }
        if (segments.isEmpty()) return null
        return segments.foldIndexed(this as DocumentFile?) { index, current, segment ->
            if (current == null) {
                null
            } else if (index == segments.lastIndex) {
                current.findUniqueFile(segment, documentCache)
            } else {
                current.findUniqueDirectory(segment, documentCache)
            }
        }
    }

    private fun DocumentFile.findRelativeDirectory(
        path: String,
        documentCache: DocumentFileCache,
    ): DocumentFile? {
        val normalized = path.normalizedRemotePath()
        if (normalized.isBlank()) return this
        val segments = normalized.split('/').filter { it.isNotBlank() }
        return segments.fold(this as DocumentFile?) { current, segment ->
            current?.findUniqueDirectory(segment, documentCache)
        }
    }

    private fun DocumentFile.copyToLocal(destination: File) {
        destination.parentFile?.mkdirs()
        val inputStream = appContext.contentResolver.openInputStream(uri)
            ?: error("Cannot open ${name ?: uri} for reading")
        inputStream.use { input ->
            destination.outputStream().use { output ->
                input.copyBounded(output, StorageLimits.EPUB_BYTES)
            }
        }
    }

    private fun DocumentFile.readText(): String {
        val inputStream = appContext.contentResolver.openInputStream(uri)
            ?: error("Cannot open ${name ?: uri} for reading")
        return inputStream.use { it.readBounded(StorageLimits.ENTRY_BYTES).toString(Charsets.UTF_8) }
    }

    private fun DocumentFile.findUniqueFile(
        name: String,
        documentCache: DocumentFileCache,
    ): DocumentFile? {
        val matches = documentCache.filesNamed(this, name)
        val canonical = documentCache.bestFile(matches) ?: return null
        check(matches.size == 1) { "Ambiguous remote name: $name" }
        return canonical
    }

    private fun DocumentFile.findUniqueDirectory(
        name: String,
        documentCache: DocumentFileCache,
    ): DocumentFile? {
        val matches = documentCache.directoriesNamed(this, name)
        val canonical = documentCache.bestDirectory(matches) ?: return null
        check(matches.size == 1) { "Ambiguous remote name: $name" }
        return canonical
    }

    private fun DocumentFile.deleteTree(documentCache: DocumentFileCache) {
        if (isDirectory || !isFile) {
            documentCache.childrenOf(this).forEach { child ->
                child.deleteTree(documentCache)
            }
        }
        documentCache.invalidate(this)
        if (!delete()) error("Cannot delete ${name ?: uri}")
    }

    internal class DocumentFileCache {
        private val childrenByDirectoryUri = mutableMapOf<String, List<DocumentFile>>()

        fun childrenOf(directory: DocumentFile): List<DocumentFile> {
            return childrenByDirectoryUri.getOrPut(directory.uri.toString()) {
                directory.listFiles().toList()
            }
        }

        fun invalidate(directory: DocumentFile) {
            childrenByDirectoryUri.remove(directory.uri.toString())
        }

        fun filesNamed(directory: DocumentFile, name: String): List<DocumentFile> {
            return childrenOf(directory)
                .filter { file -> file.name.matchesDocumentName(name) && !file.isDirectory }
        }

        fun directoriesNamed(directory: DocumentFile, name: String): List<DocumentFile> {
            return childrenOf(directory)
                .filter { file -> file.name.matchesDocumentName(name) }
                .filter { file -> file.isDirectory || !file.isFile }
        }

        fun bestFile(files: List<DocumentFile>): DocumentFile? {
            return files.bestDocumentFile()
        }

        fun bestDirectory(directories: List<DocumentFile>): DocumentFile? {
            if (directories.size <= 1) return directories.firstOrNull()
            return directories.maxWithOrNull(
                compareBy<DocumentFile> { file -> childrenOf(file).size }
                    .thenBy { file -> file.lastModified() }
                    .thenBy { file -> file.uri.toString() },
            )
        }

        private fun List<DocumentFile>.bestDocumentFile(): DocumentFile? {
            return maxWithOrNull(
                compareBy<DocumentFile> { file -> file.lastModified() }
                    .thenBy { file -> file.uri.toString() },
            )
        }
    }
}

/** SAF providers may append a MIME extension, including .txt to JSON-named commit markers. */
internal fun DocumentFile.withExactSyncName(expected: String): DocumentFile {
    if (name != expected && (!renameTo(expected) || name != expected)) {
        delete()
        error("Sync provider cannot preserve file name: $expected")
    }
    return this
}

internal fun String?.matchesDocumentName(expected: String): Boolean {
    return this
        ?.trim()
        ?.equals(expected.trim(), ignoreCase = true) == true
}
