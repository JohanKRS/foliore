package com.foliore.android.ai

import com.foliore.android.metadata.MetadataLanguages
import com.foliore.shared.domain.BookMetadataDraft
import java.text.Normalizer
import java.util.Locale

/**
 * One metadata standard for every book the AI fills in: the language as a BCP 47 code (shown by its
 * full name in the editor), and tags taken from the Thema subject categories by their readable names.
 */
internal object MetadataStandard {
    /** 1 to 3 genres plus at most 3 subjects. */
    const val MAX_TAGS = 6

    /**
     * Rules for the model. [textLanguageTag] is the language for tags and the About text, or null for
     * each book's own language. [libraryTags] are reused when they name the same category.
     */
    fun instructions(textLanguageTag: String?, libraryTags: List<String>): String = buildString {
        append(
            "language must be a BCP 47 tag as EPUB dc:language stores it, such as \"en\", \"pt-BR\" or \"fr\". " +
                "Keep the current language when it is right for the book. When it is missing, or written as a name " +
                "such as \"English\" or \"Português\", return the tag instead.",
        )
        append(' ')
        if (textLanguageTag == null) {
            append(
                "Write description/about and tags in the book's own language, given by the language field. " +
                    "If the language is unknown, infer it from the title and the existing text first.",
            )
        } else {
            val name = Locale.forLanguageTag(textLanguageTag).getDisplayName(Locale.ENGLISH)
            append(
                "Write description/about and tags in $name ($textLanguageTag), even when the book is in another " +
                    "language. The language field still names the book's own language.",
            )
        }
        append(' ')
        append(
            "Tags follow the Thema subject categories, the international standard of the book trade, written as " +
                "their readable category names and never as Thema codes. Return 1 to 3 genre tags first, at the most " +
                "specific level that is still useful (for example \"Science fiction\", then \"Hard science fiction\"), " +
                "then at most 3 subject tags, only for topics central to the book (for example \"Space exploration\"). " +
                "Use sentence case, like a catalog heading. Never tag the format, the author, the title, the series, " +
                "a year, or catch-alls such as \"Fiction\", \"Novel\", \"Book\" or \"eBook\".",
        )
        if (libraryTags.isNotEmpty()) {
            append(' ')
            append(
                "Tags already used in this library: ${libraryTags.joinToString(", ")}. " +
                    "When one of them names the same category in the language required above, return it exactly " +
                    "as written instead of a new variant.",
            )
        }
    }

    /** Applies the standard in code too, whatever the model returned. */
    fun standardize(draft: BookMetadataDraft, libraryTags: List<String>): BookMetadataDraft {
        val known = libraryTags.associateBy(::tagKey)
        val tags = draft.tags
            .mapNotNull { it.trim().trimEnd('.').takeIf(String::isNotEmpty) }
            .map { tag -> known[tagKey(tag)] ?: tag.replaceFirstChar { it.titlecase(Locale.ROOT) } }
            .distinctBy(::tagKey)
            .take(MAX_TAGS)
        return draft.copy(
            language = draft.language?.let { MetadataLanguages.code(it) ?: it.trim().takeIf(String::isNotEmpty) },
            tags = tags,
        )
    }

    /** Case and accents don't make a different tag: "Ficção científica" = "ficcao cientifica". */
    private fun tagKey(tag: String): String = Normalizer.normalize(tag.trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("\\s+"), " ")
        .lowercase(Locale.ROOT)
}

internal fun BookMetadataDraft.standardized(libraryTags: List<String>): BookMetadataDraft =
    MetadataStandard.standardize(this, libraryTags)
