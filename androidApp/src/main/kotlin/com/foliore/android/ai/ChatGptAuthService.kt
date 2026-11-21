package com.foliore.android.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.foliore.android.R
import com.foliore.android.storage.AppLanguagePreferenceRepository
import com.foliore.android.storage.ChatGptAccount
import com.foliore.android.storage.ChatGptAccountRepository
import com.foliore.android.storage.ChatGptTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

sealed interface ChatGptSignInStatus {
    data object Idle : ChatGptSignInStatus
    data object WaitingForBrowser : ChatGptSignInStatus
    data object Completing : ChatGptSignInStatus
    data object SignedIn : ChatGptSignInStatus
    data class Failed(val reason: ChatGptSignInFailure) : ChatGptSignInStatus
}

enum class ChatGptSignInFailure { CANCELLED, DENIED, BROWSER_UNAVAILABLE, TIMED_OUT, FAILED }

sealed interface ChatGptAccessToken {
    data class Ready(val token: String) : ChatGptAccessToken
    data object SignInRequired : ChatGptAccessToken
    data object Unavailable : ChatGptAccessToken
}

/**
 * Optional "Sign in with ChatGPT" connection. Process-wide, because the sign-in outlives the
 * screen that started it while the browser is in front.
 */
class ChatGptAuthService private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val tokenMutex = Mutex()
    private val _status = MutableStateFlow<ChatGptSignInStatus>(ChatGptSignInStatus.Idle)
    private var signInJob: Job? = null

    val repository = ChatGptAccountRepository(appContext)
    val account: StateFlow<ChatGptAccount?> = repository.account
    val status: StateFlow<ChatGptSignInStatus> = _status.asStateFlow()

    /**
     * Starts the browser sign-in. [openBrowser] must start an activity for the URL and return
     * false when no browser can handle it.
     */
    fun startSignIn(openBrowser: (Uri) -> Boolean) {
        if (signInJob?.isActive == true) return
        _status.value = ChatGptSignInStatus.WaitingForBrowser
        signInJob = scope.launch {
            val result = runCatching { signIn(openBrowser) }.getOrElse { ChatGptSignInStatus.Failed(ChatGptSignInFailure.FAILED) }
            _status.value = result
            ChatGptSignInForegroundService.stop(appContext)
        }
    }

    fun cancelSignIn() {
        signInJob?.cancel()
        signInJob = null
        ChatGptSignInForegroundService.stop(appContext)
        _status.value = ChatGptSignInStatus.Failed(ChatGptSignInFailure.CANCELLED)
    }

    fun clearStatus() {
        if (signInJob?.isActive != true) _status.value = ChatGptSignInStatus.Idle
    }

    private suspend fun signIn(openBrowser: (Uri) -> Boolean): ChatGptSignInStatus {
        val discovery = fetchDiscovery() ?: return ChatGptSignInStatus.Failed(ChatGptSignInFailure.FAILED)
        val savedClientId = repository.issuedClientId
        val pkce = ChatGptOAuth.newPkce()
        val state = ChatGptOAuth.randomUrlToken()
        val nonce = ChatGptOAuth.randomUrlToken()
        val localized = AppLanguagePreferenceRepository.localizedContext(appContext)

        ChatGptLoopbackServer.open().use { server ->
            val redirectUri = ChatGptOAuth.redirectUri(server.port)
            val url = ChatGptOAuth.authorizationUrl(
                authorizationEndpoint = discovery.authorizationEndpoint,
                request = ChatGptOAuth.AuthorizationRequest(
                    redirectUri = redirectUri,
                    state = state,
                    nonce = nonce,
                    pkce = pkce,
                    hostId = repository.hostId,
                    issuedClientId = savedClientId,
                ),
            )
            ChatGptSignInForegroundService.start(appContext)
            val opened = withContext(Dispatchers.Main) { openBrowser(Uri.parse(url)) }
            if (!opened) return ChatGptSignInStatus.Failed(ChatGptSignInFailure.BROWSER_UNAVAILABLE)

            val successPage = ChatGptLoopbackServer.CallbackPage(
                title = localized.getString(R.string.chatgpt_callback_title),
                message = localized.getString(R.string.chatgpt_callback_message),
                returnLabel = localized.getString(R.string.chatgpt_callback_return),
                returnUrl = RETURN_URL,
            )
            val failurePage = successPage.copy(
                title = localized.getString(R.string.chatgpt_callback_failed_title),
                message = localized.getString(R.string.chatgpt_callback_failed_message),
            )
            val callback = server.awaitCallback(SIGN_IN_TIMEOUT_MILLIS) { path, query ->
                if (path != ChatGptOAuth.CALLBACK_PATH) return@awaitCallback null
                val result = ChatGptOAuth.parseCallback(query, state, savedClientId) ?: return@awaitCallback null
                result to if (result is ChatGptOAuth.CallbackResult.Code) successPage else failurePage
            } ?: return ChatGptSignInStatus.Failed(ChatGptSignInFailure.TIMED_OUT)

            _status.value = ChatGptSignInStatus.Completing
            return when (callback) {
                ChatGptOAuth.CallbackResult.Denied -> ChatGptSignInStatus.Failed(ChatGptSignInFailure.DENIED)
                is ChatGptOAuth.CallbackResult.Failed -> ChatGptSignInStatus.Failed(ChatGptSignInFailure.FAILED)
                is ChatGptOAuth.CallbackResult.Code -> completeSignIn(discovery, callback, pkce, nonce, redirectUri)
            }
        }
    }

    private fun completeSignIn(
        discovery: ChatGptOAuth.Discovery,
        callback: ChatGptOAuth.CallbackResult.Code,
        pkce: ChatGptOAuth.Pkce,
        nonce: String,
        redirectUri: String,
    ): ChatGptSignInStatus {
        val response = OpenAiHttp.postForm(
            discovery.tokenEndpoint,
            ChatGptOAuth.authorizationCodeForm(callback.code, callback.clientId, pkce.verifier, redirectUri),
        )
        if (!response.isSuccess) return ChatGptSignInStatus.Failed(ChatGptSignInFailure.FAILED)
        val tokens = ChatGptOAuth.parseTokenResponse(response.body)
        val refreshToken = tokens.refreshToken ?: return ChatGptSignInStatus.Failed(ChatGptSignInFailure.FAILED)
        val idToken = tokens.idToken ?: return ChatGptSignInStatus.Failed(ChatGptSignInFailure.FAILED)
        val jwks = OpenAiHttp.get(discovery.jwksUri).takeIf { it.isSuccess }?.body
            ?: return ChatGptSignInStatus.Failed(ChatGptSignInFailure.FAILED)
        val claims = ChatGptOAuth.validateIdToken(
            idToken = idToken,
            jwks = jwks,
            clientId = callback.clientId,
            nonce = nonce,
            nowEpochSeconds = System.currentTimeMillis() / 1000,
        )
        val saved = repository.saveSignIn(
            clientId = callback.clientId,
            tokens = tokens.toLocalTokens(refreshToken),
            email = claims.email,
            name = claims.name,
            subject = claims.subject,
        )
        if (!saved) return ChatGptSignInStatus.Failed(ChatGptSignInFailure.FAILED)
        refreshModelsBlocking(tokens.accessToken)
        return ChatGptSignInStatus.SignedIn
    }

    /** Returns a valid access token, refreshing it at most once at a time. */
    internal suspend fun accessToken(forceRefresh: Boolean = false): ChatGptAccessToken = tokenMutex.withLock {
        val clientId = repository.issuedClientId ?: return ChatGptAccessToken.SignInRequired
        val current = repository.tokens() ?: return ChatGptAccessToken.SignInRequired
        val now = System.currentTimeMillis()
        val fresh = current.accessExpiresAtMillis - now > REFRESH_MARGIN_MILLIS
        if (fresh && !forceRefresh) return ChatGptAccessToken.Ready(current.accessToken)
        if (fresh && now < current.earliestRefreshAtMillis) return ChatGptAccessToken.Ready(current.accessToken)

        val discovery = fetchDiscovery() ?: return ChatGptAccessToken.Unavailable
        val response = runCatching {
            OpenAiHttp.postForm(discovery.tokenEndpoint, ChatGptOAuth.refreshForm(clientId, current.refreshToken))
        }.getOrNull() ?: return ChatGptAccessToken.Unavailable
        if (!response.isSuccess) {
            val code = ChatGptOAuth.oauthErrorCode(response.body)
            return if (ChatGptOAuth.isTerminalRefreshError(code) || code == "invalid_client") {
                repository.signOut()
                ChatGptAccessToken.SignInRequired
            } else {
                ChatGptAccessToken.Unavailable
            }
        }
        val tokens = runCatching { ChatGptOAuth.parseTokenResponse(response.body) }.getOrNull()
            ?: return ChatGptAccessToken.Unavailable
        repository.saveTokens(tokens.toLocalTokens(tokens.refreshToken ?: current.refreshToken))
        ChatGptAccessToken.Ready(tokens.accessToken)
    }

    /** Loads the models this account may use (GET /v1/models, visibility == "list"). */
    suspend fun refreshModels() = withContext(Dispatchers.IO) {
        val token = (accessToken() as? ChatGptAccessToken.Ready)?.token ?: return@withContext
        refreshModelsBlocking(token)
    }

    private fun refreshModelsBlocking(accessToken: String) {
        val response = runCatching { OpenAiHttp.get(MODELS_ENDPOINT, accessToken) }.getOrNull() ?: return
        if (!response.isSuccess) return
        val models = runCatching { parseListedModels(response.body) }.getOrNull().orEmpty()
        if (models.isNotEmpty()) repository.saveModels(models)
    }

    fun selectModel(model: String) = repository.selectModel(model)

    fun markWelcomeShown() = repository.markWelcomeShown()

    /** Revokes the refresh token (best effort) and forgets the account on this device. */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        tokenMutex.withLock {
            val clientId = repository.issuedClientId
            val refreshToken = repository.tokens()?.refreshToken
            repository.signOut()
            _status.value = ChatGptSignInStatus.Idle
            if (clientId != null && refreshToken != null) {
                runCatching {
                    fetchDiscovery()?.revocationEndpoint?.let { endpoint ->
                        repeat(3) { attempt ->
                            val response = OpenAiHttp.postForm(endpoint, ChatGptOAuth.revocationForm(clientId, refreshToken))
                            if (response.status < 500) return@runCatching
                            Thread.sleep(1_000L shl attempt)
                        }
                    }
                }
            }
        }
    }

    private fun fetchDiscovery(): ChatGptOAuth.Discovery? = runCatching {
        val response = OpenAiHttp.get(ChatGptOAuth.DISCOVERY_URL)
        check(response.isSuccess)
        ChatGptOAuth.parseDiscovery(response.body)
    }.getOrNull()

    private fun ChatGptOAuth.TokenResponse.toLocalTokens(refreshToken: String): ChatGptTokens {
        val now = System.currentTimeMillis()
        return ChatGptTokens(
            accessToken = accessToken,
            accessExpiresAtMillis = now + expiresIn.coerceAtLeast(60) * 1000,
            refreshToken = refreshToken,
            earliestRefreshAtMillis = earliestRefreshAtEpochSeconds()?.times(1000) ?: 0L,
        )
    }

    private fun parseListedModels(body: String): List<String> {
        val data = json.parseToJsonElement(body).jsonObject["data"] as? JsonArray ?: return emptyList()
        return data.mapNotNull { element ->
            val model = element as? JsonObject ?: return@mapNotNull null
            val visibility = (model["visibility"] as? JsonPrimitive)?.contentOrNull
            if (visibility != null && visibility != "list") return@mapNotNull null
            (model["id"] as? JsonPrimitive)?.contentOrNull ?: (model["slug"] as? JsonPrimitive)?.contentOrNull
        }.distinct()
    }

    companion object {
        const val MODELS_ENDPOINT = "${ChatGptOAuth.RESOURCE}/models"
        const val RETURN_URL = "intent://chatgpt-signed-in#Intent;scheme=foliore;package=com.foliore.android;end"
        private const val SIGN_IN_TIMEOUT_MILLIS = 10 * 60 * 1000L
        private const val REFRESH_MARGIN_MILLIS = 2 * 60 * 1000L

        @Volatile private var instance: ChatGptAuthService? = null

        fun get(context: Context): ChatGptAuthService =
            instance ?: synchronized(this) { instance ?: ChatGptAuthService(context).also { instance = it } }

        fun browserIntent(uri: Uri): Intent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
