package com.foliore.android.storage

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores small secrets in [preferences], encrypted with a non-exportable AES-GCM key from the
 * Android Keystore. Values cannot be read after a restore to another device, which is intended.
 */
internal class KeystoreSecretBox(
    private val preferences: SharedPreferences,
    private val keyAlias: String,
) {
    fun write(name: String, value: String): Boolean = runCatching {
        val editor = preferences.edit()
        put(editor, name, value)
        editor.commit()
    }.getOrDefault(false)

    /** Stage encryption in the caller's transaction. Nothing is published until the editor commits. */
    fun put(editor: SharedPreferences.Editor, name: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        editor.putString(ivKey(name), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(ciphertextKey(name), Base64.encodeToString(encrypted, Base64.NO_WRAP))
    }

    fun read(name: String): String? {
        val iv = preferences.getString(ivKey(name), null) ?: return null
        val ciphertext = preferences.getString(ciphertextKey(name), null) ?: return null
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

    fun remove(name: String, editor: SharedPreferences.Editor = preferences.edit()) {
        discard(editor, name)
        editor.apply()
    }

    fun discard(editor: SharedPreferences.Editor, name: String) {
        editor.remove(ivKey(name)).remove(ciphertextKey(name))
    }

    private fun ivKey(name: String) = "${name}_iv"
    private fun ciphertextKey(name: String) = "${name}_ciphertext"

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
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
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
