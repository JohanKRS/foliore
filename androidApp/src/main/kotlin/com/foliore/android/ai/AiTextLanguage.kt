package com.foliore.android.ai

import com.foliore.android.storage.AppLanguage
import java.util.Locale

/** Which language the AI writes tags and the About text in. */
enum class AiTextLanguage {
    /** Each book's own language, from its language field. */
    BOOK,

    /** The language the app is shown in, for every book. */
    APP,
    ;

    companion object {
        val DEFAULT = BOOK

        /**
         * The app's display language as a BCP 47 tag, limited to the languages the app is translated into.
         * Anything else falls back to English, as the app's own texts do.
         */
        fun appLanguageTag(locale: Locale = Locale.getDefault()): String {
            val supported = AppLanguage.entries.mapNotNull { it.languageTag }
            return supported.firstOrNull { it.equals(locale.toLanguageTag(), ignoreCase = true) }
                ?: supported.firstOrNull { Locale.forLanguageTag(it).language == locale.language }
                ?: "en"
        }
    }
}
