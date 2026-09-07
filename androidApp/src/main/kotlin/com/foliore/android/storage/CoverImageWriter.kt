package com.foliore.android.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.os.Build
import com.caverock.androidsvg.SVG
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.math.roundToInt

data class CoverImagePaths(
    val coverImagePath: String,
    val coverThumbPath: String,
)

class CoverImageWriter {
    fun writeCover(bookDir: File, bytes: ByteArray): CoverImagePaths? {
        require(bytes.size.toLong() <= StorageLimits.ENTRY_BYTES) { "Cover exceeds size limit" }
        val decoded = decode(bytes) ?: return null
        val cover = decoded.scaledToFit(maxWidth = 1400, maxHeight = 2100)
        val thumb = decoded.scaledToFit(maxWidth = 420, maxHeight = 630)

        val coverFile = File(bookDir, "cover_original.jpg")
        val thumbFile = File(bookDir, "cover_thumb.jpg")

        cover.writeJpeg(coverFile, quality = 90)
        thumb.writeJpeg(thumbFile, quality = 84)

        if (cover !== decoded) cover.recycle()
        if (thumb !== decoded) thumb.recycle()
        decoded.recycle()

        return CoverImagePaths(
            coverImagePath = coverFile.absolutePath,
            coverThumbPath = thumbFile.absolutePath,
        )
    }

    fun detectedFormat(bytes: ByteArray): String {
        if (bytes.hasPrefix(0xFF, 0xD8, 0xFF)) return "jpeg"
        if (bytes.hasPrefix(0x89, 0x50, 0x4E, 0x47)) return "png"
        if (bytes.asciiPrefix(6) in setOf("GIF87a", "GIF89a")) return "gif"
        if (bytes.asciiPrefix(4) == "RIFF" && bytes.asciiAt(8, 4) == "WEBP") return "webp"
        if (bytes.asciiPrefix(2) == "BM") return "bmp"
        if (bytes.asciiAt(4, 4) == "ftyp") {
            val brand = bytes.asciiAt(8, 4)
            if (brand.startsWith("avi")) return "avif"
            if (brand.startsWith("hei") || brand in setOf("mif1", "msf1")) return "heif"
        }
        if (bytes.looksLikeSvg()) return "svg"
        return "unknown"
    }

    fun isReadableCover(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    private fun decode(bytes: ByteArray): Bitmap? {
        if (bytes.looksLikeSvg()) {
            decodeSvg(bytes)?.let { return it }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            require(bounds.outWidth.toLong() * bounds.outHeight <= StorageLimits.IMAGE_PIXELS) { "Cover dimensions exceed limit" }
            val options = BitmapFactory.Options().apply { inSampleSize = 1 }
            while (bounds.outWidth / options.inSampleSize > MAX_COVER_WIDTH * 2 ||
                bounds.outHeight / options.inSampleSize > MAX_COVER_HEIGHT * 2) options.inSampleSize *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.let { return it }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
                    require(info.size.width.toLong() * info.size.height <= StorageLimits.IMAGE_PIXELS) { "Cover dimensions exceed limit" }
                    val scale = min(MAX_COVER_WIDTH.toFloat() / info.size.width, MAX_COVER_HEIGHT.toFloat() / info.size.height).coerceAtMost(1f)
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            }.getOrNull()?.let { return it }
        }

        return null
    }

    private fun decodeSvg(bytes: ByteArray): Bitmap? {
        return runCatching {
            SVG.setInternalEntitiesEnabled(false)
            val svg = SVG.getFromInputStream(bytes.inputStream())
            val viewBox = svg.documentViewBox
            val sourceWidth = svg.documentWidth.takeIf { it > 0f }
                ?: viewBox?.width()?.takeIf { it > 0f }
                ?: DEFAULT_SVG_WIDTH
            val sourceHeight = svg.documentHeight.takeIf { it > 0f }
                ?: viewBox?.height()?.takeIf { it > 0f }
                ?: DEFAULT_SVG_HEIGHT
            val scale = min(
                MAX_COVER_WIDTH.toFloat() / sourceWidth,
                MAX_COVER_HEIGHT.toFloat() / sourceHeight,
            ).coerceAtMost(1f)
            val targetWidth = (sourceWidth * scale).roundToInt().coerceIn(1, MAX_COVER_WIDTH)
            val targetHeight = (sourceHeight * scale).roundToInt().coerceIn(1, MAX_COVER_HEIGHT)

            svg.documentWidth = targetWidth.toFloat()
            svg.documentHeight = targetHeight.toFloat()
            Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                svg.renderToCanvas(canvas)
            }
        }.getOrNull()
    }

    private fun Bitmap.scaledToFit(maxWidth: Int, maxHeight: Int): Bitmap {
        val scale = min(maxWidth.toFloat() / width.toFloat(), maxHeight.toFloat() / height.toFloat())
            .coerceAtMost(1f)
        if (scale >= 1f) return this

        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    private fun Bitmap.writeJpeg(file: File, quality: Int) {
        FileOutputStream(file).use { output ->
            compress(Bitmap.CompressFormat.JPEG, quality, output)
        }
    }

    private fun ByteArray.hasPrefix(vararg expected: Int): Boolean {
        return size >= expected.size && expected.indices.all { index ->
            this[index].toInt() and 0xFF == expected[index]
        }
    }

    private fun ByteArray.asciiPrefix(length: Int): String = asciiAt(offset = 0, length = length)

    private fun ByteArray.asciiAt(offset: Int, length: Int): String {
        if (offset < 0 || size < offset + length) return ""
        return String(this, offset, length, Charsets.US_ASCII)
    }

    private fun ByteArray.looksLikeSvg(): Boolean {
        val prefixLength = min(size, SVG_SNIFF_BYTES)
        if (prefixLength == 0) return false
        val prefix = String(this, 0, prefixLength, Charsets.UTF_8)
            .removePrefix("\uFEFF")
            .trimStart()
            .lowercase()
        return prefix.startsWith("<svg") ||
            (prefix.startsWith("<?xml") && "<svg" in prefix)
    }

    private companion object {
        const val MAX_COVER_WIDTH = 1400
        const val MAX_COVER_HEIGHT = 2100
        const val DEFAULT_SVG_WIDTH = 1000f
        const val DEFAULT_SVG_HEIGHT = 1500f
        const val SVG_SNIFF_BYTES = 4096
    }
}
