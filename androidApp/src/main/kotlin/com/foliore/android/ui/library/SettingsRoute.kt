package com.foliore.android.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foliore.android.R
import com.foliore.android.storage.SyncProvider
import com.foliore.android.ui.theme.ThemeMode
import com.foliore.android.viewmodel.LibraryViewModel

@Composable
fun SettingsRoute(
    viewModel: LibraryViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    bottomChromeInset: Dp = 0.dp,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSyncFolderGuidance by rememberSaveable { mutableStateOf(false) }
    var showDeleteGoogleDriveBackupConfirmation by rememberSaveable { mutableStateOf(false) }
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.configureSyncFolder(uri)
        }
    }
    var launchGoogleDriveResolution: (android.app.PendingIntent) -> Unit = {}
    val googleDriveAuthorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        viewModel.completeGoogleDriveAuthorization(result.data, launchGoogleDriveResolution)
    }
    launchGoogleDriveResolution = { pendingIntent ->
        googleDriveAuthorizationLauncher.launch(
            IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
        )
    }
    val message = notice?.type?.let { noticeMessage(it) }

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeNotice()
        }
    }

    LaunchedEffect(state.syncProvider, state.isGoogleDriveConnected) {
        if (
            state.syncProvider == SyncProvider.GOOGLE_DRIVE_APPDATA &&
            state.isGoogleDriveConnected
        ) {
            viewModel.refreshGoogleDriveBackupStats(launchGoogleDriveResolution)
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = bottomChromeInset),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { paddingValues ->
        SettingsScreen(
            themeMode = themeMode,
            hasOpenAiApiKey = state.hasOpenAiApiKey,
            hasGoogleBooksApiKey = state.hasGoogleBooksApiKey,
            kindleEmailSettings = state.kindleEmailSettings,
            syncProvider = state.syncProvider,
            syncFolderLabel = state.syncFolderLabel,
            googleDriveAccountLabel = state.googleDriveAccountLabel,
            isGoogleDriveConnected = state.isGoogleDriveConnected,
            googleDriveBackupStats = state.googleDriveBackupStats,
            isGoogleDriveBackupStatsLoading = state.isGoogleDriveBackupStatsLoading,
            isAutoSyncEnabled = state.isAutoSyncEnabled,
            isAutoSyncWifiOnly = state.isAutoSyncWifiOnly,
            isSyncing = state.isSyncing,
            lastSyncSummary = state.lastSyncSummary,
            onThemeModeChange = onThemeModeChange,
            onOpenAiApiKeySave = viewModel::saveOpenAiApiKey,
            onOpenAiApiKeyClear = viewModel::clearOpenAiApiKey,
            onGoogleBooksApiKeySave = viewModel::saveGoogleBooksApiKey,
            onGoogleBooksApiKeyClear = viewModel::clearGoogleBooksApiKey,
            onKindleEmailSave = viewModel::saveKindleEmail,
            onKindleEmailClear = viewModel::clearKindleEmail,
            onKindleSmtpSave = viewModel::saveKindleSmtpSettings,
            onKindleSmtpClear = viewModel::clearKindleSmtpSettings,
            onChooseSyncFolder = { showSyncFolderGuidance = true },
            onConnectGoogleDrive = {
                viewModel.connectGoogleDrive(launchGoogleDriveResolution)
            },
            onRefreshGoogleDriveBackupStats = {
                viewModel.refreshGoogleDriveBackupStats(launchGoogleDriveResolution)
            },
            onDeleteGoogleDriveBackup = { showDeleteGoogleDriveBackupConfirmation = true },
            onAutoSyncEnabledChange = viewModel::setAutoSyncEnabled,
            onAutoSyncWifiOnlyChange = viewModel::setAutoSyncWifiOnly,
            onSyncNow = viewModel::syncNow,
            onDisconnectSync = viewModel::disconnectSync,
            modifier = Modifier.padding(paddingValues),
        )
    }

    if (showSyncFolderGuidance) {
        AlertDialog(
            onDismissRequest = { showSyncFolderGuidance = false },
            title = {
                Text(text = stringResource(R.string.settings_sync_folder_guidance_title))
            },
            text = {
                Text(text = stringResource(R.string.settings_sync_folder_guidance_body))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSyncFolderGuidance = false
                        folderLauncher.launch(null)
                    },
                ) {
                    Text(text = stringResource(R.string.settings_sync_folder_guidance_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyncFolderGuidance = false }) {
                    Text(text = stringResource(R.string.settings_sync_folder_guidance_cancel))
                }
            },
        )
    }

    if (showDeleteGoogleDriveBackupConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteGoogleDriveBackupConfirmation = false },
            title = {
                Text(text = stringResource(R.string.settings_sync_google_drive_delete_backup_title))
            },
            text = {
                val stats = state.googleDriveBackupStats
                Text(
                    text = if (stats != null) {
                        stringResource(
                            R.string.settings_sync_google_drive_delete_backup_body_with_stats,
                            stats.syncedBooks,
                            stats.remoteFiles,
                            stats.rootItems,
                            stats.unknownRootItems,
                        )
                    } else {
                        stringResource(R.string.settings_sync_google_drive_delete_backup_body)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteGoogleDriveBackupConfirmation = false
                        viewModel.deleteGoogleDriveBackup(launchGoogleDriveResolution)
                    },
                ) {
                    Text(text = stringResource(R.string.settings_sync_google_drive_delete_backup_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteGoogleDriveBackupConfirmation = false }) {
                    Text(text = stringResource(R.string.settings_sync_folder_guidance_cancel))
                }
            },
        )
    }
}
