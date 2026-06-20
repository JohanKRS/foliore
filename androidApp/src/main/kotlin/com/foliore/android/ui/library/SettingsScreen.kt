package com.foliore.android.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Folder
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
import com.foliore.android.storage.KindleEmailSettings
import com.foliore.android.ui.theme.ThemeMode
import com.foliore.android.viewmodel.SyncSummary

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    hasOpenAiApiKey: Boolean,
    hasGoogleBooksApiKey: Boolean,
    kindleEmailSettings: KindleEmailSettings,
    syncFolderLabel: String?,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
        ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(16.dp))
        EditorialDivider(modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(28.dp))

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
                Text(
                    text = stringResource(R.string.settings_sync_folder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = syncFolderLabel?.let {
                        stringResource(R.string.settings_sync_configured, it)
                    } ?: stringResource(R.string.settings_sync_not_configured),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_sync_folder_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (syncFolderLabel != null && lastSyncSummary == null) {
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
                Button(
                    onClick = onSyncNow,
                    enabled = !isSyncing && syncFolderLabel != null,
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
                        } else if (syncFolderLabel != null && lastSyncSummary == null) {
                            stringResource(R.string.settings_sync_now_first_run)
                        } else {
                            stringResource(R.string.settings_sync_now)
                        },
                    )
                }
                if (syncFolderLabel != null) {
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
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
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
