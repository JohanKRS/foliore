package com.foliore.android.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MetadataPreferenceRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _hasGoogleBooksApiKey = MutableStateFlow(loadHasGoogleBooksApiKey())

    val hasGoogleBooksApiKey: StateFlow<Boolean> = _hasGoogleBooksApiKey.asStateFlow()

    fun saveGoogleBooksApiKey(apiKey: String): Boolean {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) return false

        return if (saveEncryptedSecret(cleanKey)) {
            _hasGoogleBooksApiKey.value = true
            true
        } else {
            false
        }
    }

    fun googleBooksApiKey(): String? {
        return readEncryptedSecret()
    }

    fun clearGoogleBooksApiKey() {
        preferences.edit()
            .remove(KEY_GOOGLE_BOOKS_API_KEY_IV)
            .remove(KEY_GOOGLE_BOOKS_API_KEY_CIPHERTEXT)
            .apply()
        _hasGoogleBooksApiKey.value = false
    }

    private fun loadHasGoogleBooksApiKey(): Boolean {
        return preferences.contains(KEY_GOOGLE_BOOKS_API_KEY_IV) &&
            preferences.contains(KEY_GOOGLE_BOOKS_API_KEY_CIPHERTEXT)
    }

    private fun saveEncryptedSecret(value: String): Boolean {
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, secretKey())
            }
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            preferences.edit()
                .putString(KEY_GOOGLE_BOOKS_API_KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString(KEY_GOOGLE_BOOKS_API_KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply()
            true
        }.getOrDefault(false)
    }

    private fun readEncryptedSecret(): String? {
        val iv = preferences.getString(KEY_GOOGLE_BOOKS_API_KEY_IV, null) ?: return null
        val ciphertext = preferences.getString(KEY_GOOGLE_BOOKS_API_KEY_CIPHERTEXT, null) ?: return null

        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(
                    Cipher.DECRYPT_MODE,
                    secretKey(),
                    GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(iv, Base64.NO_WRAP)),
                )
            }
            String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS_GOOGLE_BOOKS_API_KEY, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS_GOOGLE_BOOKS_API_KEY,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "foliore_metadata_preferences"
        const val KEY_GOOGLE_BOOKS_API_KEY_IV = "google_books_api_key_iv"
        const val KEY_GOOGLE_BOOKS_API_KEY_CIPHERTEXT = "google_books_api_key_ciphertext"
        const val KEY_ALIAS_GOOGLE_BOOKS_API_KEY = "foliore_google_books_api_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
