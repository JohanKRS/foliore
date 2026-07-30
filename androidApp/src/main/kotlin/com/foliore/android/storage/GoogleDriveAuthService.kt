package com.foliore.android.storage

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks

class GoogleDriveAuthService(
    context: Context,
    private val syncSettingsRepository: SyncSettingsRepository,
) {
    private val appContext = context.applicationContext
    private val authorizationClient = Identity.getAuthorizationClient(appContext)

    fun authorize(): GoogleDriveAuthorization {
        val pinnedResult = requestAuthorization(useStoredAccount = true)
        return if (
            pinnedResult is GoogleDriveAuthorization.Failed &&
            syncSettingsRepository.googleDriveAccountName() != null
        ) {
            requestAuthorization(useStoredAccount = false)
        } else {
            pinnedResult
        }
    }

    fun authorizeForConnection(): GoogleDriveAuthorization {
        return requestAuthorization(useStoredAccount = false)
    }

    private fun requestAuthorization(useStoredAccount: Boolean): GoogleDriveAuthorization {
        return runCatching {
            Tasks.await(
                authorizationClient.authorize(
                    authorizationRequest(useStoredAccount = useStoredAccount),
                ),
            ).toAuthorization()
        }.getOrElse { error ->
            error.toAuthorizationFailure("authorize")
        }
    }

    fun authorizationFromIntent(data: Intent?): GoogleDriveAuthorization {
        if (data == null) {
            return GoogleDriveAuthorization.Failed(
                GoogleDriveAuthorizationFailure.CANCELLED,
            )
        }
        return runCatching {
            authorizationClient.getAuthorizationResultFromIntent(data).toAuthorization()
        }.getOrElse { error ->
            error.toAuthorizationFailure("authorization result")
        }
    }

    private fun authorizationRequest(useStoredAccount: Boolean): AuthorizationRequest {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
        if (useStoredAccount) {
            syncSettingsRepository.googleDriveAccountName()?.let { accountName ->
                builder.setAccount(Account(accountName, GOOGLE_ACCOUNT_TYPE))
            }
        }
        return builder.build()
    }

    private fun AuthorizationResult.toAuthorization(): GoogleDriveAuthorization {
        if (hasResolution()) {
            val pendingIntent = pendingIntent ?: return GoogleDriveAuthorization.Failed(
                GoogleDriveAuthorizationFailure.UNAVAILABLE,
            )
            return GoogleDriveAuthorization.NeedsResolution(pendingIntent)
        }
        val token = accessToken ?: return GoogleDriveAuthorization.Failed(
            GoogleDriveAuthorizationFailure.UNAVAILABLE,
        )
        val googleAccount = toGoogleSignInAccount()
        val accountName = googleAccount?.account?.name ?: googleAccount?.email
        val accountLabel = googleAccount?.email ?: accountName
        syncSettingsRepository.rememberGoogleDriveAccount(
            accountLabel = accountLabel,
            accountName = accountName,
        )
        return GoogleDriveAuthorization.Authorized(
            accessToken = token,
            accountLabel = accountLabel,
            accountName = accountName,
        )
    }

    private fun Throwable.toAuthorizationFailure(operation: String): GoogleDriveAuthorization.Failed {
        val apiException = generateSequence(this) { error -> error.cause }
            .filterIsInstance<ApiException>()
            .firstOrNull()
        val failure = when (apiException?.statusCode) {
            CommonStatusCodes.DEVELOPER_ERROR -> {
                GoogleDriveAuthorizationFailure.CONFIGURATION_REQUIRED
            }
            CommonStatusCodes.CANCELED -> GoogleDriveAuthorizationFailure.CANCELLED
            else -> GoogleDriveAuthorizationFailure.UNAVAILABLE
        }
        Log.e(
            TAG,
            "Google Drive $operation failed status=${apiException?.statusCode} " +
                "type=${this::class.java.name} message=$message",
            this,
        )
        return GoogleDriveAuthorization.Failed(failure)
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val TAG = "GoogleDriveAuthService"
    }
}

enum class GoogleDriveAuthorizationFailure {
    CONFIGURATION_REQUIRED,
    CANCELLED,
    UNAVAILABLE,
}

sealed interface GoogleDriveAuthorization {
    data class Authorized(
        val accessToken: String,
        val accountLabel: String?,
        val accountName: String?,
    ) : GoogleDriveAuthorization
    data class NeedsResolution(val pendingIntent: PendingIntent) : GoogleDriveAuthorization
    data class Failed(val reason: GoogleDriveAuthorizationFailure) : GoogleDriveAuthorization
}
