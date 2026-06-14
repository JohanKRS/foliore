package com.foliore.android.storage

import android.content.Context

internal class PendingRemoteDeletionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        SyncPreferenceKeys.PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun markDeleted(bookId: String) {
        val updated = pendingBookIds() + bookId
        preferences.edit()
            .putStringSet(SyncPreferenceKeys.KEY_PENDING_REMOTE_DELETIONS, updated)
            .apply()
    }

    fun pendingBookIds(): Set<String> {
        return preferences.getStringSet(SyncPreferenceKeys.KEY_PENDING_REMOTE_DELETIONS, emptySet())
            ?.toSet()
            .orEmpty()
    }

    fun clear(bookIds: Set<String>) {
        if (bookIds.isEmpty()) return
        val remaining = pendingBookIds() - bookIds
        preferences.edit()
            .putStringSet(SyncPreferenceKeys.KEY_PENDING_REMOTE_DELETIONS, remaining)
            .apply()
    }
}

internal object SyncPreferenceKeys {
    const val PREFERENCES_NAME = "foliore_sync"
    const val KEY_PENDING_REMOTE_DELETIONS = "pending_remote_deletions"
}
