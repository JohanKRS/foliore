package com.foliore.android.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

data class CoverImagePaths(
    val coverImagePath: String,
    val coverThumbPath: String,
)

class CoverImageWriter {
    fun writeCover(bookDir: File, bytes: ByteArray): CoverImagePaths? {
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
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
}

