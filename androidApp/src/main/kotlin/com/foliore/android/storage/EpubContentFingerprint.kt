package com.foliore.android.storage

import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Identifies the readable content of an EPUB independently of mutable package metadata,
 * cover artwork, ZIP timestamps, compression level, and entry order.
 */
internal object EpubContentFingerprint {
    fun calculate(
        file: File,
        opfPath: String,
        coverPath: String?,
        navPath: String?,
        ncxPath: String?,
    ): String? = runCatching {
        val ignoredPaths = setOfNotNull(
            opfPath.normalizedEntryPath().takeIf { it.isNotEmpty() },
            coverPath?.normalizedEntryPath(),
            navPath?.normalizedEntryPath(),
            ncxPath?.normalizedEntryPath(),
        )
        val digest = MessageDigest.getInstance("SHA-256")

        ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence()
                .filterNot { it.isDirectory }
                .filterNot { entry -> entry.name.shouldIgnoreForContentFingerprint(ignoredPaths) }
                .sortedBy { it.name }
                .toList()

            if (entries.isEmpty()) error("EPUB has no stable content entries")

            entries.forEach { entry ->
                digest.updateLength(entry.name.toByteArray(Charsets.UTF_8).size.toLong())
                digest.update(entry.name.toByteArray(Charsets.UTF_8))
                digest.updateLength(entry.size)
                zip.getInputStream(entry).use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        digest.update(buffer, 0, read)
                    }
                }
            }
        }

        digest.digest().toHexString()
    }.getOrNull()

    private fun String.shouldIgnoreForContentFingerprint(ignoredPaths: Set<String>): Boolean {
        val normalized = normalizedEntryPath()
        val lowercase = normalized.lowercase(Locale.ROOT)
        if (normalized in ignoredPaths) return true
        if (lowercase == "meta-inf/metadata.xml") return true
        if (lowercase.startsWith("__macosx/") || lowercase.endsWith("/.ds_store")) return true

        val extension = lowercase.substringAfterLast('.', missingDelimiterValue = "")
        val isImage = extension in setOf("avif", "bmp", "gif", "jpeg", "jpg", "png", "svg", "webp")
        val isDocument = extension in setOf("htm", "html", "xhtml")
        val fileName = lowercase.substringAfterLast('/').substringBeforeLast('.')
        val isNamedAsCover = fileName == "cover" ||
            fileName.startsWith("cover-") ||
            fileName.startsWith("cover_") ||
            fileName.endsWith("-cover") ||
            fileName.endsWith("_cover") ||
            fileName.contains("coverpage") ||
            fileName.contains("cover-page") ||
            fileName.contains("cover_page")
        return (isImage || isDocument) && (
            isNamedAsCover || lowercase.contains("/cover/") || lowercase.startsWith("cover/")
        )
    }

    private fun String.normalizedEntryPath(): String = trim().trimStart('/')

    private fun MessageDigest.updateLength(value: Long) {
        for (shift in 56 downTo 0 step 8) {
            update(((value ushr shift) and 0xff).toByte())
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
