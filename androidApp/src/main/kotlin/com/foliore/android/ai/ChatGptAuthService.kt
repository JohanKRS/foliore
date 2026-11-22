package com.foliore.android.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.foliore.android.R
import com.foliore.android.storage.AppLanguagePreferenceRepository
import com.foliore.android.storage.ChatGptAccount
import com.foliore.android.storage.ChatGptAccountRepository
import com.foliore.android.storage.ChatGptTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
    data object PlanNotGranted : ChatGptAccessToken
    data object Unavailable : ChatGptAccessToken
}

/**
 * Optional "Sign in with ChatGPT" connection. Process-wide, because the sign-in outlives the
 * screen that started it while the browser is in front.
 */
class ChatGptAuthService private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val attempts = ChatGptSignInAttempts()
    private val tokenMutex = Mutex()
    private val _status = MutableStateFlow<ChatGptSignInStatus>(ChatGptSignInStatus.Idle)
    private var signInJob: Job? = null
    // Accessed only under tokenMutex. Retry the replacement before ever using the old refresh token.
    private var pendingRotation: ChatGptTokens? = null

    val repository = ChatGptAccountRepository(appContext)
    val account: StateFlow<ChatGptAccount?> = repository.account
    val status: StateFlow<ChatGptSignInStatus> = _status.asStateFlow()

    /**
     * Starts the browser sign-in. [openBrowser] must start an activity for the URL and return
     * false when no browser can handle it.
     */
    fun startSignIn(mode: ChatGptSignInMode, openBrowser: (Uri) -> Boolean) = attempts.start { id ->
        _status.value = ChatGptSignInStatus.WaitingForBrowser
        signInJob = scope.launch {
            val deadline = launch {
                delay(SIGN_IN_TIMEOUT_MILLIS)
                endSignIn(id, ChatGptSignInFailure.TIMED_OUT, cancel = true)
            }
            try {
                val grant = signIn(id, mode, openBrowser)
                var loadModels = false
                tokenMutex.withLock {
                    // Cancellation and publishing credentials share this commit point.
                    attempts.finish(id) {
                        val saved = try {
                            repository.saveSignIn(
                                grant.clientId, grant.tokens, grant.claims.email, grant.claims.name, grant.claims.subject,
                            )
                        } catch (_: Exception) {
                            false
                        }
                        if (saved) {
                            pendingRotation = null
                            loadModels = ChatGptOAuth.grantsPlanUsage(grant.tokens.scope)
                            finishAttempt(ChatGptSignInStatus.SignedIn)
                        } else {
                            finishAttempt(ChatGptSignInStatus.Failed(ChatGptSignInFailure.FAILED))
                        }
                    }
                }
                if (loadModels) scope.launch { refreshModels() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: SignInFailure) {
                endSignIn(id, failure.reason)
            } catch (_: Exception) {
                endSignIn(id, ChatGptSignInFailure.FAILED)
            } finally {
                deadline.cancel()
            }
        }
    }

    fun cancelSignIn() = endSignIn(null, ChatGptSignInFailure.CANCELLED, cancel = true)

    internal fun serviceEnded(id: Long, reason: ChatGptSignInFailure) = endSignIn(id, reason, cancel = true)

    private fun endSignIn(id: Long?, reason: ChatGptSignInFailure, cancel: Boolean = false) = attempts.finish(id) {
        if (cancel) signInJob?.cancel()
        finishAttempt(ChatGptSignInStatus.Failed(reason))
    }

    private fun finishAttempt(status: ChatGptSignInStatus) {
        signInJob = null
        ChatGptSignInForegroundService.stop(appContext)
        _status.value = status
    }

    fun clearStatus(current: ChatGptSignInStatus) {
        if (current is ChatGptSignInStatus.Failed || current == ChatGptSignInStatus.SignedIn) {
            _status.compareAndSet(current, ChatGptSignInStatus.Idle)
        }
    }

    private class SignInFailure(val reason: ChatGptSignInFailure) : Exception()
    private data class Grant(val clientId: String, val tokens: ChatGptTokens, val claims: ChatGptOAuth.IdTokenClaims)

    private suspend fun signIn(id: Long, mode: ChatGptSignInMode, openBrowser: (Uri) -> Boolean): Grant {
        val discovery = fetchDiscovery() ?: throw SignInFailure(ChatGptSignInFailure.FAILED)
        val savedClientId = mode.clientId(repository.issuedClientId)
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
                    requestConsent = mode == ChatGptSignInMode.ENABLE_PLAN,
                ),
            )
            withContext(Dispatchers.Main) {
                val active = attempts.whileActive(id) {
                    if (!ChatGptSignInForegroundService.start(appContext, id)) throw SignInFailure(ChatGptSignInFailure.FAILED)
                    if (!openBrowser(Uri.parse(url))) throw SignInFailure(ChatGptSignInFailure.BROWSER_UNAVAILABLE)
                }
                if (!active) throw CancellationException("Sign-in ended")
            }

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
            } ?: throw SignInFailure(ChatGptSignInFailure.TIMED_OUT)

            if (!attempts.whileActive(id) { _status.value = ChatGptSignInStatus.Completing }) {
                throw CancellationException("Sign-in ended")
            }
            return when (callback) {
                ChatGptOAuth.CallbackResult.Denied -> throw SignInFailure(ChatGptSignInFailure.DENIED)
                is ChatGptOAuth.CallbackResult.Failed -> throw SignInFailure(ChatGptSignInFailure.FAILED)
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
    ): Grant {
        val response = OpenAiHttp.postForm(
            discovery.tokenEndpoint,
            ChatGptOAuth.authorizationCodeForm(callback.code, callback.clientId, pkce.verifier, redirectUri),
        )
        if (!response.isSuccess) throw SignInFailure(ChatGptSignInFailure.FAILED)
        val tokens = ChatGptOAuth.parseTokenResponse(response.body)
        val idToken = tokens.idToken ?: throw SignInFailure(ChatGptSignInFailure.FAILED)
        val jwks = OpenAiHttp.get(discovery.jwksUri).takeIf { it.isSuccess }?.body
            ?: throw SignInFailure(ChatGptSignInFailure.FAILED)
        val claims = ChatGptOAuth.validateIdToken(
            idToken = idToken,
            jwks = jwks,
            clientId = callback.clientId,
            nonce = nonce,
            nowEpochSeconds = System.currentTimeMillis() / 1000,
        )
        return Grant(callback.clientId, tokens.toLocalTokens(tokens.refreshToken, tokens.scope), claims)
    }

    /** Returns a valid access token, refreshing it at most once at a time. */
    internal suspend fun accessToken(forceRefresh: Boolean = false): ChatGptAccessToken = tokenMutex.withLock {
        val clientId = repository.issuedClientId ?: return ChatGptAccessToken.SignInRequired
        pendingRotation?.let { replacement ->
            if (!repository.saveTokens(replacement)) return ChatGptAccessToken.Unavailable
            pendingRotation = null
        }
        val current = repository.tokens() ?: return ChatGptAccessToken.SignInRequired
        if (!ChatGptOAuth.grantsPlanUsage(current.scope)) return ChatGptAccessToken.PlanNotGranted
        val now = System.currentTimeMillis()
        val remaining = current.accessExpiresAtMillis - now
        if ((!forceRefresh && remaining > REFRESH_MARGIN_MILLIS) ||
            (remaining > 0 && now < current.earliestRefreshAtMillis)) {
            return ChatGptAccessToken.Ready(current.accessToken)
        }
        val refreshToken = current.refreshToken
        if (refreshToken == null || repository.refreshInFlight) {
            return if (remaining > 0 && !forceRefresh) ChatGptAccessToken.Ready(current.accessToken)
            else ChatGptAccessToken.SignInRequired
        }

        val discovery = fetchDiscovery() ?: return ChatGptAccessToken.Unavailable
        if (!repository.setRefreshInFlight(true)) return ChatGptAccessToken.Unavailable
        val response = runCatching {
            OpenAiHttp.postForm(discovery.tokenEndpoint, ChatGptOAuth.refreshForm(clientId, refreshToken))
        }.getOrNull() ?: return ChatGptAccessToken.Unavailable
        if (!response.isSuccess) {
            val code = ChatGptOAuth.oauthErrorCode(response.body)
            return if (ChatGptOAuth.isTerminalRefreshError(code) || code == "invalid_client") {
                repository.signOut()
                ChatGptAccessToken.SignInRequired
            } else {
                // A definite OAuth rejection has not issued a replacement. An ambiguous response
                // leaves the marker in place, just like a lost response or an interrupted process.
                if (code != null || response.status == 429) repository.setRefreshInFlight(false)
                ChatGptAccessToken.Unavailable
            }
        }
        val tokens = runCatching { ChatGptOAuth.parseTokenResponse(response.body) }.getOrNull()
            ?: return ChatGptAccessToken.Unavailable
        val replacement = tokens.toLocalTokens(tokens.refreshToken ?: refreshToken, tokens.scope ?: current.scope)
        if (!repository.saveTokens(replacement)) {
            pendingRotation = replacement
            return ChatGptAccessToken.Unavailable
        }
        if (ChatGptOAuth.grantsPlanUsage(replacement.scope)) ChatGptAccessToken.Ready(replacement.accessToken)
        else ChatGptAccessToken.PlanNotGranted
    }

    suspend fun refreshModels(): Unit = withContext(Dispatchers.IO) {
        val token = (accessToken() as? ChatGptAccessToken.Ready)?.token ?: return@withContext
        val response = runCatching { OpenAiHttp.get(MODELS_ENDPOINT, token) }.getOrNull() ?: return@withContext
        if (!response.isSuccess) return@withContext
        val models = runCatching { ChatGptOAuth.listedModels(response.body) }.getOrNull() ?: return@withContext
        tokenMutex.withLock {
            if (repository.tokens()?.accessToken == token) repository.saveModels(models)
        }
    }

    fun selectModel(model: String) = repository.selectModel(model)

    fun markWelcomeShown() = repository.markWelcomeShown()

    /** Revokes the refresh token (best effort) and forgets the account on this device. */
    suspend fun signOut(): Unit = withContext(Dispatchers.IO) {
        cancelSignIn()
        val (clientId, refreshToken) = tokenMutex.withLock {
            val registration = repository.issuedClientId
            val token = (pendingRotation ?: repository.tokens())?.refreshToken
            pendingRotation = null
            repository.signOut()
            registration to token
        }
        if (clientId != null && refreshToken != null) {
            runCatching {
                fetchDiscovery()?.revocationEndpoint?.let { endpoint ->
                    repeat(3) { attempt ->
                        val response = OpenAiHttp.postForm(endpoint, ChatGptOAuth.revocationForm(clientId, refreshToken))
                        if (response.status < 500) return@runCatching
                        delay(1_000L shl attempt)
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

    private fun ChatGptOAuth.TokenResponse.toLocalTokens(refreshToken: String?, grantedScope: String?): ChatGptTokens {
        val now = System.currentTimeMillis()
        return ChatGptTokens(
            accessToken = accessToken,
            accessExpiresAtMillis = now + expiresIn.coerceAtLeast(60) * 1000,
            refreshToken = refreshToken,
            scope = grantedScope,
            earliestRefreshAtMillis = earliestRefreshAtEpochSeconds()?.times(1000) ?: 0L,
        )
    }

    companion object {
        const val MODELS_ENDPOINT = "${ChatGptOAuth.RESOURCE}/models"
        const val RETURN_URL = "intent://chatgpt-signed-in#Intent;scheme=foliore;package=com.foliore.android;end"
        // Finish before Android 14’s three-minute short-service budget, including code exchange.
        private const val SIGN_IN_TIMEOUT_MILLIS = 150_000L
        private const val REFRESH_MARGIN_MILLIS = 2 * 60 * 1000L

        @Volatile private var instance: ChatGptAuthService? = null

        fun get(context: Context): ChatGptAuthService =
            instance ?: synchronized(this) { instance ?: ChatGptAuthService(context).also { instance = it } }

        fun browserIntent(uri: Uri): Intent = Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
