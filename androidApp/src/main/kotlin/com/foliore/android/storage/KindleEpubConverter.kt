package com.foliore.android.storage

import com.foliore.shared.epub.EpubParser
import java.io.File
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object MobiNative {
    init { System.loadLibrary("foliore_conversion") }
    external fun extract(source: String, destination: String): Int
}

internal class EbookConversionException(val reason: ImportFailureReason) : Exception(reason.name)

/** Converts entirely offline. Only a validated, deterministic EPUB is handed to the importer. */
internal class KindleEpubConverter(
    private val extract: (String, String) -> Int = { source, destination -> MobiNative.extract(source, destination) },
) {
    fun convert(source: File, target: File) {
        val working = File(target.parentFile, "conversion-${UUID.randomUUID()}")
        try {
            require(source.length() in 1..StorageLimits.EPUB_BYTES)
            check(working.mkdir())
            val result = extract(source.absolutePath, working.absolutePath)
            if (result != 0) throw EbookConversionException(when (result) {
                2 -> ImportFailureReason.DRM_PROTECTED
                4 -> ImportFailureReason.TOO_LARGE
                else -> ImportFailureReason.CONVERSION_FAILED
            })
            val extracted = working.listFiles().orEmpty()
            require(extracted.all { it.isFile && it.length() <= StorageLimits.ENTRY_BYTES })
            require(extracted.sumOf { it.length() } <= StorageLimits.EXPANDED_BYTES)
            KindleMarkupNormalizer.normalize(working)
            val parts = working.listFiles()?.sortedBy { it.name }.orEmpty()
            require(parts.size in 1..StorageLimits.ENTRIES - 2)
            require(parts.any { it.name == "content.opf" })
            var expanded = 0L
            parts.forEach { part ->
                require(part.isFile && part.length() <= StorageLimits.ENTRY_BYTES)
                require(part.length() <= StorageLimits.ENTRY_BYTES)
                expanded += part.length()
                require(expanded <= StorageLimits.EXPANDED_BYTES)
            }
            ZipOutputStream(target.outputStream().buffered()).use { zip ->
                val mimetype = "application/epub+zip".toByteArray()
                zip.putNextEntry(entry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimetype.size.toLong()
                    compressedSize = size
                    crc = CRC32().apply { update(mimetype) }.value
                })
                zip.write(mimetype)
                zip.closeEntry()
                zip.putNextEntry(entry("META-INF/container.xml"))
                zip.write(CONTAINER.toByteArray())
                zip.closeEntry()
                parts.forEach { part ->
                    zip.putNextEntry(entry("OEBPS/${part.name}"))
                    part.inputStream().use { it.copyBounded(zip, StorageLimits.ENTRY_BYTES) }
                    zip.closeEntry()
                }
            }
            require(target.length() <= StorageLimits.EPUB_BYTES)
            AndroidZipEpubArchive(target).use { archive ->
                val parsed = EpubParser().parse(archive)
                require(parsed.isReadable && parsed.technicalInfo.errors.isEmpty())
                require(parsed.technicalInfo.spineItemCount > 0)
            }
        } catch (error: Exception) {
            target.delete()
            throw if (error is EbookConversionException) error
                else EbookConversionException(ImportFailureReason.CONVERSION_FAILED)
        } finally {
            working.deleteRecursively()
        }
    }

    private fun entry(name: String) = ZipEntry(name).apply {
        // Fixed DOS timestamp keeps hashes identical across repeated imports and timezones.
        time = java.util.GregorianCalendar(1980, 0, 1, 0, 0, 0).timeInMillis
    }

    companion object {
        private const val CONTAINER = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""
    }
}
