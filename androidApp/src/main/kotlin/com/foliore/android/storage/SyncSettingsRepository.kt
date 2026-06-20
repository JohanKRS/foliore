package com.foliore.android.storage

import android.content.Context

class SyncSettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        SyncPreferenceKeys.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun activeProvider(): SyncProvider {
        val stored = preferences.getString(KEY_ACTIVE_PROVIDER, null)
        if (stored != null) {
            return runCatching { SyncProvider.valueOf(stored) }.getOrDefault(SyncProvider.NONE)
        }
        return if (preferences.getString(KEY_LEGACY_SYNC_TREE_URI, null) != null) {
            SyncProvider.MANUAL_FOLDER
        } else {
            SyncProvider.NONE
        }
    }

    fun setActiveProvider(provider: SyncProvider) {
        preferences.edit()
            .putString(KEY_ACTIVE_PROVIDER, provider.name)
            .apply()
    }

    fun googleDriveAccountLabel(): String? {
        return preferences.getString(KEY_GOOGLE_DRIVE_ACCOUNT_LABEL, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun setGoogleDriveConnected(accountLabel: String?) {
        preferences.edit()
            .putString(KEY_GOOGLE_DRIVE_ACCOUNT_LABEL, accountLabel ?: GOOGLE_DRIVE_DEFAULT_LABEL)
            .putString(KEY_ACTIVE_PROVIDER, SyncProvider.GOOGLE_DRIVE_APPDATA.name)
            .apply()
    }

    fun disconnectGoogleDrive() {
        preferences.edit()
            .remove(KEY_GOOGLE_DRIVE_ACCOUNT_LABEL)
            .apply()
        if (activeProvider() == SyncProvider.GOOGLE_DRIVE_APPDATA) {
            setActiveProvider(SyncProvider.NONE)
        }
    }

    fun isAutoSyncEnabled(): Boolean {
        return preferences.getBoolean(KEY_AUTO_SYNC_ENABLED, false)
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_AUTO_SYNC_ENABLED, enabled)
            .apply()
    }

    fun isAutoSyncWifiOnly(): Boolean {
        return preferences.getBoolean(KEY_AUTO_SYNC_WIFI_ONLY, true)
    }

    fun setAutoSyncWifiOnly(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_AUTO_SYNC_WIFI_ONLY, enabled)
            .apply()
    }

    companion object {
        const val GOOGLE_DRIVE_DEFAULT_LABEL = "Google Drive privado"
        private const val KEY_ACTIVE_PROVIDER = "active_sync_provider"
        private const val KEY_GOOGLE_DRIVE_ACCOUNT_LABEL = "google_drive_account_label"
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
        private const val KEY_AUTO_SYNC_WIFI_ONLY = "auto_sync_wifi_only"
        private const val KEY_LEGACY_SYNC_TREE_URI = "sync_tree_uri"
    }
}
