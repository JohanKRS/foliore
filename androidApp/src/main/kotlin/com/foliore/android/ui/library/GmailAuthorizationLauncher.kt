package com.foliore.android.ui.library

import android.app.PendingIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.foliore.android.viewmodel.LibraryViewModel

@Composable
internal fun rememberGmailAuthorizationLauncher(viewModel: LibraryViewModel): (PendingIntent) -> Unit {
    var resolve: (PendingIntent) -> Unit = {}
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.completeGmailAuthorization(result.data, resolve)
    }
    resolve = { pendingIntent ->
        runCatching { launcher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build()) }
            .onFailure { viewModel.gmailAuthorizationUnavailable() }
    }
    return resolve
}
