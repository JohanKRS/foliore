package com.foliore.android.storage

import android.content.Context
import com.foliore.android.ai.AiProviderMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AiPreferenceRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _providerMode = MutableStateFlow(loadProviderMode())

    val providerMode: StateFlow<AiProviderMode> = _providerMode.asStateFlow()

    fun setProviderMode(providerMode: AiProviderMode) {
        preferences.edit().putString(KEY_PROVIDER_MODE, providerMode.name).apply()
        _providerMode.value = providerMode
    }

    private fun loadProviderMode(): AiProviderMode {
        val raw = preferences.getString(KEY_PROVIDER_MODE, null)
        return raw?.let { value ->
            AiProviderMode.entries.firstOrNull { it.name == value }
        } ?: AiProviderMode.DISABLED
    }

    private companion object {
        const val PREFERENCES_NAME = "foliore_ai"
        const val KEY_PROVIDER_MODE = "provider_mode"
    }
}
