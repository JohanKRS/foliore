package com.foliore.android.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Patterns
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class KindleEmailProvider { GMAIL, SMTP }

data class KindleEmailSettings(
    val kindleEmail: String?,
    val senderEmail: String?,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpUsername: String?,
    val hasSmtpPassword: Boolean,
    val gmailAccount: String? = null,
    val provider: KindleEmailProvider = KindleEmailProvider.SMTP,
) {
    val isReady: Boolean
        get() = if (provider == KindleEmailProvider.GMAIL) kindleEmail != null && gmailAccount != null else isSmtpReady
    val isSmtpReady: Boolean
        get() = kindleEmail != null &&
            senderEmail != null &&
            smtpHost.isNotBlank() &&
            smtpPort in 1..65535 &&
            smtpUsername != null &&
            hasSmtpPassword
}

data class KindleEmailCredentials(
    val kindleEmail: String,
    val senderEmail: String,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpUsername: String,
    val smtpPassword: String,
)

class KindleEmailPreferenceRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val settings: KindleEmailSettings
        get() = KindleEmailSettings(
            kindleEmail = readEmail(KEY_KINDLE_EMAIL),
            senderEmail = readEmail(KEY_SENDER_EMAIL),
            smtpHost = preferences.getString(KEY_SMTP_HOST, DEFAULT_SMTP_HOST) ?: DEFAULT_SMTP_HOST,
            smtpPort = preferences.getInt(KEY_SMTP_PORT, DEFAULT_SMTP_PORT),
            smtpUsername = preferences.getString(KEY_SMTP_USERNAME, null)?.trim()?.takeIf { it.isNotEmpty() },
            hasSmtpPassword = hasSmtpPassword(),
            gmailAccount = readEmail(KEY_GMAIL_ACCOUNT),
            provider = preferences.getString(KEY_PROVIDER, null)?.let { value ->
                KindleEmailProvider.entries.firstOrNull { it.name == value }
            } ?: if (hasSmtpPassword()) KindleEmailProvider.SMTP else KindleEmailProvider.GMAIL,
        )

    fun connectGmail(account: String): Boolean {
        if (!Patterns.EMAIL_ADDRESS.matcher(account).matches()) return false
        preferences.edit().putString(KEY_GMAIL_ACCOUNT, account).putString(KEY_PROVIDER, KindleEmailProvider.GMAIL.name).apply()
        return true
    }

    fun disconnectGmail() {
        // Forget this connection only. Google's revokeAccess also revokes Drive scopes.
        preferences.edit().remove(KEY_GMAIL_ACCOUNT).apply()
    }

    fun saveKindleEmail(email: String): Boolean {
        val clean = email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(clean).matches()) return false
        preferences.edit().putString(KEY_KINDLE_EMAIL, clean).apply()
        return true
    }

    fun saveSmtpSettings(
        senderEmail: String,
        smtpHost: String,
        smtpPort: Int,
        smtpUsername: String,
        smtpPassword: String,
    ): Boolean {
        val cleanSender = senderEmail.trim()
        val cleanHost = smtpHost.trim()
        val cleanUsername = smtpUsername.trim()
        val cleanPassword = smtpPassword.trim()
        val keepExistingPassword = cleanPassword.isBlank() && hasSmtpPassword()

        if (!Patterns.EMAIL_ADDRESS.matcher(cleanSender).matches()) return false
        if (cleanHost.isBlank() || smtpPort !in 1..65535 || cleanUsername.isBlank()) return false
        if (!keepExistingPassword && cleanPassword.isBlank()) return false

        val editor = preferences.edit()
            .putString(KEY_PROVIDER, KindleEmailProvider.SMTP.name)
            .putString(KEY_SENDER_EMAIL, cleanSender)
            .putString(KEY_SMTP_HOST, cleanHost)
            .putInt(KEY_SMTP_PORT, smtpPort)
            .putString(KEY_SMTP_USERNAME, cleanUsername)

        if (!keepExistingPassword) {
            val saved = saveEncryptedSecret(
                value = cleanPassword,
                ivKey = KEY_SMTP_PASSWORD_IV,
                ciphertextKey = KEY_SMTP_PASSWORD_CIPHERTEXT,
            )
            if (!saved) return false
        }
        editor.apply()
        return true
    }

    fun credentials(): KindleEmailCredentials? {
        val current = settings
        val password = readEncryptedSecret(
            ivKey = KEY_SMTP_PASSWORD_IV,
            ciphertextKey = KEY_SMTP_PASSWORD_CIPHERTEXT,
        )
        return KindleEmailCredentials(
            kindleEmail = current.kindleEmail ?: return null,
            senderEmail = current.senderEmail ?: return null,
            smtpHost = current.smtpHost,
            smtpPort = current.smtpPort,
            smtpUsername = current.smtpUsername ?: return null,
            smtpPassword = password ?: return null,
        )
    }

    fun clearKindleEmail() {
        preferences.edit().remove(KEY_KINDLE_EMAIL).apply()
    }

    fun clearSmtpSettings() {
        preferences.edit()
            .remove(KEY_SENDER_EMAIL)
            .remove(KEY_SMTP_HOST)
            .remove(KEY_SMTP_PORT)
            .remove(KEY_SMTP_USERNAME)
            .remove(KEY_SMTP_PASSWORD_IV)
            .remove(KEY_SMTP_PASSWORD_CIPHERTEXT)
            .apply()
    }

    private fun readEmail(key: String): String? {
        return preferences.getString(key, null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun hasSmtpPassword(): Boolean {
        return preferences.contains(KEY_SMTP_PASSWORD_IV) &&
            preferences.contains(KEY_SMTP_PASSWORD_CIPHERTEXT)
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
        (keyStore.getEntry(KEY_ALIAS_SMTP_PASSWORD, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS_SMTP_PASSWORD,
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
        const val KEY_GMAIL_ACCOUNT = "gmail_account"
        const val KEY_PROVIDER = "email_provider"
        const val PREFERENCES_NAME = "foliore_kindle_email_preferences"
        const val KEY_KINDLE_EMAIL = "kindle_email"
        const val KEY_SENDER_EMAIL = "sender_email"
        const val KEY_SMTP_HOST = "smtp_host"
        const val KEY_SMTP_PORT = "smtp_port"
        const val KEY_SMTP_USERNAME = "smtp_username"
        const val KEY_SMTP_PASSWORD_IV = "smtp_password_iv"
        const val KEY_SMTP_PASSWORD_CIPHERTEXT = "smtp_password_ciphertext"
        const val KEY_ALIAS_SMTP_PASSWORD = "foliore_kindle_smtp_password"
        const val DEFAULT_SMTP_HOST = "smtp.gmail.com"
        const val DEFAULT_SMTP_PORT = 587
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
