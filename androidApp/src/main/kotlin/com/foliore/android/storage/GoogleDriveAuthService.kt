package com.foliore.android.storage

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks

class GoogleDriveAuthService(
    context: Context,
    private val syncSettingsRepository: SyncSettingsRepository,
) {
    private val appContext = context.applicationContext
    private val authorizationClient = Identity.getAuthorizationClient(appContext)

    fun authorize(): GoogleDriveAuthorization {
        return runCatching {
            Tasks.await(
                authorizationClient.authorize(
                    authorizationRequest(useStoredAccount = true),
                ),
            ).toAuthorization()
        }.getOrElse {
            GoogleDriveAuthorization.Failed
        }
    }

    fun authorizeForConnection(): GoogleDriveAuthorization {
        return runCatching {
            Tasks.await(
                authorizationClient.authorize(
                    authorizationRequest(useStoredAccount = false),
                ),
            ).toAuthorization()
        }.getOrElse {
            GoogleDriveAuthorization.Failed
        }
    }

    fun authorizationFromIntent(data: Intent?): GoogleDriveAuthorization {
        if (data == null) return GoogleDriveAuthorization.Failed
        return runCatching {
            authorizationClient.getAuthorizationResultFromIntent(data).toAuthorization()
        }.getOrElse {
            GoogleDriveAuthorization.Failed
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
            val pendingIntent = pendingIntent ?: return GoogleDriveAuthorization.Failed
            return GoogleDriveAuthorization.NeedsResolution(pendingIntent)
        }
        val token = accessToken ?: return GoogleDriveAuthorization.Failed
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

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}

sealed interface GoogleDriveAuthorization {
    data class Authorized(
        val accessToken: String,
        val accountLabel: String?,
        val accountName: String?,
    ) : GoogleDriveAuthorization
    data class NeedsResolution(val pendingIntent: PendingIntent) : GoogleDriveAuthorization
    data object Failed : GoogleDriveAuthorization
}
