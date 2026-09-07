package com.foliore.android.storage

import java.net.URI
import java.nio.file.Paths

internal object EpubCoverReferences {
    fun relative(from: String, target: String): String =
        Paths.get(from.substringBeforeLast('/', "")).relativize(Paths.get(target)).toString().replace('\\', '/')

    fun rewrite(text: String, documentPath: String, oldCover: String, newCover: String): String {
        fun replacement(value: String): String? {
            val uri = runCatching { URI(value.replace(" ", "%20")) }.getOrNull() ?: return null
            if (uri.isAbsolute || uri.rawAuthority != null) return null
            val parent = documentPath.substringBeforeLast('/', "")
            val resolved = Paths.get(parent).resolve(uri.path).normalize().toString().replace('\\', '/')
            if (resolved != oldCover) return null
            return relative(documentPath, newCover) + (uri.rawFragment?.let { "#$it" } ?: "")
        }
        val attributes = Regex("((?:[A-Za-z_][\\w.-]*:)?(?:href|src)\\s*=\\s*)([\"'])(.*?)(\\2)", RegexOption.IGNORE_CASE)
        var updated = attributes.replace(text) { match ->
            replacement(match.groupValues[3])?.let { match.groupValues[1] + match.groupValues[2] + it + match.groupValues[2] }
                ?: match.value
        }
        updated = Regex("url\\(\\s*([\"']?)([^)\"']+)\\1\\s*\\)", RegexOption.IGNORE_CASE).replace(updated) { match ->
            replacement(match.groupValues[2].trim())?.let { "url(\"$it\")" } ?: match.value
        }
        if (documentPath.endsWith(".opf", ignoreCase = true)) {
            val newHref = relative(documentPath, newCover)
            updated = Regex("<item\\b[^>]*>", RegexOption.IGNORE_CASE).replace(updated) { match ->
                val href = Regex("\\bhref\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE).find(match.value)?.groupValues?.get(2)
                if (href != newHref) match.value else {
                    val mime = Regex("\\bmedia-type\\s*=\\s*([\"']).*?\\1", RegexOption.IGNORE_CASE)
                    if (mime.containsMatchIn(match.value)) mime.replace(match.value, "media-type=\"image/jpeg\"")
                    else match.value.replace("<item", "<item media-type=\"image/jpeg\"", ignoreCase = true)
                }
            }
        }
        return updated
    }
}
