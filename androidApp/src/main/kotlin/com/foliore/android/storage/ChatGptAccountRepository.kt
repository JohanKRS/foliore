package com.foliore.android.storage

import android.content.Context
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

data class ChatGptAccount(
    val email: String?,
    val name: String?,
    val models: List<String>,
    val selectedModel: String?,
) {
    val label: String? get() = email ?: name
    val model: String? get() = selectedModel?.takeIf { it in models } ?: models.firstOrNull()
}

internal data class ChatGptTokens(
    val accessToken: String,
    val accessExpiresAtMillis: Long,
    val refreshToken: String,
    val earliestRefreshAtMillis: Long,
)

/**
 * Local state for the optional "Sign in with ChatGPT" connection. Tokens are encrypted with a
 * Keystore key and never leave the device except in requests to OpenAI.
 */
class ChatGptAccountRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val secrets = KeystoreSecretBox(preferences, KEY_ALIAS)
    private val json = Json { ignoreUnknownKeys = true }
    private val _account = MutableStateFlow(loadAccount())

    val account: StateFlow<ChatGptAccount?> = _account.asStateFlow()

    /** Issued once by OpenAI on the first sign-in and reused afterwards; it is not a secret. */
    val issuedClientId: String? get() = preferences.getString(KEY_CLIENT_ID, null)

    /** Stable per-install host identifier sent as ext_agent_host_id. */
    val hostId: String
        get() = preferences.getString(KEY_HOST_ID, null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString(KEY_HOST_ID, it).apply()
        }

    val welcomeShown: Boolean get() = preferences.getBoolean(KEY_WELCOME_SHOWN, false)

    fun markWelcomeShown() {
        preferences.edit().putBoolean(KEY_WELCOME_SHOWN, true).apply()
    }

    internal fun saveSignIn(
        clientId: String,
        tokens: ChatGptTokens,
        email: String?,
        name: String?,
        subject: String,
    ): Boolean {
        val editor = preferences.edit()
            .putString(KEY_CLIENT_ID, clientId)
            .putString(KEY_SUBJECT, subject)
            .putString(KEY_EMAIL, email)
            .putString(KEY_NAME, name)
        if (preferences.getString(KEY_SUBJECT, null) != subject) editor.remove(KEY_MODELS).remove(KEY_SELECTED_MODEL)
        editor.apply()
        if (!saveTokens(tokens)) return false
        _account.value = loadAccount()
        return _account.value != null
    }

    internal fun tokens(): ChatGptTokens? {
        val access = secrets.read(SECRET_ACCESS_TOKEN) ?: return null
        val refresh = secrets.read(SECRET_REFRESH_TOKEN) ?: return null
        return ChatGptTokens(
            accessToken = access,
            accessExpiresAtMillis = preferences.getLong(KEY_ACCESS_EXPIRES_AT, 0L),
            refreshToken = refresh,
            earliestRefreshAtMillis = preferences.getLong(KEY_EARLIEST_REFRESH_AT, 0L),
        )
    }

    internal fun saveTokens(tokens: ChatGptTokens): Boolean {
        val savedAccess = secrets.write(SECRET_ACCESS_TOKEN, tokens.accessToken)
        val savedRefresh = secrets.write(SECRET_REFRESH_TOKEN, tokens.refreshToken)
        preferences.edit()
            .putLong(KEY_ACCESS_EXPIRES_AT, tokens.accessExpiresAtMillis)
            .putLong(KEY_EARLIEST_REFRESH_AT, tokens.earliestRefreshAtMillis)
            .apply()
        return savedAccess && savedRefresh
    }

    fun saveModels(models: List<String>) {
        preferences.edit()
            .putString(KEY_MODELS, json.encodeToString(ListSerializer(String.serializer()), models))
            .apply()
        _account.value = loadAccount()
    }

    fun selectModel(model: String) {
        preferences.edit().putString(KEY_SELECTED_MODEL, model).apply()
        _account.value = loadAccount()
    }

    /** Forgets tokens and the account. The issued client id and host id are kept for the next sign-in. */
    fun signOut() {
        val editor = preferences.edit()
            .remove(KEY_SUBJECT)
            .remove(KEY_EMAIL)
            .remove(KEY_NAME)
            .remove(KEY_MODELS)
            .remove(KEY_SELECTED_MODEL)
            .remove(KEY_ACCESS_EXPIRES_AT)
            .remove(KEY_EARLIEST_REFRESH_AT)
        secrets.remove(SECRET_ACCESS_TOKEN, editor)
        secrets.remove(SECRET_REFRESH_TOKEN)
        _account.value = null
    }

    private fun loadAccount(): ChatGptAccount? {
        if (preferences.getString(KEY_SUBJECT, null) == null) return null
        if (secrets.read(SECRET_REFRESH_TOKEN) == null) return null
        val models = preferences.getString(KEY_MODELS, null)?.let {
            runCatching { json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull()
        }.orEmpty()
        return ChatGptAccount(
            email = preferences.getString(KEY_EMAIL, null),
            name = preferences.getString(KEY_NAME, null),
            models = models,
            selectedModel = preferences.getString(KEY_SELECTED_MODEL, null),
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "foliore_chatgpt"
        const val KEY_ALIAS = "foliore_chatgpt_tokens"
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_HOST_ID = "host_id"
        const val KEY_SUBJECT = "subject"
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "name"
        const val KEY_MODELS = "models"
        const val KEY_SELECTED_MODEL = "selected_model"
        const val KEY_WELCOME_SHOWN = "welcome_shown"
        const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
        const val KEY_EARLIEST_REFRESH_AT = "earliest_refresh_at"
        const val SECRET_ACCESS_TOKEN = "access_token"
        const val SECRET_REFRESH_TOKEN = "refresh_token"
    }
}
