package com.foliore.android.storage

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile

internal object StorageLimits {
    const val EPUB_BYTES = 256L * 1024 * 1024
    const val ENTRY_BYTES = 32L * 1024 * 1024
    const val XML_BYTES = 8L * 1024 * 1024
    const val EXPANDED_BYTES = 512L * 1024 * 1024
    const val ENTRIES = 10_000
    const val IMAGE_PIXELS = 80_000_000L

    fun inspect(zip: ZipFile) {
        require(zip.size() <= ENTRIES) { "Too many EPUB entries" }
        var expanded = 0L
        val names = HashSet<String>()
        zip.entries().asSequence().forEach { entry ->
            require(names.add(entry.name)) { "Duplicate EPUB entry" }
            require(entry.size in 0..ENTRY_BYTES) { "EPUB entry exceeds size limit" }
            expanded += entry.size
            require(expanded <= EXPANDED_BYTES) { "EPUB exceeds expanded size limit" }
        }
    }
}

internal class ReadBudget(private var remaining: Long) {
    fun consume(bytes: Int) {
        require(bytes >= 0 && bytes.toLong() <= remaining) { "Content exceeds size limit" }
        remaining -= bytes
    }
}

internal fun InputStream.copyBounded(output: OutputStream, limit: Long, budget: ReadBudget? = null): Long {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val count = read(buffer)
        if (count < 0) return copied
        require(count.toLong() <= limit - copied) { "Content exceeds size limit" }
        budget?.consume(count)
        output.write(buffer, 0, count)
        copied += count
    }
}

internal fun InputStream.readBounded(limit: Long, budget: ReadBudget? = null): ByteArray {
    val output = ByteArrayOutputStream()
    copyBounded(output, limit, budget)
    return output.toByteArray()
}

internal fun java.io.File.sha256(): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
