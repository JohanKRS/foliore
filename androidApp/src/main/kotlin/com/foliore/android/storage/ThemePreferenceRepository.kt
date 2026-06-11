package com.foliore.android.storage

import android.content.Context
import com.foliore.android.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemePreferenceRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _themeMode = MutableStateFlow(loadThemeMode())

    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(themeMode: ThemeMode) {
        preferences.edit().putString(KEY_THEME_MODE, themeMode.name).apply()
        _themeMode.value = themeMode
    }

    private fun loadThemeMode(): ThemeMode {
        val raw = preferences.getString(KEY_THEME_MODE, null)
        return raw?.let { value ->
            ThemeMode.values().firstOrNull { it.name == value }
        } ?: ThemeMode.SYSTEM
    }

    private companion object {
        const val PREFERENCES_NAME = "foliore_appearance"
        const val KEY_THEME_MODE = "theme_mode"
    }
}

