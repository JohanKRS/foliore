package com.foliore.android.storage

import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AppLanguage(
    val languageTag: String?,
) {
    SYSTEM(languageTag = null),
    PORTUGUESE_BRAZIL(languageTag = "pt-BR"),
    ENGLISH(languageTag = "en"),
    SPANISH(languageTag = "es"),
    JAPANESE(languageTag = "ja"),
    FRENCH(languageTag = "fr"),
    ITALIAN(languageTag = "it"),
    GERMAN(languageTag = "de"),
    CHINESE_SIMPLIFIED(languageTag = "zh-CN"),
}

class AppLanguagePreferenceRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val _appLanguage = MutableStateFlow(loadAppLanguage(preferences))

    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    fun setAppLanguage(appLanguage: AppLanguage) {
        preferences.edit()
            .putString(KEY_APP_LANGUAGE, appLanguage.name)
            .commit()
        _appLanguage.value = appLanguage
    }

    companion object {
        fun localizedContext(context: Context): Context {
            val preferences = context.applicationContext.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            )
            val appLanguage = loadAppLanguage(preferences)
            val systemLocale = context.resources.configuration.locales[0]
            val locale = appLanguage.languageTag?.let(Locale::forLanguageTag) ?: systemLocale
            Locale.setDefault(locale)

            if (appLanguage == AppLanguage.SYSTEM) return context

            val configuration = Configuration(context.resources.configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
            return context.createConfigurationContext(configuration)
        }

        private fun loadAppLanguage(
            preferences: android.content.SharedPreferences,
        ): AppLanguage {
            val stored = preferences.getString(KEY_APP_LANGUAGE, null)
            return AppLanguage.entries.firstOrNull { it.name == stored } ?: AppLanguage.SYSTEM
        }

        private const val PREFERENCES_NAME = "foliore_appearance"
        private const val KEY_APP_LANGUAGE = "app_language"
    }
}
