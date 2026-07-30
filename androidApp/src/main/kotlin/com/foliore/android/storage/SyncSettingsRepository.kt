package com.foliore.android.storage

import android.annotation.SuppressLint
import android.content.Context

@SuppressLint("ApplySharedPref") // Sync identity and cache must survive an immediate process stop.
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
            .commit()
    }

    fun googleDriveAccountLabel(): String? {
        return preferences.getString(KEY_GOOGLE_DRIVE_ACCOUNT_LABEL, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun googleDriveAccountName(): String? {
        return preferences.getString(KEY_GOOGLE_DRIVE_ACCOUNT_NAME, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun setGoogleDriveConnected(
        accountLabel: String?,
        accountName: String?,
    ) {
        val editor = preferences.edit()
            .putString(
                KEY_GOOGLE_DRIVE_ACCOUNT_LABEL,
                accountLabel ?: googleDriveAccountLabel() ?: GOOGLE_DRIVE_DEFAULT_LABEL,
            )
            .putString(KEY_ACTIVE_PROVIDER, SyncProvider.GOOGLE_DRIVE_APPDATA.name)
        accountName?.takeIf { it.isNotBlank() }?.let { name ->
            editor.putString(KEY_GOOGLE_DRIVE_ACCOUNT_NAME, name)
        }
        editor.commit()
    }

    fun rememberGoogleDriveAccount(
        accountLabel: String?,
        accountName: String?,
    ) {
        if (accountLabel.isNullOrBlank() && accountName.isNullOrBlank()) return
        val editor = preferences.edit()
        accountLabel?.takeIf { it.isNotBlank() }?.let { label ->
            editor.putString(KEY_GOOGLE_DRIVE_ACCOUNT_LABEL, label)
        }
        accountName?.takeIf { it.isNotBlank() }?.let { name ->
            editor.putString(KEY_GOOGLE_DRIVE_ACCOUNT_NAME, name)
        }
        editor.commit()
    }

    fun disconnectGoogleDrive() {
        val editor = preferences.edit()
            .remove(KEY_GOOGLE_DRIVE_ACCOUNT_LABEL)
            .remove(KEY_GOOGLE_DRIVE_ACCOUNT_NAME)
            .remove(KEY_GOOGLE_DRIVE_BACKUP_STATS)
        if (activeProvider() == SyncProvider.GOOGLE_DRIVE_APPDATA) {
            editor.putString(KEY_ACTIVE_PROVIDER, SyncProvider.NONE.name)
        }
        editor.commit()
    }

    fun cachedGoogleDriveBackupStats(): GoogleDriveBackupStats? {
        val encoded = preferences.getString(KEY_GOOGLE_DRIVE_BACKUP_STATS, null) ?: return null
        return GoogleDriveBackupStatsCacheCodec.decode(encoded)
    }

    fun setCachedGoogleDriveBackupStats(stats: GoogleDriveBackupStats) {
        preferences.edit()
            .putString(KEY_GOOGLE_DRIVE_BACKUP_STATS, GoogleDriveBackupStatsCacheCodec.encode(stats))
            .commit()
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
        private const val KEY_GOOGLE_DRIVE_ACCOUNT_NAME = "google_drive_account_name"
        private const val KEY_GOOGLE_DRIVE_BACKUP_STATS = "google_drive_backup_stats"
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
        private const val KEY_AUTO_SYNC_WIFI_ONLY = "auto_sync_wifi_only"
        private const val KEY_LEGACY_SYNC_TREE_URI = "sync_tree_uri"
    }
}
