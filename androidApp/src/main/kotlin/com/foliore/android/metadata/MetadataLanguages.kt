package com.foliore.android.metadata

import java.text.Normalizer
import java.util.Locale

/** Keep BCP-47 codes in EPUBs; show readable, localized names in the editor. */
object MetadataLanguages {
    private fun key(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()
    private val languages by lazy { Locale.getISOLanguages().associateWith { Locale.forLanguageTag(it) } }
    private val aliases by lazy {
        buildMap {
            languages.forEach { (code, locale) ->
                put(code, code)
                runCatching { locale.isO3Language }.getOrNull()?.let { put(it, code) }
                (listOf(Locale.ENGLISH, Locale.forLanguageTag("pt-BR"), Locale.FRENCH, Locale.GERMAN,
                    Locale.ITALIAN, Locale.forLanguageTag("es"), Locale.JAPANESE, Locale.CHINESE, locale)).forEach {
                    put(key(locale.getDisplayLanguage(it)), code)
                }
            }
            putAll(mapOf("fre" to "fr", "ger" to "de", "chi" to "zh", "dut" to "nl", "gre" to "el",
                "rum" to "ro", "cze" to "cs", "slo" to "sk", "wel" to "cy", "per" to "fa", "alb" to "sq",
                "arm" to "hy", "baq" to "eu", "bur" to "my", "geo" to "ka", "ice" to "is", "mac" to "mk", "may" to "ms", "tib" to "bo"))
        }
    }
    fun code(value: String?): String? {
        val raw = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val clean = raw.substringAfterLast('/').replace('_', '-')
        aliases[key(clean)]?.let { return it }
        // Also recognize the displayed regional/script names when editing an existing value.
        val parts = clean.split('-')
        val base = aliases[key(parts.first())]
        if (base != null && parts.drop(1).all { it.matches(Regex("[A-Za-z0-9]{2,8}")) }) {
            return Locale.forLanguageTag((listOf(base) + parts.drop(1)).joinToString("-")).toLanguageTag()
        }
        return null
    }
    fun display(value: String?, displayLocale: Locale): String {
        val code = code(value) ?: return value.orEmpty()
        return Locale.forLanguageTag(code).getDisplayName(displayLocale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(displayLocale) else it.toString() }
    }
    fun fromDisplay(value: String, previous: String?, displayLocale: Locale): String? {
        if (value.trim() == display(previous, displayLocale)) return code(previous) ?: previous
        return code(value) ?: value.trim().takeIf(String::isNotEmpty)
    }
}
