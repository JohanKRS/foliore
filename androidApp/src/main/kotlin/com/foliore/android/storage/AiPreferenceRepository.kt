package com.foliore.android.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.foliore.android.ai.CodexBridgeConfig
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
    private val _codexBridgeBaseUrl = MutableStateFlow(loadCodexBridgeBaseUrl())
    private val _hasCodexBridgeToken = MutableStateFlow(loadHasCodexBridgeToken())

    val providerMode: StateFlow<AiProviderMode> = _providerMode.asStateFlow()
    val hasOpenAiApiKey: StateFlow<Boolean> = _hasOpenAiApiKey.asStateFlow()
    val codexBridgeBaseUrl: StateFlow<String?> = _codexBridgeBaseUrl.asStateFlow()
    val hasCodexBridgeToken: StateFlow<Boolean> = _hasCodexBridgeToken.asStateFlow()

    fun setProviderMode(providerMode: AiProviderMode) {
        preferences.edit().putString(KEY_PROVIDER_MODE, providerMode.name).apply()
        _providerMode.value = providerMode
    }

    fun saveOpenAiApiKey(apiKey: String): Boolean {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) return false

        return if (saveEncryptedSecret(
                value = cleanKey,
                ivKey = KEY_OPENAI_API_KEY_IV,
                ciphertextKey = KEY_OPENAI_API_KEY_CIPHERTEXT,
            )
        ) {
            _hasOpenAiApiKey.value = true
            true
        } else {
            false
        }
    }

    fun openAiApiKey(): String? {
        return readEncryptedSecret(
            ivKey = KEY_OPENAI_API_KEY_IV,
            ciphertextKey = KEY_OPENAI_API_KEY_CIPHERTEXT,
        )
    }

    fun clearOpenAiApiKey() {
        preferences.edit()
            .remove(KEY_OPENAI_API_KEY_IV)
            .remove(KEY_OPENAI_API_KEY_CIPHERTEXT)
            .apply()
        _hasOpenAiApiKey.value = false
    }

    fun saveCodexBridgeSettings(baseUrl: String, token: String): Boolean {
        val cleanBaseUrl = baseUrl.normalizedBridgeBaseUrl() ?: return false
        val cleanToken = token.trim()
        val hasExistingToken = loadHasCodexBridgeToken()

        if (cleanToken.isBlank() && !hasExistingToken) return false

        val tokenSaved = if (cleanToken.isNotBlank()) {
            saveEncryptedSecret(
                value = cleanToken,
                ivKey = KEY_CODEX_BRIDGE_TOKEN_IV,
                ciphertextKey = KEY_CODEX_BRIDGE_TOKEN_CIPHERTEXT,
            )
        } else {
            true
        }
        if (!tokenSaved) return false

        preferences.edit()
            .putString(KEY_CODEX_BRIDGE_BASE_URL, cleanBaseUrl)
            .apply()
        _codexBridgeBaseUrl.value = cleanBaseUrl
        _hasCodexBridgeToken.value = true
        return true
    }

    fun clearCodexBridgeSettings() {
        preferences.edit()
            .remove(KEY_CODEX_BRIDGE_BASE_URL)
            .remove(KEY_CODEX_BRIDGE_TOKEN_IV)
            .remove(KEY_CODEX_BRIDGE_TOKEN_CIPHERTEXT)
            .apply()
        _codexBridgeBaseUrl.value = null
        _hasCodexBridgeToken.value = false
    }

    fun codexBridgeConfig(): CodexBridgeConfig? {
        val baseUrl = loadCodexBridgeBaseUrl() ?: return null
        val token = readEncryptedSecret(
            ivKey = KEY_CODEX_BRIDGE_TOKEN_IV,
            ciphertextKey = KEY_CODEX_BRIDGE_TOKEN_CIPHERTEXT,
        ) ?: return null
        return CodexBridgeConfig(
            baseUrl = baseUrl,
            token = token,
        )
    }

    private fun loadProviderMode(): AiProviderMode {
        val raw = preferences.getString(KEY_PROVIDER_MODE, null)
        if (raw == "CODEX_OAUTH_EXPERIMENTAL") {
            return AiProviderMode.CODEX_BRIDGE_LOCAL
        }
        return raw?.let { value ->
            AiProviderMode.entries.firstOrNull { it.name == value }
        } ?: AiProviderMode.DISABLED
    }

    private fun loadHasOpenAiApiKey(): Boolean {
        return preferences.contains(KEY_OPENAI_API_KEY_IV) &&
            preferences.contains(KEY_OPENAI_API_KEY_CIPHERTEXT)
    }

    private fun loadCodexBridgeBaseUrl(): String? {
        return preferences.getString(KEY_CODEX_BRIDGE_BASE_URL, null)
            ?.normalizedBridgeBaseUrl()
    }

    private fun loadHasCodexBridgeToken(): Boolean {
        return preferences.contains(KEY_CODEX_BRIDGE_TOKEN_IV) &&
            preferences.contains(KEY_CODEX_BRIDGE_TOKEN_CIPHERTEXT)
    }

    private fun saveEncryptedSecret(
        value: String,
        ivKey: String,
        ciphertextKey: String,
    ): Boolean {
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, secretKey())
            }
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            preferences.edit()
                .putString(ivKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString(ciphertextKey, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .apply()
            true
        }.getOrDefault(false)
    }

    private fun readEncryptedSecret(
        ivKey: String,
        ciphertextKey: String,
    ): String? {
        val iv = preferences.getString(ivKey, null) ?: return null
        val ciphertext = preferences.getString(ciphertextKey, null) ?: return null

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

    private fun String.normalizedBridgeBaseUrl(): String? {
        val clean = trim().trimEnd('/')
        if (clean.isBlank()) return null
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) return null
        return clean
    }

    private companion object {
        const val PREFERENCES_NAME = "foliore_ai"
        const val KEY_PROVIDER_MODE = "provider_mode"
        const val KEY_OPENAI_API_KEY_IV = "openai_api_key_iv"
        const val KEY_OPENAI_API_KEY_CIPHERTEXT = "openai_api_key_ciphertext"
        const val KEY_CODEX_BRIDGE_BASE_URL = "codex_bridge_base_url"
        const val KEY_CODEX_BRIDGE_TOKEN_IV = "codex_bridge_token_iv"
        const val KEY_CODEX_BRIDGE_TOKEN_CIPHERTEXT = "codex_bridge_token_ciphertext"
        const val KEY_ALIAS_OPENAI_API_KEY = "foliore_openai_api_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
