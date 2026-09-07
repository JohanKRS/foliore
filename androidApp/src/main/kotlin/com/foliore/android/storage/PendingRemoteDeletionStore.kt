package com.foliore.android.storage

import android.content.Context

internal class PendingRemoteDeletionStore(private val context: Context, private val destinationOverride: String? = null) {
    private val preferences = context.applicationContext.getSharedPreferences(
        SyncPreferenceKeys.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private fun scopedKey(): String {
        val settings = SyncSettingsRepository(context)
        val destination = destinationOverride ?: when (settings.activeProvider()) {
            SyncProvider.GOOGLE_DRIVE_APPDATA -> "drive:" + settings.googleDriveAccountName().orEmpty()
            SyncProvider.MANUAL_FOLDER -> "folder:" + preferences.getString("sync_tree_uri", "")
            SyncProvider.NONE -> "none"
        }
        return SyncPreferenceKeys.KEY_PENDING_REMOTE_DELETIONS + ":" +
            java.security.MessageDigest.getInstance("SHA-256").digest(destination.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun markDeleted(bookId: String, revision: Long, updatedAt: String) {
        val updated = pendingBookIds() + bookId
        preferences.edit()
            .putStringSet(scopedKey(), updated)
            .putString(scopedKey() + ":" + bookId, "$revision|$updatedAt")
            .commit().also { check(it) { "Cannot persist deletion intent" } }
    }

    fun matchesDeletedVersion(bookId: String, revision: Long, updatedAt: String): Boolean =
        preferences.getString(scopedKey() + ":" + bookId, null) == "$revision|$updatedAt"

    fun pendingBookIds(): Set<String> {
        return preferences.getStringSet(scopedKey(), emptySet())
            ?.toSet()
            .orEmpty()
    }

    fun clear(bookIds: Set<String>) {
        if (bookIds.isEmpty()) return
        val remaining = pendingBookIds() - bookIds
        preferences.edit()
            .putStringSet(scopedKey(), remaining)
            .also { editor -> bookIds.forEach { editor.remove(scopedKey() + ":" + it) } }
            .commit().also { check(it) { "Cannot persist deletion intent" } }
    }
}

internal object SyncPreferenceKeys {
    const val PREFERENCES_NAME = "foliore_sync"
    const val KEY_PENDING_REMOTE_DELETIONS = "pending_remote_deletions"
}
