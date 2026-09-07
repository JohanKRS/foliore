package com.foliore.android.storage

import com.foliore.shared.epub.EpubArchive
import java.io.Closeable
import java.io.File
import java.util.zip.ZipFile

class AndroidZipEpubArchive(file: File) : EpubArchive, Closeable {
    private val zipFile = ZipFile(file).also { zip ->
        try {
            require(file.length() <= StorageLimits.EPUB_BYTES) { "EPUB exceeds size limit" }
            StorageLimits.inspect(zip)
        } catch (error: Throwable) { zip.close(); throw error }
    }
    private val budget = ReadBudget(StorageLimits.EXPANDED_BYTES)

    override fun listEntries(): List<String> {
        return zipFile.entries().asSequence().map { it.name }.toList()
    }

    override fun readText(path: String): String? {
        val entry = zipFile.getEntry(path.trimStart('/')) ?: return null
        return zipFile.getInputStream(entry).use { it.readBounded(StorageLimits.XML_BYTES, budget).toString(Charsets.UTF_8) }
    }

    override fun readBytes(path: String): ByteArray? {
        val entry = zipFile.getEntry(path.trimStart('/')) ?: return null
        return zipFile.getInputStream(entry).use { it.readBounded(StorageLimits.ENTRY_BYTES, budget) }
    }

    override fun close() {
        zipFile.close()
    }
}

