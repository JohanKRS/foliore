package com.foliore.android.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class IncomingCoverRepository(context: Context) {
    private val appContext = context.applicationContext

    fun copyFromIntent(intent: Intent): Uri? {
        if (intent.action != Intent.ACTION_SEND) return null
        val type = intent.type ?: return null
        if (!type.startsWith("image/")) return null
        val source = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        } ?: return null

        return runCatching {
            val cacheDir = File(appContext.cacheDir, "incoming-covers").apply { mkdirs() }
            val target = File(cacheDir, "${UUID.randomUUID()}.img")
            val input = appContext.contentResolver.openInputStream(source) ?: return null
            input.use { sourceStream ->
                FileOutputStream(target).use { output ->
                    sourceStream.copyTo(output)
                }
            }
            Uri.fromFile(target)
        }.getOrNull()
    }
}
