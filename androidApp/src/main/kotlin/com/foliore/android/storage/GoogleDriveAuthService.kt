package com.foliore.android.storage

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks

class GoogleDriveAuthService(context: Context) {
    private val appContext = context.applicationContext
    private val authorizationClient = Identity.getAuthorizationClient(appContext)

    fun authorize(): GoogleDriveAuthorization {
        return runCatching {
            Tasks.await(authorizationClient.authorize(authorizationRequest())).toAuthorization()
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

    private fun authorizationRequest(): AuthorizationRequest {
        return AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
    }

    private fun AuthorizationResult.toAuthorization(): GoogleDriveAuthorization {
        if (hasResolution()) {
            val pendingIntent = pendingIntent ?: return GoogleDriveAuthorization.Failed
            return GoogleDriveAuthorization.NeedsResolution(pendingIntent)
        }
        val token = accessToken ?: return GoogleDriveAuthorization.Failed
        return GoogleDriveAuthorization.Authorized(accessToken = token)
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}

sealed interface GoogleDriveAuthorization {
    data class Authorized(val accessToken: String) : GoogleDriveAuthorization
    data class NeedsResolution(val pendingIntent: PendingIntent) : GoogleDriveAuthorization
    data object Failed : GoogleDriveAuthorization
}
