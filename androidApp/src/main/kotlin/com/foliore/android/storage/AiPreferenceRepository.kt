package com.foliore.android.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.foliore.android.ai.AiProviderMode
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AiPreferenceRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _providerMode = MutableStateFlow(loadProviderMode())
    private val _hasOpenAiApiKey = MutableStateFlow(loadHasOpenAiApiKey())

    val providerMode: StateFlow<AiProviderMode> = _providerMode.asStateFlow()
    val hasOpenAiApiKey: StateFlow<Boolean> = _hasOpenAiApiKey.asStateFlow()

    fun setProviderMode(providerMode: AiProviderMode) {
        preferences.edit().putString(KEY_PROVIDER_MODE, providerMode.name).apply()
        _providerMode.value = providerMode
    }

    fun saveOpenAiApiKey(apiKey: String): Boolean {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) return false

        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, secretKey())
            }
            val encrypted = cipher.doFinal(cleanKey.toByteArray(Charsets.UTF_8))
            preferences.edit()
                .putString(KEY_OPENAI_API_KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString(KEY_OPENAI_API_KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply()
            _hasOpenAiApiKey.value = true
            true
        }.getOrDefault(false)
    }

    fun openAiApiKey(): String? {
        val iv = preferences.getString(KEY_OPENAI_API_KEY_IV, null) ?: return null
        val ciphertext = preferences.getString(KEY_OPENAI_API_KEY_CIPHERTEXT, null) ?: return null

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

    fun clearOpenAiApiKey() {
        preferences.edit()
            .remove(KEY_OPENAI_API_KEY_IV)
            .remove(KEY_OPENAI_API_KEY_CIPHERTEXT)
            .apply()
        _hasOpenAiApiKey.value = false
    }

    private fun loadProviderMode(): AiProviderMode {
        val raw = preferences.getString(KEY_PROVIDER_MODE, null)
        return raw?.let { value ->
            AiProviderMode.entries.firstOrNull { it.name == value }
        } ?: AiProviderMode.DISABLED
    }

    private fun loadHasOpenAiApiKey(): Boolean {
        return preferences.contains(KEY_OPENAI_API_KEY_IV) &&
            preferences.contains(KEY_OPENAI_API_KEY_CIPHERTEXT)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS_OPENAI_API_KEY, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS_OPENAI_API_KEY,
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
        const val PREFERENCES_NAME = "foliore_ai"
        const val KEY_PROVIDER_MODE = "provider_mode"
        const val KEY_OPENAI_API_KEY_IV = "openai_api_key_iv"
        const val KEY_OPENAI_API_KEY_CIPHERTEXT = "openai_api_key_ciphertext"
        const val KEY_ALIAS_OPENAI_API_KEY = "foliore_openai_api_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
