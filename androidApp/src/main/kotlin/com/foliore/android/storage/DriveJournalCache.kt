package com.foliore.android.storage

import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Only reuses bytes after a fresh Drive listing confirms their identity/version/checksum. */
internal class DriveJournalCache(cacheDir: File, accountScope: String) {
    private val root = File(cacheDir, "drive-journal/${accountScope.digest()}")

    fun read(file: DriveFileMetadata, download: () -> String): String {
        val key = key(file) ?: return download()
        val destination = File(root, key)
        val cached = runCatching {
            destination.takeIf { it.isFile && it.length() == file.sizeText?.toLongOrNull() }
                ?.readText()?.takeIf { it.digest() == file.sha256Checksum }
        }.getOrNull()
        if (cached != null) return cached
        val text = download()
        check(text.toByteArray(Charsets.UTF_8).size.toLong() == file.sizeText?.toLongOrNull() &&
            text.digest() == file.sha256Checksum) { "Drive journal content changed or checksum mismatch; retry sync" }
        // Cache failure never prevents syncing. An interrupted write cannot become a valid hit.
        runCatching {
            root.mkdirs()
            val temporary = File(root, "${UUID.randomUUID()}.tmp")
            try {
                temporary.writeText(text)
                temporary.renameTo(destination)
            } finally { temporary.delete() }
        }
        return text
    }

    fun retain(files: List<DriveFileMetadata>) {
        val keep = files.mapNotNull(::key).toSet()
        root.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }

    private fun key(file: DriveFileMetadata): String? {
        if (!(file.name.startsWith(RemoteManifestJournal.RECORD_PREFIX) ||
                file.name.startsWith(RemoteManifestJournal.COMMIT_PREFIX)) ||
            file.version?.toLongOrNull() == null ||
            file.sha256Checksum?.matches(Regex("[a-f0-9]{64}")) != true ||
            file.sizeText?.toLongOrNull()?.let { it in 0..StorageLimits.ENTRY_BYTES } != true) return null
        return "${file.id}\n${file.version}\n${file.sha256Checksum}".digest()
    }

    private fun String.digest(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
