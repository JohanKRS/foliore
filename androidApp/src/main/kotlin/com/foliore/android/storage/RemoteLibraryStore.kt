package com.foliore.android.storage

import java.io.File

internal interface RemoteLibraryStore {
    val label: String
    val maxConcurrentUploads: Int
        get() = 1
    val maxConcurrentDownloads: Int
        get() = 1

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

    fun copyToLocal(
        path: String,
        destination: File,
    ): File?

    fun hasDirectory(path: String): Boolean

    fun ensureDirectory(path: String)

    fun deleteTree(path: String)

    fun deleteUnexpectedChildren(
        directoryPath: String,
        expectedDirectoryNames: Set<String>,
    ): Boolean
}

internal fun String.normalizedRemotePath(): String {
    return split('/')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("/")
}

internal fun String.parentRemotePath(): String {
    val normalized = normalizedRemotePath()
    return normalized.substringBeforeLast('/', missingDelimiterValue = "")
}

internal fun String.remoteName(): String {
    return normalizedRemotePath().substringAfterLast('/')
}
