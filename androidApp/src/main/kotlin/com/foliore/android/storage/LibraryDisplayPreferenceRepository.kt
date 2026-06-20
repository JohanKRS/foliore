package com.foliore.android.storage

import android.content.Context

class LibraryDisplayPreferenceRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun gridColumns(): String? {
        return preferences.getString(KEY_GRID_COLUMNS, null)
    }

    fun setGridColumns(value: String) {
        preferences.edit().putString(KEY_GRID_COLUMNS, value).apply()
    }

    fun showTitle(): Boolean {
        return preferences.getBoolean(KEY_SHOW_TITLE, true)
    }

    fun showAuthor(): Boolean {
        return preferences.getBoolean(KEY_SHOW_AUTHOR, true)
    }

    fun showSeries(): Boolean {
        return preferences.getBoolean(KEY_SHOW_SERIES, true)
    }

    fun setShowTitle(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_TITLE, enabled).apply()
    }

    fun setShowAuthor(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_AUTHOR, enabled).apply()
    }

    fun setShowSeries(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_SHOW_SERIES, enabled).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "foliore_display"
        const val KEY_GRID_COLUMNS = "grid_columns"
        const val KEY_SHOW_TITLE = "show_title"
        const val KEY_SHOW_AUTHOR = "show_author"
        const val KEY_SHOW_SERIES = "show_series"
    }
}
