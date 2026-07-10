package com.foliore.android.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.foliore.android.R
import com.foliore.android.storage.GoogleDriveBackupStats
import com.foliore.android.storage.KindleEmailSettings
import com.foliore.android.storage.SyncProvider
import com.foliore.android.ui.FolioreTopLevelHeader
import com.foliore.android.ui.theme.ThemeMode
import com.foliore.android.viewmodel.SyncSummary
import java.util.Locale

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    hasOpenAiApiKey: Boolean,
    hasGoogleBooksApiKey: Boolean,
    kindleEmailSettings: KindleEmailSettings,
    syncProvider: SyncProvider,
    syncFolderLabel: String?,
    googleDriveAccountLabel: String?,
    isGoogleDriveConnected: Boolean,
    googleDriveBackupStats: GoogleDriveBackupStats?,
    isGoogleDriveBackupStatsLoading: Boolean,
    isAutoSyncEnabled: Boolean,
    isAutoSyncWifiOnly: Boolean,
    isSyncing: Boolean,
    lastSyncSummary: SyncSummary?,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenAiApiKeySave: (String) -> Unit,
    onOpenAiApiKeyClear: () -> Unit,
    onGoogleBooksApiKeySave: (String) -> Unit,
    onGoogleBooksApiKeyClear: () -> Unit,
    onKindleEmailSave: (String) -> Unit,
    onKindleEmailClear: () -> Unit,
    onKindleSmtpSave: (String, String, String, String, String) -> Unit,
    onKindleSmtpClear: () -> Unit,
    onChooseSyncFolder: () -> Unit,
    onConnectGoogleDrive: () -> Unit,
    onRefreshGoogleDriveBackupStats: () -> Unit,
    onDeleteGoogleDriveBackup: () -> Unit,
    onAutoSyncEnabledChange: (Boolean) -> Unit,
    onAutoSyncWifiOnlyChange: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onDisconnectSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var openAiApiKeyInput by rememberSaveable { mutableStateOf("") }
    var googleBooksApiKeyInput by rememberSaveable { mutableStateOf("") }
    var kindleEmailInput by rememberSaveable { mutableStateOf("") }
    var kindleSenderInput by rememberSaveable(kindleEmailSettings.senderEmail) {
        mutableStateOf(kindleEmailSettings.senderEmail.orEmpty())
    }
    var kindleSmtpHostInput by rememberSaveable(kindleEmailSettings.smtpHost) {
        mutableStateOf(kindleEmailSettings.smtpHost)
    }
    var kindleSmtpPortInput by rememberSaveable(kindleEmailSettings.smtpPort) {
        mutableStateOf(kindleEmailSettings.smtpPort.toString())
    }
    var kindleSmtpUserInput by rememberSaveable(kindleEmailSettings.smtpUsername) {
        mutableStateOf(kindleEmailSettings.smtpUsername.orEmpty())
    }
    var kindleSmtpPasswordInput by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = 920.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
        Box(
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.systemBars.only(WindowInsetsSides.Top),
            ),
        ) {
            FolioreTopLevelHeader(title = stringResource(R.string.settings_title))
        }
        EditorialDivider(
            modifier = Modifier.fillMaxWidth(),
            horizontalPadding = 0.dp,
        )
        Spacer(modifier = Modifier.height(28.dp))

        Column(modifier = Modifier.padding(horizontal = 28.dp)) {
        SectionTitle(text = stringResource(R.string.settings_appearance))
        Spacer(modifier = Modifier.height(8.dp))
        SelectableOptionRow(
            selected = themeMode == ThemeMode.SYSTEM,
            icon = Icons.Outlined.Palette,
            title = stringResource(R.string.settings_theme_system),
            description = stringResource(R.string.settings_theme_system_description),
            onClick = { onThemeModeChange(ThemeMode.SYSTEM) },
        )
        SelectableOptionRow(
            selected = themeMode == ThemeMode.LIGHT,
            icon = Icons.Outlined.LightMode,
            title = stringResource(R.string.settings_theme_light),
            description = stringResource(R.string.settings_theme_light_description),
            onClick = { onThemeModeChange(ThemeMode.LIGHT) },
        )
        SelectableOptionRow(
            selected = themeMode == ThemeMode.DARK,
            icon = Icons.Outlined.DarkMode,
            title = stringResource(R.string.settings_theme_dark),
            description = stringResource(R.string.settings_theme_dark_description),
            onClick = { onThemeModeChange(ThemeMode.DARK) },
        )

        Spacer(modifier = Modifier.height(28.dp))
        EditorialDivider(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(28.dp))

        SectionTitle(text = stringResource(R.string.settings_library))
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.TaskAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_preserve_original),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.settings_preserve_original_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        EditorialDivider(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(28.dp))

        SectionTitle(text = stringResource(R.string.settings_kindle))
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Email,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_kindle_email),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.settings_kindle_email_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = kindleEmailSettings.kindleEmail?.let {
                    stringResource(R.string.settings_kindle_email_configured, it)
                } ?: stringResource(R.string.settings_kindle_email_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = kindleEmailInput,
                onValueChange = { kindleEmailInput = it },
                label = { Text(text = stringResource(R.string.settings_kindle_email_label)) },
                placeholder = { Text(text = stringResource(R.string.settings_kindle_email_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        onKindleEmailSave(kindleEmailInput)
                        kindleEmailInput = ""
                    },
                    enabled = kindleEmailInput.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.settings_kindle_email_save))
                }
                OutlinedButton(
                    onClick = {
                        onKindleEmailClear()
                        kindleEmailInput = ""
                    },
                    enabled = kindleEmailSettings.kindleEmail != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.settings_kindle_email_clear))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (kindleEmailSettings.isReady) {
                    stringResource(R.string.settings_kindle_smtp_configured)
                } else if (kindleEmailSettings.hasSmtpPassword) {
                    stringResource(R.string.settings_kindle_smtp_incomplete)
                } else {
                    stringResource(R.string.settings_kindle_smtp_missing)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = kindleSenderInput,
                onValueChange = { kindleSenderInput = it },
                label = { Text(text = stringResource(R.string.settings_kindle_sender_label)) },
                placeholder = { Text(text = stringResource(R.string.settings_kindle_sender_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = kindleSmtpHostInput,
                    onValueChange = { kindleSmtpHostInput = it },
                    label = { Text(text = stringResource(R.string.settings_kindle_smtp_host_label)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
                )
                OutlinedTextField(
                    value = kindleSmtpPortInput,
                    onValueChange = { kindleSmtpPortInput = it },
                    label = { Text(text = stringResource(R.string.settings_kindle_smtp_port_label)) },
                    modifier = Modifier.weight(0.46f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
                )
            }
            OutlinedTextField(
                value = kindleSmtpUserInput,
                onValueChange = { kindleSmtpUserInput = it },
                label = { Text(text = stringResource(R.string.settings_kindle_smtp_user_label)) },
                placeholder = { Text(text = stringResource(R.string.settings_kindle_smtp_user_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
            )
            OutlinedTextField(
                value = kindleSmtpPasswordInput,
                onValueChange = { kindleSmtpPasswordInput = it },
                label = {
                    Text(
                        text = if (kindleEmailSettings.hasSmtpPassword) {
                            stringResource(R.string.settings_kindle_smtp_password_keep_label)
                        } else {
                            stringResource(R.string.settings_kindle_smtp_password_label)
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        onKindleSmtpSave(
                            kindleSenderInput,
                            kindleSmtpHostInput,
                            kindleSmtpPortInput,
                            kindleSmtpUserInput,
                            kindleSmtpPasswordInput,
                        )
                        kindleSmtpPasswordInput = ""
                    },
                    enabled = kindleSenderInput.isNotBlank() &&
                        kindleSmtpHostInput.isNotBlank() &&
                        kindleSmtpPortInput.isNotBlank() &&
                        kindleSmtpUserInput.isNotBlank() &&
                        (kindleSmtpPasswordInput.isNotBlank() || kindleEmailSettings.hasSmtpPassword),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.settings_kindle_smtp_save))
                }
                OutlinedButton(
                    onClick = {
                        onKindleSmtpClear()
                        kindleSenderInput = ""
                        kindleSmtpHostInput = kindleEmailSettings.smtpHost
                        kindleSmtpPortInput = kindleEmailSettings.smtpPort.toString()
                        kindleSmtpUserInput = ""
                        kindleSmtpPasswordInput = ""
                    },
                    enabled = kindleEmailSettings.senderEmail != null ||
                        kindleEmailSettings.smtpUsername != null ||
                        kindleEmailSettings.hasSmtpPassword,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.settings_kindle_smtp_clear))
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        EditorialDivider(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(28.dp))

        SectionTitle(text = stringResource(R.string.settings_metadata_sources))
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Public,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_metadata_google_books),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.settings_metadata_google_books_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (hasGoogleBooksApiKey) {
                    stringResource(R.string.settings_metadata_google_books_key_configured)
                } else {
                    stringResource(R.string.settings_metadata_google_books_key_missing)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = googleBooksApiKeyInput,
                onValueChange = { googleBooksApiKeyInput = it },
                label = { Text(text = stringResource(R.string.settings_metadata_google_books_key_label)) },
                placeholder = { Text(text = stringResource(R.string.settings_metadata_google_books_key_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        onGoogleBooksApiKeySave(googleBooksApiKeyInput)
                        googleBooksApiKeyInput = ""
                    },
                    enabled = googleBooksApiKeyInput.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.settings_metadata_google_books_key_save))
                }
                OutlinedButton(
                    onClick = {
                        onGoogleBooksApiKeyClear()
                        googleBooksApiKeyInput = ""
                    },
                    enabled = hasGoogleBooksApiKey,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.settings_metadata_google_books_key_clear))
                }
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        EditorialDivider(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(28.dp))

        SectionTitle(text = stringResource(R.string.settings_ai))
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_ai_openai_api),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.settings_ai_openai_api_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (hasOpenAiApiKey) {
                    stringResource(R.string.settings_ai_api_key_configured)
                } else {
                    stringResource(R.string.settings_ai_api_key_missing)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = openAiApiKeyInput,
                onValueChange = { openAiApiKeyInput = it },
                label = { Text(text = stringResource(R.string.settings_ai_api_key_label)) },
                placeholder = { Text(text = stringResource(R.string.settings_ai_api_key_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = {
                        onOpenAiApiKeySave(openAiApiKeyInput)
                        openAiApiKeyInput = ""
                    },
                    enabled = openAiApiKeyInput.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.settings_ai_api_key_save))
                }
                OutlinedButton(
                    onClick = {
                        onOpenAiApiKeyClear()
                        openAiApiKeyInput = ""
                    },
                    enabled = hasOpenAiApiKey,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.settings_ai_api_key_clear))
                }
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        EditorialDivider(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(28.dp))

        SectionTitle(text = stringResource(R.string.settings_sync))
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SelectableOptionRow(
                selected = syncProvider == SyncProvider.MANUAL_FOLDER,
                icon = Icons.Outlined.Folder,
                title = stringResource(R.string.settings_sync_manual_folder),
                description = if (syncProvider == SyncProvider.MANUAL_FOLDER && syncFolderLabel != null) {
                    stringResource(R.string.settings_sync_configured, syncFolderLabel)
                } else {
                    stringResource(R.string.settings_sync_manual_folder_description)
                },
                onClick = onChooseSyncFolder,
            )
            SelectableOptionRow(
                selected = syncProvider == SyncProvider.GOOGLE_DRIVE_APPDATA,
                icon = Icons.Outlined.CloudSync,
                title = stringResource(R.string.settings_sync_google_drive),
                description = googleDriveAccountLabel?.let {
                    stringResource(R.string.settings_sync_google_drive_configured, it)
                } ?: stringResource(R.string.settings_sync_google_drive_description),
                onClick = onConnectGoogleDrive,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.Sync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
            Column(modifier = Modifier.weight(1f)) {
                val hasActiveProvider = syncProvider != SyncProvider.NONE &&
                    (syncProvider != SyncProvider.GOOGLE_DRIVE_APPDATA || isGoogleDriveConnected)
                Text(
                    text = when (syncProvider) {
                        SyncProvider.MANUAL_FOLDER -> stringResource(R.string.settings_sync_folder)
                        SyncProvider.GOOGLE_DRIVE_APPDATA -> stringResource(R.string.settings_sync_google_drive)
                        SyncProvider.NONE -> stringResource(R.string.settings_sync_no_provider)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = when (syncProvider) {
                        SyncProvider.MANUAL_FOLDER -> stringResource(R.string.settings_sync_folder_hint)
                        SyncProvider.GOOGLE_DRIVE_APPDATA -> stringResource(R.string.settings_sync_google_drive_hint)
                        SyncProvider.NONE -> stringResource(R.string.settings_sync_provider_hint)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hasActiveProvider && lastSyncSummary == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_sync_first_run_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (lastSyncSummary != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.settings_sync_last_result,
                            lastSyncSummary.uploadedBooks,
                            lastSyncSummary.downloadedBooks,
                            lastSyncSummary.conflictedBooks,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (syncProvider == SyncProvider.MANUAL_FOLDER) {
                    OutlinedButton(
                        onClick = onChooseSyncFolder,
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.settings_sync_choose_folder))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                if (syncProvider == SyncProvider.GOOGLE_DRIVE_APPDATA) {
                    OutlinedButton(
                        onClick = onConnectGoogleDrive,
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudSync,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isGoogleDriveConnected) {
                                stringResource(R.string.settings_sync_google_drive_reconnect)
                            } else {
                                stringResource(R.string.settings_sync_google_drive_connect)
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    GoogleDriveBackupStatus(
                        stats = googleDriveBackupStats,
                        isLoading = isGoogleDriveBackupStatsLoading,
                        onRefresh = onRefreshGoogleDriveBackupStats,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Button(
                    onClick = onSyncNow,
                    enabled = !isSyncing && hasActiveProvider,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Sync,
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSyncing) {
                            stringResource(R.string.settings_sync_running)
                        } else if (hasActiveProvider && lastSyncSummary == null) {
                            stringResource(R.string.settings_sync_now_first_run)
                        } else {
                            stringResource(R.string.settings_sync_now)
                        },
                    )
                }
                if (syncProvider != SyncProvider.NONE) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onDisconnectSync,
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LinkOff,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.settings_sync_disconnect))
                    }
                }
                if (syncProvider == SyncProvider.GOOGLE_DRIVE_APPDATA && isGoogleDriveConnected) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onDeleteGoogleDriveBackup,
                        enabled = !isSyncing &&
                            !isGoogleDriveBackupStatsLoading &&
                            googleDriveBackupStats?.hasBackup == true,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.settings_sync_google_drive_delete_backup))
                    }
                }
                if (syncProvider != SyncProvider.NONE) {
                    Spacer(modifier = Modifier.height(18.dp))
                    SyncSwitchRow(
                        title = stringResource(R.string.settings_sync_auto),
                        description = stringResource(R.string.settings_sync_auto_description),
                        checked = isAutoSyncEnabled,
                        onCheckedChange = onAutoSyncEnabledChange,
                    )
                    SyncSwitchRow(
                        title = stringResource(R.string.settings_sync_wifi_only),
                        description = stringResource(R.string.settings_sync_wifi_only_description),
                        checked = isAutoSyncWifiOnly,
                        enabled = isAutoSyncEnabled,
                        onCheckedChange = onAutoSyncWifiOnlyChange,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
        }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun GoogleDriveBackupStatus(
    stats: GoogleDriveBackupStats?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_sync_google_drive_backup_status_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = when {
                isLoading -> stringResource(R.string.settings_sync_google_drive_backup_status_loading)
                stats == null -> stringResource(R.string.settings_sync_google_drive_backup_status_unknown)
                !stats.hasBackup -> stringResource(R.string.settings_sync_google_drive_backup_status_empty)
                else -> stringResource(
                    R.string.settings_sync_google_drive_backup_status_summary,
                    stats.syncedBooks,
                    stats.remoteFiles,
                    stats.totalBytes.toReadableDriveSize(),
                    stats.rootItems,
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (stats?.updatedAt != null) {
            Text(
                text = stringResource(
                    R.string.settings_sync_google_drive_backup_status_updated,
                    stats.updatedAt,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (stats != null && stats.unknownRootItems > 0) {
            Text(
                text = stringResource(
                    R.string.settings_sync_google_drive_backup_status_unknown_items,
                    stats.unknownRootItems,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        OutlinedButton(
            onClick = onRefresh,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Outlined.Sync,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = stringResource(R.string.settings_sync_google_drive_backup_refresh))
        }
    }
}

private fun Long.toReadableDriveSize(): String {
    if (this < 1024L) return "$this B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = this.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return if (value >= 10.0) {
        String.format(Locale.getDefault(), "%.0f %s", value, units[unitIndex])
    } else {
        String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
    }
}

@Composable
private fun SyncSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                },
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SelectableOptionRow(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
    }
}
