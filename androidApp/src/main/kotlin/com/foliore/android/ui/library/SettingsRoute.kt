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
import com.foliore.android.storage.AppLanguage
import com.foliore.android.storage.SyncProvider
import com.foliore.android.storage.LauncherIcon
import com.foliore.android.ui.theme.ThemeMode
import com.foliore.android.viewmodel.LibraryNoticeType
import com.foliore.android.viewmodel.LibraryViewModel

@Composable
fun SettingsRoute(
    viewModel: LibraryViewModel,
    launcherIcon: LauncherIcon,
    onLauncherIconChange: (LauncherIcon) -> Boolean,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    appLanguage: AppLanguage,
    onAppLanguageChange: (AppLanguage) -> Unit,
    shortcut: SettingsShortcut? = null,
    onShortcutConsumed: () -> Unit = {},
    topChromeInset: Dp = 0.dp,
    bottomChromeInset: Dp = 0.dp,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSyncFolderGuidance by rememberSaveable { mutableStateOf(false) }
    var showGoogleDriveLibraryChoice by rememberSaveable { mutableStateOf(false) }
    var showDeleteGoogleDriveBackupConfirmation by rememberSaveable { mutableStateOf(false) }
    var showLanguagePicker by rememberSaveable { mutableStateOf(false) }
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
    val gmailAuthorization = rememberGmailAuthorizationLauncher(viewModel)
    val gmailAccountPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val account = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
            val type = result.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_TYPE)
            if (account != null && type == "com.google") viewModel.connectGmail(account, gmailAuthorization)
            else viewModel.gmailAuthorizationUnavailable()
        }
    }
    val message = notice?.type?.let { noticeMessage(it) }

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeNotice()
        }
    }

    LaunchedEffect(notice?.type) {
        if (notice?.type == LibraryNoticeType.SYNC_GOOGLE_DRIVE_CONNECTED) {
            showGoogleDriveLibraryChoice = true
        }
    }

    LaunchedEffect(
        showGoogleDriveLibraryChoice,
        state.googleDriveBackupStats,
        state.isGoogleDriveBackupStatsLoading,
    ) {
        if (
            showGoogleDriveLibraryChoice &&
            !state.isGoogleDriveBackupStatsLoading &&
            (state.googleDriveBackupStats?.syncedBooks ?: 0) == 0
        ) {
            showGoogleDriveLibraryChoice = false
        }
    }

    LaunchedEffect(state.syncProvider, state.isGoogleDriveConnected) {
        if (
            state.syncProvider == SyncProvider.GOOGLE_DRIVE_APPDATA &&
            state.isGoogleDriveConnected
        ) {
            viewModel.refreshGoogleDriveBackupStatsSilently()
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
            bookCount = state.books.size,
            topChromeInset = topChromeInset,
            bottomChromeInset = bottomChromeInset,
            launcherIcon = launcherIcon,
            onLauncherIconChange = onLauncherIconChange,
            themeMode = themeMode,
            appLanguage = appLanguage,
            aiSettings = AiSettingsState(
                hasOpenAiApiKey = state.hasOpenAiApiKey,
                chatGptAccount = state.chatGptAccount,
                hasRegistration = state.hasChatGptRegistration,
                isChatGptSigningIn = state.isChatGptSigningIn,
                textLanguage = state.aiTextLanguage,
            ),
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
            onChooseAppLanguage = { showLanguagePicker = true },
            onOpenAiApiKeySave = viewModel::saveOpenAiApiKey,
            onOpenAiApiKeyClear = viewModel::clearOpenAiApiKey,
            onChatGptSignIn = { mode ->
                viewModel.startChatGptSignIn(mode) { uri ->
                    runCatching {
                        context.startActivity(com.foliore.android.ai.ChatGptAuthService.browserIntent(uri))
                    }.isSuccess
                }
            },
            onChatGptCancelSignIn = viewModel::cancelChatGptSignIn,
            onChatGptSignOut = viewModel::signOutChatGpt,
            onChatGptModelSelect = viewModel::selectChatGptModel,
            onAiTextLanguageChange = viewModel::setAiTextLanguage,
            onKindleEmailSave = viewModel::saveKindleEmail,
            onKindleEmailClear = viewModel::clearKindleEmail,
            onKindleSmtpSave = viewModel::saveKindleSmtpSettings,
            onKindleSmtpClear = viewModel::clearKindleSmtpSettings,
            isGmailConnecting = state.isGmailConnecting || state.hasActiveProgress,
            onConnectGmail = {
                runCatching { gmailAccountPicker.launch(com.google.android.gms.common.AccountPicker.newChooseAccountIntent(
                    com.google.android.gms.common.AccountPicker.AccountChooserOptions.Builder()
                        .setAllowableAccountsTypes(listOf("com.google")).setAlwaysShowAccountPicker(true).build()))
                }.onFailure { viewModel.gmailAuthorizationUnavailable() }
            },
            onUseGmail = { state.kindleEmailSettings.gmailAccount?.let { viewModel.connectGmail(it, gmailAuthorization) } },
            onDisconnectGmail = viewModel::disconnectGmail,
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
            shortcut = shortcut,
            onShortcutConsumed = onShortcutConsumed,
            modifier = Modifier.padding(paddingValues),
        )
    }

    if (showLanguagePicker) {
        FolioreOptionsDialog(
            title = stringResource(R.string.settings_language_dialog_title),
            onDismiss = { showLanguagePicker = false },
        ) {
            FolioreDialogSectionCard(
                label = stringResource(R.string.settings_language),
            ) {
                FolioreDialogOptionGrid(
                    columns = if (maxWidth >= 420.dp) 2 else 1,
                    options = AppLanguage.entries.map { language ->
                        FolioreDialogOption(
                            label = stringResource(language.labelResource()),
                            selected = appLanguage == language,
                            onClick = {
                                showLanguagePicker = false
                                onAppLanguageChange(language)
                            },
                        )
                    },
                )
            }
        }
    }

    if (state.showChatGptWelcome) {
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        AlertDialog(
            onDismissRequest = viewModel::dismissChatGptWelcome,
            title = {
                Text(
                    text = state.chatGptAccount?.label?.let {
                        stringResource(R.string.chatgpt_welcome_title_with_account, it)
                    } ?: stringResource(R.string.chatgpt_welcome_title),
                )
            },
            text = {
                Text(text = stringResource(R.string.chatgpt_welcome_body))
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissChatGptWelcome) {
                    Text(text = stringResource(R.string.chatgpt_welcome_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        runCatching { uriHandler.openUri(com.foliore.android.ai.ChatGptOAuth.USAGE_SETTINGS_URL) }
                    },
                ) {
                    Text(text = stringResource(R.string.chatgpt_manage_usage))
                }
            },
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

    if (
        showGoogleDriveLibraryChoice &&
        !state.isGoogleDriveBackupStatsLoading &&
        (state.googleDriveBackupStats?.syncedBooks ?: 0) > 0
    ) {
        AlertDialog(
            onDismissRequest = { showGoogleDriveLibraryChoice = false },
            title = {
                Text(text = stringResource(R.string.settings_sync_google_drive_existing_title))
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.settings_sync_google_drive_existing_body,
                        state.googleDriveBackupStats?.syncedBooks ?: 0,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showGoogleDriveLibraryChoice = false
                        viewModel.syncNow()
                    },
                ) {
                    Text(text = stringResource(R.string.settings_sync_google_drive_existing_use))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showGoogleDriveLibraryChoice = false
                        showDeleteGoogleDriveBackupConfirmation = true
                    },
                ) {
                    Text(text = stringResource(R.string.settings_sync_google_drive_existing_replace))
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

private fun AppLanguage.labelResource(): Int {
    return when (this) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.PORTUGUESE_BRAZIL -> R.string.settings_language_portuguese_brazil
        AppLanguage.ENGLISH -> R.string.settings_language_english
        AppLanguage.SPANISH -> R.string.settings_language_spanish
        AppLanguage.JAPANESE -> R.string.settings_language_japanese
        AppLanguage.FRENCH -> R.string.settings_language_french
        AppLanguage.ITALIAN -> R.string.settings_language_italian
        AppLanguage.GERMAN -> R.string.settings_language_german
        AppLanguage.CHINESE_SIMPLIFIED -> R.string.settings_language_chinese_simplified
    }
}
