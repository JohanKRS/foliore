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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.foliore.android.R
import com.foliore.android.storage.AppLanguage
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
    appLanguage: AppLanguage,
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
    onChooseAppLanguage: () -> Unit,
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
    var showThemePicker by rememberSaveable { mutableStateOf(false) }
    var showKindleSettings by rememberSaveable { mutableStateOf(false) }
    var showExperimentalSettings by rememberSaveable { mutableStateOf(false) }
    var showSyncMethodPicker by rememberSaveable { mutableStateOf(false) }

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
        CompactSettingsRow(
            icon = Icons.Outlined.Palette,
            title = stringResource(R.string.settings_theme),
            description = stringResource(themeMode.labelResource()),
            onClick = { showThemePicker = true },
        )
        CompactSettingsRow(
            icon = Icons.Outlined.Language,
            title = stringResource(R.string.settings_language),
            description = stringResource(appLanguage.labelResource()),
            onClick = onChooseAppLanguage,
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
        CompactSettingsRow(
            icon = Icons.Outlined.Email,
            title = stringResource(R.string.settings_kindle_email),
            description = when {
                kindleEmailSettings.isReady -> stringResource(R.string.settings_kindle_smtp_configured)
                kindleEmailSettings.kindleEmail != null -> stringResource(
                    R.string.settings_kindle_email_configured,
                    kindleEmailSettings.kindleEmail,
                )
                else -> stringResource(R.string.settings_kindle_email_missing)
            },
            onClick = { showKindleSettings = true },
        )

        Spacer(modifier = Modifier.height(28.dp))
        EditorialDivider(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(28.dp))

        SectionTitle(text = stringResource(R.string.settings_experimental))
        Spacer(modifier = Modifier.height(8.dp))
        CompactSettingsRow(
            icon = Icons.Outlined.AutoAwesome,
            title = stringResource(R.string.settings_experimental),
            description = stringResource(R.string.settings_experimental_description),
            onClick = { showExperimentalSettings = true },
        )
        Spacer(modifier = Modifier.height(28.dp))
        EditorialDivider(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(28.dp))

        SectionTitle(text = stringResource(R.string.settings_sync))
        Spacer(modifier = Modifier.height(12.dp))
        CompactSettingsRow(
            icon = Icons.Outlined.CloudSync,
            title = stringResource(R.string.settings_sync_method),
            description = when (syncProvider) {
                SyncProvider.MANUAL_FOLDER -> syncFolderLabel?.let {
                    stringResource(R.string.settings_sync_configured, it)
                } ?: stringResource(R.string.settings_sync_manual_folder)
                SyncProvider.GOOGLE_DRIVE_APPDATA -> googleDriveAccountLabel?.let {
                    stringResource(R.string.settings_sync_google_drive_configured, it)
                } ?: stringResource(R.string.settings_sync_google_drive)
                SyncProvider.NONE -> stringResource(R.string.settings_sync_no_provider)
            },
            onClick = { showSyncMethodPicker = true },
        )
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
                val hasKnownSync = lastSyncSummary != null ||
                    (
                        syncProvider == SyncProvider.GOOGLE_DRIVE_APPDATA &&
                            googleDriveBackupStats?.hasBackup == true
                    )
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
                if (hasActiveProvider && !hasKnownSync) {
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
                    if (!isGoogleDriveConnected) {
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
                            Text(stringResource(R.string.settings_sync_google_drive_connect))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
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
                        } else if (hasActiveProvider && !hasKnownSync) {
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

    if (showThemePicker) {
        ThemeSettingsDialog(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onDismiss = { showThemePicker = false },
        )
    }
    if (showKindleSettings) {
        KindleSettingsDialog(
            settings = kindleEmailSettings,
            onKindleEmailSave = onKindleEmailSave,
            onKindleEmailClear = onKindleEmailClear,
            onKindleSmtpSave = onKindleSmtpSave,
            onKindleSmtpClear = onKindleSmtpClear,
            onDismiss = { showKindleSettings = false },
        )
    }
    if (showExperimentalSettings) {
        ExperimentalSettingsDialog(
            hasGoogleBooksApiKey = hasGoogleBooksApiKey,
            hasOpenAiApiKey = hasOpenAiApiKey,
            onGoogleBooksApiKeySave = onGoogleBooksApiKeySave,
            onGoogleBooksApiKeyClear = onGoogleBooksApiKeyClear,
            onOpenAiApiKeySave = onOpenAiApiKeySave,
            onOpenAiApiKeyClear = onOpenAiApiKeyClear,
            onDismiss = { showExperimentalSettings = false },
        )
    }
    if (showSyncMethodPicker) {
        SyncMethodDialog(
            syncProvider = syncProvider,
            syncFolderLabel = syncFolderLabel,
            googleDriveAccountLabel = googleDriveAccountLabel,
            isGoogleDriveConnected = isGoogleDriveConnected,
            onChooseSyncFolder = onChooseSyncFolder,
            onConnectGoogleDrive = onConnectGoogleDrive,
            onDismiss = { showSyncMethodPicker = false },
        )
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
                stats == null && isLoading -> {
                    stringResource(R.string.settings_sync_google_drive_backup_status_loading)
                }
                stats == null -> stringResource(R.string.settings_sync_google_drive_backup_status_unknown)
                !stats.hasBackup -> stringResource(R.string.settings_sync_google_drive_backup_status_empty)
                else -> stringResource(
                    R.string.settings_sync_google_drive_backup_status_summary,
                    stats.syncedBooks,
                    stats.remoteFiles,
                    stats.totalBytes.toReadableDriveSize(),
                )
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isLoading && stats != null) {
            Text(
                text = stringResource(R.string.settings_sync_google_drive_backup_status_refreshing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                disabledCheckedThumbColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                disabledUncheckedThumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
                disabledUncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
                disabledUncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            ),
        )
    }
}

@Composable
private fun CompactSettingsRow(
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        )
    }
}

private fun ThemeMode.labelResource(): Int {
    return when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
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
