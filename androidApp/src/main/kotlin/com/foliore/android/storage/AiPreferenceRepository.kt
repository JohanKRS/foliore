package com.foliore.android.storage

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Optional OpenAI API key, used for cover generation and as a fallback for text features. */
class AiPreferenceRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val secrets = KeystoreSecretBox(preferences, KEY_ALIAS_OPENAI_API_KEY)
    private val _hasOpenAiApiKey = MutableStateFlow(!openAiApiKey().isNullOrBlank())

    val hasOpenAiApiKey: StateFlow<Boolean> = _hasOpenAiApiKey.asStateFlow()

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
        const val SECRET_OPENAI_API_KEY = "openai_api_key"
        const val KEY_ALIAS_OPENAI_API_KEY = "foliore_openai_api_key"
    }
}
