package com.foliore.android.storage

import com.foliore.shared.epub.EpubArchive
import java.io.Closeable
import java.io.File
import java.util.zip.ZipFile

class AndroidZipEpubArchive(file: File) : EpubArchive, Closeable {
    private val zipFile = ZipFile(file)

    override fun listEntries(): List<String> {
        return zipFile.entries().asSequence().map { it.name }.toList()
    }

    override fun readText(path: String): String? {
        val entry = zipFile.getEntry(path.trimStart('/')) ?: return null
        return zipFile.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    override fun readBytes(path: String): ByteArray? {
        val entry = zipFile.getEntry(path.trimStart('/')) ?: return null
        return zipFile.getInputStream(entry).use { it.readBytes() }
    }

    override fun close() {
        zipFile.close()
    }
}

