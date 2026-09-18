package com.foliore.android.storage

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

/** Tokens live only for the current operation; Google Play services owns their cache. */
class GmailAuthService(context: Context) {
    private val client = Identity.getAuthorizationClient(context.applicationContext)

    fun authorize(account: String): GmailAuthorization = runCatching {
        Tasks.await(client.authorize(AuthorizationRequest.builder()
            .setAccount(Account(account, "com.google"))
            .setRequestedScopes(listOf(Scope(SEND_SCOPE)))
            .build()), 60, TimeUnit.SECONDS).asResult()
    }.getOrElse(::failure)

    fun complete(data: Intent?): GmailAuthorization {
        if (data == null) return GmailAuthorization.Failed(GmailAuthFailure.CANCELLED)
        return runCatching { client.getAuthorizationResultFromIntent(data).asResult() }.getOrElse(::failure)
    }

    fun clearToken(token: String) {
        runCatching { Tasks.await(client.clearToken(ClearTokenRequest.builder().setToken(token).build()), 15, TimeUnit.SECONDS) }
    }

    private fun AuthorizationResult.asResult(): GmailAuthorization {
        if (hasResolution()) return pendingIntent?.let(GmailAuthorization::NeedsResolution)
            ?: GmailAuthorization.Failed(GmailAuthFailure.UNAVAILABLE)
        if (SEND_SCOPE !in grantedScopes) return GmailAuthorization.Failed(GmailAuthFailure.PERMISSION_REQUIRED)
        return accessToken?.takeIf(String::isNotBlank)?.let(GmailAuthorization::Authorized)
            ?: GmailAuthorization.Failed(GmailAuthFailure.UNAVAILABLE)
    }

    private fun failure(error: Throwable): GmailAuthorization.Failed {
        val status = generateSequence(error) { it.cause }.filterIsInstance<ApiException>().firstOrNull()?.statusCode
        return GmailAuthorization.Failed(when (status) {
            CommonStatusCodes.CANCELED -> GmailAuthFailure.CANCELLED
            CommonStatusCodes.DEVELOPER_ERROR -> GmailAuthFailure.CONFIGURATION_REQUIRED
            else -> GmailAuthFailure.UNAVAILABLE
        })
    }

    companion object { const val SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send" }
}

enum class GmailAuthFailure { CANCELLED, PERMISSION_REQUIRED, CONFIGURATION_REQUIRED, UNAVAILABLE }
sealed interface GmailAuthorization {
    // Avoid including bearer tokens in a generated toString().
    class Authorized(val token: String) : GmailAuthorization
    data class NeedsResolution(val pendingIntent: PendingIntent) : GmailAuthorization
    data class Failed(val reason: GmailAuthFailure) : GmailAuthorization
}
