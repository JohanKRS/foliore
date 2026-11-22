package com.foliore.android.storage

import android.content.Context
import com.foliore.android.ai.AiTextLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Optional OpenAI API key, used for cover generation and as a fallback for text features. */
class AiPreferenceRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val secrets = KeystoreSecretBox(preferences, KEY_ALIAS_OPENAI_API_KEY)
    private val _hasOpenAiApiKey = MutableStateFlow(!openAiApiKey().isNullOrBlank())

    val hasOpenAiApiKey: StateFlow<Boolean> = _hasOpenAiApiKey.asStateFlow()

    private val _textLanguage = MutableStateFlow(
        preferences.getString(KEY_TEXT_LANGUAGE, null)
            ?.let { saved -> AiTextLanguage.entries.firstOrNull { it.name == saved } }
            ?: AiTextLanguage.DEFAULT,
    )

    /** The language the AI writes tags and the About text in. */
    val textLanguage: StateFlow<AiTextLanguage> = _textLanguage.asStateFlow()

    fun setTextLanguage(language: AiTextLanguage) {
        preferences.edit().putString(KEY_TEXT_LANGUAGE, language.name).apply()
        _textLanguage.value = language
    }

    fun saveOpenAiApiKey(apiKey: String): Boolean {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) return false
        return secrets.write(SECRET_OPENAI_API_KEY, cleanKey).also { saved ->
            if (saved) _hasOpenAiApiKey.value = true
        }
    }

    fun openAiApiKey(): String? = secrets.read(SECRET_OPENAI_API_KEY)

    fun clearOpenAiApiKey() {
        secrets.remove(SECRET_OPENAI_API_KEY, preferences.edit().remove(LEGACY_KEY_PROVIDER_MODE))
        _hasOpenAiApiKey.value = false
    }

    private companion object {
        const val PREFERENCES_NAME = "foliore_ai"
        const val LEGACY_KEY_PROVIDER_MODE = "provider_mode"
        const val KEY_TEXT_LANGUAGE = "text_language"
        const val SECRET_OPENAI_API_KEY = "openai_api_key"
        const val KEY_ALIAS_OPENAI_API_KEY = "foliore_openai_api_key"
    }
}
