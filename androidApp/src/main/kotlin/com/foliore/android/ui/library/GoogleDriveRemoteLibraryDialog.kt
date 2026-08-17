package com.foliore.android.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.foliore.android.R
import com.foliore.android.storage.GoogleDriveRemoteLibraryRecovery
import com.foliore.android.viewmodel.SyncProgress
import com.foliore.android.viewmodel.SyncProgressPhase

@Composable
internal fun GoogleDriveRemoteLibraryDialog(
    recovery: GoogleDriveRemoteLibraryRecovery,
    isWorking: Boolean,
    progress: SyncProgress?,
    onRecreateOnDrive: () -> Unit,
    onKeepOnlyOnDevice: () -> Unit,
    onDismiss: () -> Unit,
) {
    FolioreOptionsDialog(
        title = stringResource(R.string.sync_google_drive_remote_removed_title),
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (progress?.phase == SyncProgressPhase.DELETING_REMOTE) {
                SyncProgressStrip(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = stringResource(R.string.sync_google_drive_remote_removed_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (recovery.remainingFiles > 0) {
                Text(
                    text = stringResource(
                        R.string.sync_google_drive_remote_removed_leftovers,
                        recovery.remainingFiles,
                        recovery.remainingBytes.toReadableDriveSize(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onRecreateOnDrive,
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.sync_google_drive_remote_removed_recreate))
            }
            Text(
                text = stringResource(R.string.sync_google_drive_remote_removed_recreate_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onKeepOnlyOnDevice,
                enabled = !isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.sync_google_drive_remote_removed_keep_local))
            }
            Text(
                text = stringResource(R.string.sync_google_drive_remote_removed_keep_local_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
