package com.foliore.android.ai

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatGptOAuthTest {
    @Test
    fun codeChallengeMatchesRfc7636Example() {
        // RFC 7636, appendix B.
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            ChatGptOAuth.codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
        val pkce = ChatGptOAuth.newPkce()
        assertTrue(pkce.verifier.length >= 43)
        assertEquals(ChatGptOAuth.codeChallenge(pkce.verifier), pkce.challenge)
    }

    @Test
    fun redirectUsesTheLoopbackAddressNeverLocalhost() {
        assertEquals("http://127.0.0.1:1455/auth/callback", ChatGptOAuth.redirectUri(1455))
    }

    @Test
    fun firstSignInUsesTheDynamicClientAndNamesTheApp() {
        val url = ChatGptOAuth.authorizationUrl("https://auth.openai.com/oauth/authorize", request(issuedClientId = null))
        val query = ChatGptOAuth.parseQuery(url.substringAfter('?'))
        assertEquals("dynamic_agent_client", query["client_id"])
        assertEquals("Foliore", query["agent_name_hint"])
        assertEquals("code", query["response_type"])
        assertEquals("S256", query["code_challenge_method"])
        assertEquals("challenge", query["code_challenge"])
        assertEquals("http://127.0.0.1:1455/auth/callback", query["redirect_uri"])
        assertEquals(ChatGptOAuth.SCOPES, query["scope"])
        assertEquals("https://api.openai.com/v1", query["resource"])
        assertEquals("host", query["ext_agent_host_id"])
        assertFalse(url.contains('+'), "Spaces must be encoded as %20")
    }

    @Test
    fun laterSignInsReuseTheIssuedClientWithoutTheNameHint() {
        val url = ChatGptOAuth.authorizationUrl("https://auth.openai.com/oauth/authorize", request(issuedClientId = "oaiapp_123"))
        val query = ChatGptOAuth.parseQuery(url.substringAfter('?'))
        assertEquals("oaiapp_123", query["client_id"])
        assertNull(query["agent_name_hint"])
    }

    @Test
    fun callbackWithAnotherStateIsIgnored() {
        assertNull(ChatGptOAuth.parseCallback(mapOf("state" to "other", "code" to "c"), "state", null))
        assertNull(ChatGptOAuth.parseCallback(mapOf("code" to "c"), "state", null))
    }

    @Test
    fun firstCallbackCarriesTheIssuedClientId() {
        assertEquals(
            ChatGptOAuth.CallbackResult.Code("c", "oaiapp_123"),
            ChatGptOAuth.parseCallback(mapOf("state" to "state", "code" to "c", "client_id" to "oaiapp_123"), "state", null),
        )
        assertEquals(
            ChatGptOAuth.CallbackResult.Failed("registration_incomplete"),
            ChatGptOAuth.parseCallback(mapOf("state" to "state", "code" to "c"), "state", null),
        )
    }

    @Test
    fun laterCallbacksKeepTheSavedClientAndRejectAMismatch() {
        assertEquals(
            ChatGptOAuth.CallbackResult.Code("c", "oaiapp_123"),
            ChatGptOAuth.parseCallback(mapOf("state" to "state", "code" to "c"), "state", "oaiapp_123"),
        )
        assertEquals(
            ChatGptOAuth.CallbackResult.Failed("client_mismatch"),
            ChatGptOAuth.parseCallback(mapOf("state" to "state", "code" to "c", "client_id" to "oaiapp_999"), "state", "oaiapp_123"),
        )
    }

    @Test
    fun deniedAndFailedCallbacksAreReported() {
        assertEquals(
            ChatGptOAuth.CallbackResult.Denied,
            ChatGptOAuth.parseCallback(mapOf("state" to "state", "error" to "access_denied"), "state", null),
        )
        assertEquals(
            ChatGptOAuth.CallbackResult.Failed("server_error"),
            ChatGptOAuth.parseCallback(mapOf("state" to "state", "error" to "server_error"), "state", null),
        )
    }

    @Test
    fun discoveryMustComeFromTheOpenAiIssuerOverHttps() {
        val good = """{"issuer":"https://auth.openai.com","authorization_endpoint":"https://auth.openai.com/oauth/authorize",
            "token_endpoint":"https://auth.openai.com/oauth/token","jwks_uri":"https://auth.openai.com/.well-known/jwks.json"}"""
        assertEquals("https://auth.openai.com/oauth/token", ChatGptOAuth.parseDiscovery(good).tokenEndpoint)
        assertFailsWith<IllegalArgumentException> { ChatGptOAuth.parseDiscovery(good.replace("\"issuer\":\"https://auth.openai.com\"", "\"issuer\":\"https://evil.example\"")) }
        assertFailsWith<IllegalArgumentException> { ChatGptOAuth.parseDiscovery(good.replace("https://auth.openai.com/oauth/token", "http://auth.openai.com/oauth/token")) }
    }

    @Test
    fun earliestRefreshAtAcceptsSecondsMillisAndIsoDates() {
        fun parse(value: String) = ChatGptOAuth.parseTokenResponse("""{"access_token":"a","earliest_refresh_at":$value}""")
            .earliestRefreshAtEpochSeconds()
        assertEquals(1_800_000_000L, parse("1800000000"))
        assertEquals(1_800_000_000L, parse("1800000000000"))
        assertEquals(1_800_000_000L, parse("\"2027-01-15T08:00:00Z\""))
        assertNull(parse("null"))
        assertNull(ChatGptOAuth.parseTokenResponse("""{"access_token":"a"}""").earliestRefreshAtEpochSeconds())
    }

    @Test
    fun oauthErrorsAreReadFromBothShapes() {
        assertEquals("invalid_grant", ChatGptOAuth.oauthErrorCode("""{"error":"invalid_grant"}"""))
        assertEquals("refresh_token_reused", ChatGptOAuth.oauthErrorCode("""{"error":{"code":"refresh_token_reused"}}"""))
        assertNull(ChatGptOAuth.oauthErrorCode("not json"))
        assertTrue(ChatGptOAuth.isTerminalRefreshError("invalid_grant"))
        assertFalse(ChatGptOAuth.isTerminalRefreshError("temporarily_unavailable"))
    }

    @Test
    fun idTokenClaimsAreChecked() {
        val now = 1_800_000_000L
        val claims = ChatGptOAuth.validateIdTokenClaims(payload(aud = "[\"oaiapp_123\",\"other\"]", now = now), "oaiapp_123", "nonce", now)
        assertEquals("user-1", claims.subject)
        assertEquals("reader@example.com", claims.email)
        assertFailsWith<IllegalArgumentException> { ChatGptOAuth.validateIdTokenClaims(payload(aud = "\"other\"", now = now), "oaiapp_123", "nonce", now) }
        assertFailsWith<IllegalArgumentException> { ChatGptOAuth.validateIdTokenClaims(payload(now = now), "oaiapp_123", "wrong", now) }
        assertFailsWith<IllegalArgumentException> { ChatGptOAuth.validateIdTokenClaims(payload(now = now), "oaiapp_123", "nonce", now + 3_600 + 301) }
        assertFailsWith<IllegalArgumentException> {
            ChatGptOAuth.validateIdTokenClaims(payload(now = now).replace("https://auth.openai.com", "https://evil.example"), "oaiapp_123", "nonce", now)
        }
    }

    @Test
    fun idTokenSignatureIsVerifiedAgainstTheJwks() {
        val now = 1_800_000_000L
        val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val header = b64("""{"alg":"RS256","kid":"k1","typ":"JWT"}""".toByteArray())
        val body = b64(payload(now = now).toByteArray())
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keys.private)
            update("$header.$body".toByteArray())
            sign()
        }
        val token = "$header.$body.${b64(signature)}"
        val publicKey = keys.public as RSAPublicKey
        val jwks = """{"keys":[{"kty":"RSA","kid":"k1","use":"sig","n":"${b64(publicKey.modulus.toByteArray().dropWhile { it == 0.toByte() }.toByteArray())}","e":"${b64(publicKey.publicExponent.toByteArray())}"}]}"""

        assertEquals("user-1", ChatGptOAuth.validateIdToken(token, jwks, "oaiapp_123", "nonce", now).subject)
        val tampered = "$header.${b64(payload(now = now).replace("user-1", "user-2").toByteArray())}.${b64(signature)}"
        assertFailsWith<IllegalArgumentException> { ChatGptOAuth.validateIdToken(tampered, jwks, "oaiapp_123", "nonce", now) }
        assertFailsWith<IllegalStateException> { ChatGptOAuth.validateIdToken(token, jwks.replace("\"k1\"", "\"k2\""), "oaiapp_123", "nonce", now) }
    }

    @Test
    fun formsCarryTheResourceAndEncodeSafely() {
        val form = ChatGptOAuth.authorizationCodeForm("c/d", "oaiapp_123", "verifier", "http://127.0.0.1:1455/auth/callback")
        val body = ChatGptOAuth.formBody(form)
        assertEquals(form, ChatGptOAuth.parseQuery(body))
        assertTrue(body.contains("resource=https%3A%2F%2Fapi.openai.com%2Fv1"))
        assertEquals("refresh_token", ChatGptOAuth.refreshForm("oaiapp_123", "r")["grant_type"])
    }

    private fun request(issuedClientId: String?) = ChatGptOAuth.AuthorizationRequest(
        redirectUri = ChatGptOAuth.redirectUri(1455),
        state = "state",
        nonce = "nonce",
        pkce = ChatGptOAuth.Pkce("verifier", "challenge"),
        hostId = "host",
        issuedClientId = issuedClientId,
    )

    private fun payload(aud: String = "\"oaiapp_123\"", now: Long) =
        """{"iss":"https://auth.openai.com","aud":$aud,"sub":"user-1","email":"reader@example.com","name":"Reader",
            "nonce":"nonce","iat":$now,"exp":${now + 3_600}}"""

    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
