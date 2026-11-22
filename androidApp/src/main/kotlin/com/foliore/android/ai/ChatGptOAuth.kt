package com.foliore.android.ai

import java.math.BigInteger
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Protocol pieces of "Sign in with ChatGPT" for open-source apps, kept free of Android types so
 * they can be unit tested. Follows https://developers.openai.com/siwc/token-sharing-open-source.
 * No OpenAI DevKit code is copied here; only the documented protocol is implemented.
 */
object ChatGptOAuth {
    const val ISSUER = "https://auth.openai.com"
    const val DISCOVERY_URL = "$ISSUER/.well-known/openid-configuration"
    const val RESOURCE = "https://api.openai.com/v1"
    const val SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"

    /** Sent on the first sign-in; the callback returns the client id issued for this install. */
    const val DYNAMIC_CLIENT_ID = "dynamic_agent_client"
    const val AGENT_NAME = "Foliore"
    const val CALLBACK_PATH = "/auth/callback"
    const val PREFERRED_PORT = 1455
    const val USAGE_SETTINGS_URL = "https://chatgpt.com/settings/usage"

    private val random = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }

    fun randomUrlToken(bytes: Int = 32): String {
        val buffer = ByteArray(bytes).also(random::nextBytes)
        return base64Url(buffer)
    }

    fun newPkce(verifier: String = randomUrlToken()): Pkce = Pkce(verifier, codeChallenge(verifier))

    /** RFC 7636 S256: BASE64URL(SHA256(ASCII(verifier))), without padding. */
    fun codeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return base64Url(digest)
    }

    fun redirectUri(port: Int): String = "http://127.0.0.1:$port$CALLBACK_PATH"

    fun authorizationUrl(authorizationEndpoint: String, request: AuthorizationRequest): String {
        val params = linkedMapOf(
            "client_id" to (request.issuedClientId ?: DYNAMIC_CLIENT_ID),
            "response_type" to "code",
            "redirect_uri" to request.redirectUri,
            "scope" to SCOPES,
            "resource" to RESOURCE,
            "state" to request.state,
            "nonce" to request.nonce,
            "code_challenge_method" to "S256",
            "code_challenge" to request.pkce.challenge,
            "ext_agent_host_id" to request.hostId,
        )
        if (request.issuedClientId == null) params["agent_name_hint"] = AGENT_NAME
        request.loginHint?.let { params["login_hint"] = it }
        val separator = if ('?' in authorizationEndpoint) "&" else "?"
        return authorizationEndpoint + separator + params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
    }

    fun parseQuery(query: String?): Map<String, String> {
        if (query.isNullOrEmpty()) return emptyMap()
        return query.split('&').filter { it.isNotEmpty() }.associate { pair ->
            val name = pair.substringBefore('=')
            val value = if ('=' in pair) pair.substringAfter('=') else ""
            decode(name) to decode(value)
        }
    }

    /**
     * Returns null when the request does not carry this sign-in's state, so a stray or forged
     * request to the loopback port is ignored instead of ending the sign-in.
     */
    fun parseCallback(query: Map<String, String>, expectedState: String, savedClientId: String?): CallbackResult? {
        val state = query["state"] ?: return null
        if (!MessageDigest.isEqual(state.toByteArray(), expectedState.toByteArray())) return null
        query["error"]?.let { error ->
            return if (error == "access_denied") CallbackResult.Denied else CallbackResult.Failed(error)
        }
        val code = query["code"]?.takeIf { it.isNotBlank() } ?: return CallbackResult.Failed("missing_code")
        val callbackClientId = query["client_id"]?.takeIf { it.isNotBlank() && it != DYNAMIC_CLIENT_ID }
        val clientId = when {
            savedClientId == null -> callbackClientId ?: return CallbackResult.Failed("registration_incomplete")
            callbackClientId != null && callbackClientId != savedClientId -> return CallbackResult.Failed("client_mismatch")
            else -> savedClientId
        }
        return CallbackResult.Code(code = code, clientId = clientId)
    }

    fun authorizationCodeForm(code: String, clientId: String, verifier: String, redirectUri: String): Map<String, String> =
        linkedMapOf(
            "grant_type" to "authorization_code",
            "client_id" to clientId,
            "code" to code,
            "code_verifier" to verifier,
            "redirect_uri" to redirectUri,
            "resource" to RESOURCE,
        )

    fun refreshForm(clientId: String, refreshToken: String): Map<String, String> =
        linkedMapOf(
            "grant_type" to "refresh_token",
            "client_id" to clientId,
            "refresh_token" to refreshToken,
            "resource" to RESOURCE,
        )

    fun revocationForm(clientId: String, refreshToken: String): Map<String, String> =
        linkedMapOf(
            "token" to refreshToken,
            "token_type_hint" to "refresh_token",
            "client_id" to clientId,
        )

    fun formBody(values: Map<String, String>): String =
        values.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }

    fun parseDiscovery(body: String): Discovery {
        val discovery = json.decodeFromString(Discovery.serializer(), body)
        require(discovery.issuer == ISSUER) { "Unexpected issuer" }
        listOf(discovery.authorizationEndpoint, discovery.tokenEndpoint, discovery.jwksUri).forEach {
            require(it.startsWith("https://")) { "Insecure endpoint" }
        }
        discovery.revocationEndpoint?.let { require(it.startsWith("https://")) { "Insecure endpoint" } }
        return discovery
    }

    fun parseTokenResponse(body: String): TokenResponse = json.decodeFromString(TokenResponse.serializer(), body)

    /** Refresh errors that mean the grant is gone and the user has to sign in again. */
    fun isTerminalRefreshError(errorCode: String?): Boolean = errorCode in setOf(
        "invalid_grant",
        "invalid_refresh_token",
        "token_expired",
        "refresh_token_expired",
        "refresh_token_invalidated",
        "refresh_token_reused",
    )

    fun oauthErrorCode(body: String): String? = runCatching {
        val element = json.parseToJsonElement(body).jsonObject
        when (val error = element["error"]) {
            is JsonPrimitive -> error.contentOrNull
            is JsonObject -> (error["code"] as? JsonPrimitive)?.contentOrNull
            else -> null
        }
    }.getOrNull()

    /** Validates an ID token's signature against [jwks] and its claims. */
    fun validateIdToken(
        idToken: String,
        jwks: String,
        clientId: String,
        nonce: String,
        nowEpochSeconds: Long,
        leewaySeconds: Long = 300,
    ): IdTokenClaims {
        val parts = idToken.split('.')
        require(parts.size == 3) { "Malformed ID token" }
        val header = json.parseToJsonElement(String(base64UrlDecode(parts[0]), Charsets.UTF_8)).jsonObject
        require(header.string("alg") == "RS256") { "Unsupported ID token algorithm" }
        val kid = header.string("kid")
        val key = json.decodeFromString(JsonWebKeySet.serializer(), jwks).keys
            .filter { it.kty == "RSA" && (it.use == null || it.use == "sig") }
            .firstOrNull { kid == null || it.kid == kid }
            ?: error("No matching signing key")
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(
            RSAPublicKeySpec(BigInteger(1, base64UrlDecode(key.n)), BigInteger(1, base64UrlDecode(key.e))),
        )
        val verified = Signature.getInstance("SHA256withRSA").run {
            initVerify(publicKey)
            update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
            verify(base64UrlDecode(parts[2]))
        }
        require(verified) { "Invalid ID token signature" }
        return validateIdTokenClaims(
            payload = String(base64UrlDecode(parts[1]), Charsets.UTF_8),
            clientId = clientId,
            nonce = nonce,
            nowEpochSeconds = nowEpochSeconds,
            leewaySeconds = leewaySeconds,
        )
    }

    fun validateIdTokenClaims(
        payload: String,
        clientId: String,
        nonce: String,
        nowEpochSeconds: Long,
        leewaySeconds: Long = 300,
    ): IdTokenClaims {
        val claims = json.parseToJsonElement(payload).jsonObject
        require(claims.string("iss") == ISSUER) { "Unexpected ID token issuer" }
        val audience = when (val aud = claims["aud"]) {
            is JsonPrimitive -> listOfNotNull(aud.contentOrNull)
            is JsonArray -> aud.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            else -> emptyList()
        }
        require(clientId in audience) { "Unexpected ID token audience" }
        val expiresAt = (claims["exp"] as? JsonPrimitive)?.longOrNull ?: error("Missing exp")
        require(expiresAt + leewaySeconds >= nowEpochSeconds) { "Expired ID token" }
        require((claims["iat"] as? JsonPrimitive)?.longOrNull != null) { "Missing iat" }
        require(claims.string("nonce")?.let { MessageDigest.isEqual(it.toByteArray(), nonce.toByteArray()) } == true) {
            "ID token nonce mismatch"
        }
        val subject = claims.string("sub")?.takeIf { it.isNotBlank() } ?: error("Missing sub")
        return IdTokenClaims(subject = subject, email = claims.string("email"), name = claims.string("name"))
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

    private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    private fun base64UrlDecode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")

    data class Pkce(val verifier: String, val challenge: String)

    data class AuthorizationRequest(
        val redirectUri: String,
        val state: String,
        val nonce: String,
        val pkce: Pkce,
        val hostId: String,
        val issuedClientId: String?,
        val loginHint: String? = null,
    )

    sealed interface CallbackResult {
        data class Code(val code: String, val clientId: String) : CallbackResult
        data object Denied : CallbackResult
        data class Failed(val reason: String) : CallbackResult
    }

    @Serializable
    data class Discovery(
        val issuer: String,
        @SerialName("authorization_endpoint") val authorizationEndpoint: String,
        @SerialName("token_endpoint") val tokenEndpoint: String,
        @SerialName("jwks_uri") val jwksUri: String,
        @SerialName("revocation_endpoint") val revocationEndpoint: String? = null,
    )

    @Serializable
    data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("id_token") val idToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long = 3600,
        val scope: String? = null,
        @SerialName("earliest_refresh_at") val earliestRefreshAt: JsonElement? = null,
    ) {
        /** The field's format is not documented; accept epoch seconds, epoch millis or ISO-8601. */
        fun earliestRefreshAtEpochSeconds(): Long? {
            val raw = (earliestRefreshAt as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
            raw.toLongOrNull()?.let { return if (it > 100_000_000_000L) it / 1000 else it }
            raw.toDoubleOrNull()?.let { return it.toLong() }
            return runCatching { java.time.Instant.parse(raw).epochSecond }.getOrNull()
        }
    }

    data class IdTokenClaims(val subject: String, val email: String?, val name: String?)

    @Serializable
    private data class JsonWebKeySet(val keys: List<JsonWebKey> = emptyList())

    @Serializable
    private data class JsonWebKey(
        val kty: String,
        val kid: String? = null,
        val use: String? = null,
        val n: String = "",
        val e: String = "",
    )
}
