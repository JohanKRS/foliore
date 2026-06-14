package com.foliore.android.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foliore.android.ui.theme.ThemeMode
import com.foliore.android.viewmodel.LibraryViewModel

@Composable
fun SettingsRoute(
    viewModel: LibraryViewModel,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            viewModel.configureSyncFolder(uri)
        }
    }
    val message = notice?.type?.let { noticeMessage(it) }

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeNotice()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        SettingsScreen(
            themeMode = themeMode,
            aiProviderMode = state.aiProviderMode,
            hasOpenAiApiKey = state.hasOpenAiApiKey,
            codexBridgeBaseUrl = state.codexBridgeBaseUrl,
            hasCodexBridgeToken = state.hasCodexBridgeToken,
            syncFolderLabel = state.syncFolderLabel,
            isSyncing = state.isSyncing,
            lastSyncSummary = state.lastSyncSummary,
            onThemeModeChange = onThemeModeChange,
            onAiProviderModeChange = viewModel::setAiProviderMode,
            onOpenAiApiKeySave = viewModel::saveOpenAiApiKey,
            onOpenAiApiKeyClear = viewModel::clearOpenAiApiKey,
            onCodexBridgeSettingsSave = viewModel::saveCodexBridgeSettings,
            onCodexBridgeSettingsClear = viewModel::clearCodexBridgeSettings,
            onCodexBridgeConnectionTest = viewModel::testCodexBridgeConnection,
            onChooseSyncFolder = { folderLauncher.launch(null) },
            onSyncNow = viewModel::syncNow,
            onDisconnectSync = viewModel::disconnectSync,
            modifier = Modifier.padding(paddingValues),
        )
    }
}
