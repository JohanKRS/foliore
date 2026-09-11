package com.foliore.android.storage

import java.io.File

internal interface RemoteLibraryStore {
    val currentTimeMillis: Long? get() = System.currentTimeMillis()
    val deletionScope: String? get() = null
    val label: String
    val maxConcurrentUploads: Int
        get() = 1
    val maxConcurrentDownloads: Int
        get() = 1

    fun listRootEntries(): List<RemoteRootEntry>

    fun readText(path: String): String?

    fun writeText(
        path: String,
        text: String,
        mimeType: String = "application/json",
    )

    fun replaceFile(
        path: String,
        source: File,
        mimeType: String,
    )

    /** Verify the last upload using a provider-computed digest, or return false to read it back. */
    fun verifiesUpload(path: String, size: Long, sha256: String): Boolean = false

    fun copyToLocal(
        path: String,
        destination: File,
    ): File?

    fun hasDirectory(path: String): Boolean

    fun ensureDirectory(path: String)

    fun deleteTree(path: String)

    fun deleteTrees(paths: List<String>) { paths.forEach(::deleteTree) }

    fun deleteUnexpectedChildren(
        directoryPath: String,
        expectedDirectoryNames: Set<String>,
    ): Boolean
}

internal fun String.normalizedRemotePath(): String {
    if (isEmpty()) return ""
    require(!startsWith('/') && !contains('\\') && none { it.code < 32 }) { "Invalid remote path" }
    val parts = split('/')
    require(parts.all { it.isNotBlank() && it == it.trim() && it != "." && it != ".." }) { "Invalid remote path" }
    return parts.joinToString("/")
}

internal fun String.parentRemotePath(): String {
    val normalized = normalizedRemotePath()
    return normalized.substringBeforeLast('/', missingDelimiterValue = "")
}

internal fun String.remoteName(): String {
    return normalizedRemotePath().substringAfterLast('/')
}

internal data class RemoteRootEntry(val name: String, val isDirectory: Boolean, val modifiedAtMillis: Long)
