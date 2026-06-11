package com.foliore.android.storage

import android.content.Context
import java.util.UUID

class DeviceIdProvider(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getDeviceId(): String {
        val existing = preferences.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing

        val created = "android-${UUID.randomUUID()}"
        preferences.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    private companion object {
        const val PREFERENCES_NAME = "foliore_device"
        const val KEY_DEVICE_ID = "device_id"
    }
}

