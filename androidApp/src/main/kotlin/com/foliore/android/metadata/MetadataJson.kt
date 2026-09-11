package com.foliore.android.metadata

import androidx.core.text.HtmlCompat
import kotlinx.serialization.json.*
import java.net.URLEncoder

internal fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
internal fun JsonObject.obj(key: String): JsonObject? = get(key) as? JsonObject
internal fun JsonObject.items(key: String): List<JsonElement> = (get(key) as? JsonArray)?.toList().orEmpty()
internal fun JsonObject.strings(key: String): List<String> = items(key).mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
internal fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")
internal fun String.displayMetadataText(): String = (if ('<' in this || '&' in this) HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY).toString() else this)
    .replace(Regex("\\[([^\\]]+)]\\((?:https?://|//)[^)]+\\)")) { match ->
        val label = match.groupValues[1].trim('*', '_', ' ')
        if (label.matches(Regex("(?i)(pdf|epub|download|read online|baixar)"))) "" else label
    }
    .replace(Regex("(?<!\\w)[*_]{1,2}([^*_\\n]+)[*_]{1,2}(?!\\w)"), "$1")
    .replace('\u00a0', ' ').lines().joinToString("\n") { it.trim() }.replace(Regex("\\n{3,}"), "\n\n").trim()
internal fun JsonObject.description(): String? = (text("description") ?: obj("description")?.text("value"))?.displayMetadataText()?.takeIf(String::isNotBlank)
internal fun String.sortAuthor(): String = catalogAuthor().trim().split(Regex("\\s+")).let { if (it.size < 2) this else "${it.last()}, ${it.dropLast(1).joinToString(" ")}" }
internal fun String.safeWorkKey(): String? = takeIf { Regex("/works/OL[0-9]+W").matches(it) }
internal fun String.safeEditionKey(): String? = takeIf { Regex("/books/OL[0-9]+M").matches(it) }

internal fun String.safeAuthorKey(): String? = takeIf { Regex("/authors/OL[0-9]+A").matches(it) }

/** Some imported catalog titles include an author byline; remove it only for a known author. */
internal fun String.withoutAuthorByline(authors: List<String>): String {
    for (author in authors.filter(String::isNotBlank)) {
        val prefix = Regex("^by\\s+${Regex.escape(author)}\\s+", RegexOption.IGNORE_CASE).find(trim()) ?: continue
        return trim().substring(prefix.range.last + 1).takeIf(String::isNotBlank) ?: this
    }
    return this
}
