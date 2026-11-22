package com.foliore.android.storage

import android.content.Context
import android.content.SharedPreferences
import com.foliore.android.ai.ChatGptModel
import com.foliore.android.ai.ChatGptOAuth
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ChatGptAccount(
    val email: String?,
    val name: String?,
    val models: List<ChatGptModel>,
    val planUsageGranted: Boolean,
    val selectedModel: String?,
) {
    val label: String? get() = email ?: name
    val model: String? get() = models.firstOrNull { it.slug == selectedModel }?.slug ?: models.firstOrNull()?.slug
}

internal data class ChatGptTokens(
    val accessToken: String,
    val accessExpiresAtMillis: Long,
    val refreshToken: String?,
    val scope: String?,
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
        @Synchronized get() {
            val stored = preferences.getString(KEY_HOST_ID, null)
            val id = ChatGptOAuth.hostId(stored)
            if (id != stored) check(preferences.edit().putString(KEY_HOST_ID, id).commit())
            return id
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
        val subjectHash = subject.sha256()
        val previousHash = preferences.getString(KEY_REGISTERED_SUBJECT_HASH, null)
            ?: preferences.getString(KEY_SUBJECT, null)?.sha256()
        if (clientId == issuedClientId && previousHash != null && previousHash != subjectHash) return false

        return saveTokens(tokens) { editor ->
            if (clientId != issuedClientId || preferences.getString(KEY_SUBJECT, null) != subject) {
                editor.remove(KEY_MODELS).remove(KEY_SELECTED_MODEL)
            }
            editor.putString(KEY_CLIENT_ID, clientId)
                .putString(KEY_REGISTERED_SUBJECT_HASH, subjectHash)
                .putString(KEY_SUBJECT, subject)
                .putString(KEY_EMAIL, email)
                .putString(KEY_NAME, name)
        }
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    // If Android kills the process after the request, the old refresh token may already be spent.
    internal val refreshInFlight: Boolean get() = preferences.getBoolean(KEY_REFRESH_IN_FLIGHT, false)

    internal fun setRefreshInFlight(inFlight: Boolean): Boolean = runCatching {
        val editor = preferences.edit()
        if (inFlight) editor.putBoolean(KEY_REFRESH_IN_FLIGHT, true) else editor.remove(KEY_REFRESH_IN_FLIGHT)
        editor.commit()
    }.getOrDefault(false)

    internal fun tokens(): ChatGptTokens? {
        val access = secrets.read(SECRET_ACCESS_TOKEN) ?: return null
        return ChatGptTokens(
            accessToken = access,
            accessExpiresAtMillis = preferences.getLong(KEY_ACCESS_EXPIRES_AT, 0L),
            refreshToken = secrets.read(SECRET_REFRESH_TOKEN),
            scope = preferences.getString(KEY_SCOPE, null),
            earliestRefreshAtMillis = preferences.getLong(KEY_EARLIEST_REFRESH_AT, 0L),
        )
    }

    internal fun saveTokens(tokens: ChatGptTokens, updateIdentity: (SharedPreferences.Editor) -> Unit = {}): Boolean {
        val saved = runCatching {
            val editor = preferences.edit()
            secrets.put(editor, SECRET_ACCESS_TOKEN, tokens.accessToken)
            if (tokens.refreshToken == null) secrets.discard(editor, SECRET_REFRESH_TOKEN)
            else secrets.put(editor, SECRET_REFRESH_TOKEN, tokens.refreshToken)
            updateIdentity(editor)
            editor.remove(KEY_REFRESH_IN_FLIGHT)
                .putLong(KEY_ACCESS_EXPIRES_AT, tokens.accessExpiresAtMillis)
                .putLong(KEY_EARLIEST_REFRESH_AT, tokens.earliestRefreshAtMillis)
                .putString(KEY_SCOPE, tokens.scope)
            if (!ChatGptOAuth.grantsPlanUsage(tokens.scope)) editor.remove(KEY_MODELS).remove(KEY_SELECTED_MODEL)
            editor.commit()
        }.getOrDefault(false)
        if (saved) _account.value = loadAccount()
        return saved
    }

    fun saveModels(models: List<ChatGptModel>) {
        preferences.edit().putString(KEY_MODELS, json.encodeToString(models)).apply()
        _account.value = _account.value?.copy(models = models)
    }

    fun selectModel(model: String) {
        val active = _account.value ?: return
        if (active.models.none { it.slug == model }) return
        preferences.edit().putString(KEY_SELECTED_MODEL, model).apply()
        _account.value = active.copy(selectedModel = model)
    }

    /** Forgets tokens and the account. The issued client id and host id are kept for the next sign-in. */
    fun signOut() {
        val editor = preferences.edit()
            .remove(KEY_SUBJECT)
            .remove(KEY_EMAIL)
            .remove(KEY_NAME)
            .remove(KEY_MODELS)
            .remove(KEY_SELECTED_MODEL)
            .remove(KEY_SCOPE)
            .remove(KEY_REFRESH_IN_FLIGHT)
            .remove(KEY_ACCESS_EXPIRES_AT)
            .remove(KEY_EARLIEST_REFRESH_AT)
        secrets.discard(editor, SECRET_ACCESS_TOKEN)
        secrets.discard(editor, SECRET_REFRESH_TOKEN)
        editor.apply()
        _account.value = null
    }

    private fun loadAccount(): ChatGptAccount? {
        if (preferences.getString(KEY_SUBJECT, null) == null) return null
        if (secrets.read(SECRET_ACCESS_TOKEN) == null) return null
        val models = preferences.getString(KEY_MODELS, null)?.let {
            runCatching { json.decodeFromString<List<ChatGptModel>>(it) }.getOrNull()
        }.orEmpty()
        return ChatGptAccount(
            email = preferences.getString(KEY_EMAIL, null),
            name = preferences.getString(KEY_NAME, null),
            models = models,
            planUsageGranted = ChatGptOAuth.grantsPlanUsage(preferences.getString(KEY_SCOPE, null)),
            selectedModel = preferences.getString(KEY_SELECTED_MODEL, null),
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "foliore_chatgpt"
        const val KEY_ALIAS = "foliore_chatgpt_tokens"
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_HOST_ID = "host_id"
        const val KEY_SUBJECT = "subject"
        const val KEY_REGISTERED_SUBJECT_HASH = "registered_subject_hash"
        const val KEY_SCOPE = "granted_scope"
        const val KEY_REFRESH_IN_FLIGHT = "refresh_in_flight"
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
