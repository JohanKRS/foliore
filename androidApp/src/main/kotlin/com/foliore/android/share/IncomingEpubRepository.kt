package com.foliore.android.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns

class IncomingEpubRepository(context: Context) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver

    fun urisFromIntent(intent: Intent?): List<Uri> {
        if (intent == null || intent.action !in SUPPORTED_ACTIONS) return emptyList()

        val candidates = buildList<Uri> {
            when (intent.action) {
                Intent.ACTION_SEND -> streamUri(intent)?.let(::add)
                Intent.ACTION_SEND_MULTIPLE -> addAll(streamUris(intent))
                Intent.ACTION_VIEW -> intent.data?.let(::add)
            }
            intent.clipData?.let { clipData ->
                repeat(clipData.itemCount) { index ->
                    clipData.getItemAt(index).uri?.let(::add)
                }
            }
        }

        return candidates
            .distinct()
            .filter { uri -> isLikelyEpub(uri, intent.type) }
    }

    private fun streamUri(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun streamUris(intent: Intent): List<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
    }

    private fun isLikelyEpub(uri: Uri, intentType: String?): Boolean {
        val resolverType = contentResolver.getType(uri)
        val displayName = displayName(uri)
        return intentType in EPUB_MIME_TYPES ||
            resolverType in EPUB_MIME_TYPES ||
            displayName.endsWith(".epub", ignoreCase = true) ||
            uri.lastPathSegment?.endsWith(".epub", ignoreCase = true) == true
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(index)
                }
            }
        }
        return ""
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_SEND,
            Intent.ACTION_SEND_MULTIPLE,
            Intent.ACTION_VIEW,
        )
        val EPUB_MIME_TYPES = setOf(
            "application/epub+zip",
            "application/x-epub+zip",
        )
    }
}
