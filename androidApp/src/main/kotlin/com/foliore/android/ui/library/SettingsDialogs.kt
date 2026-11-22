package com.foliore.android.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.foliore.android.R
import com.foliore.android.ai.ChatGptModel
import com.foliore.android.ai.ChatGptOAuth
import com.foliore.android.ai.ChatGptSignInMode
import com.foliore.android.storage.ChatGptAccount
import com.foliore.android.storage.KindleEmailSettings
import com.foliore.android.storage.SyncProvider
import com.foliore.android.ui.theme.ThemeMode

@Composable
internal fun ThemeSettingsDialog(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    FolioreOptionsDialog(
        title = stringResource(R.string.settings_theme_dialog_title),
        onDismiss = onDismiss,
    ) {
        FolioreDialogSectionCard(label = stringResource(R.string.settings_theme)) {
            FolioreDialogOptionGrid(
                columns = if (maxWidth >= 420.dp) 3 else 1,
                options = listOf(
                    FolioreDialogOption(
                        label = stringResource(R.string.settings_theme_system),
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = {
                            onThemeModeChange(ThemeMode.SYSTEM)
                            onDismiss()
                        },
                    ),
                    FolioreDialogOption(
                        label = stringResource(R.string.settings_theme_light),
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = {
                            onThemeModeChange(ThemeMode.LIGHT)
                            onDismiss()
                        },
                    ),
                    FolioreDialogOption(
                        label = stringResource(R.string.settings_theme_dark),
                        selected = themeMode == ThemeMode.DARK,
                        onClick = {
                            onThemeModeChange(ThemeMode.DARK)
                            onDismiss()
                        },
                    ),
                ),
            )
        }
    }
}

@Composable
internal fun AiTextLanguageDialog(
    textLanguage: com.foliore.android.ai.AiTextLanguage,
    appLanguageName: String,
    onTextLanguageChange: (com.foliore.android.ai.AiTextLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    FolioreOptionsDialog(
        title = stringResource(R.string.settings_ai_text_language),
        onDismiss = onDismiss,
    ) {
        Column {
            Text(
                text = stringResource(R.string.settings_ai_text_language_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FolioreDialogSectionCard(label = stringResource(R.string.edit_metadata_field_language)) {
                FolioreDialogOptionGrid(
                    columns = 1,
                    options = listOf(
                        com.foliore.android.ai.AiTextLanguage.BOOK to
                            stringResource(R.string.settings_ai_text_language_book),
                        com.foliore.android.ai.AiTextLanguage.APP to
                            stringResource(R.string.settings_ai_text_language_app, appLanguageName),
                    ).map { (option, label) ->
                        FolioreDialogOption(
                            label = label,
                            selected = textLanguage == option,
                            onClick = {
                                onTextLanguageChange(option)
                                onDismiss()
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun SyncMethodDialog(
    syncProvider: SyncProvider,
    syncFolderLabel: String?,
    googleDriveAccountLabel: String?,
    isGoogleDriveConnected: Boolean,
    onChooseSyncFolder: () -> Unit,
    onConnectGoogleDrive: () -> Unit,
    onDismiss: () -> Unit,
) {
    FolioreOptionsDialog(
        title = stringResource(R.string.settings_sync_method_dialog_title),
        onDismiss = onDismiss,
    ) {
        FolioreDialogSectionCard(label = stringResource(R.string.settings_sync_method)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingsDialogActionRow(
                    icon = Icons.Outlined.Folder,
                    title = stringResource(R.string.settings_sync_manual_folder),
                    description = if (
                        syncProvider == SyncProvider.MANUAL_FOLDER && syncFolderLabel != null
                    ) {
                        stringResource(R.string.settings_sync_configured, syncFolderLabel)
                    } else {
                        stringResource(R.string.settings_sync_manual_folder_description)
                    },
                    selected = syncProvider == SyncProvider.MANUAL_FOLDER,
                    onClick = {
                        onDismiss()
                        onChooseSyncFolder()
                    },
                )
                SettingsDialogActionRow(
                    icon = Icons.Outlined.CloudSync,
                    title = stringResource(R.string.settings_sync_google_drive),
                    description = googleDriveAccountLabel?.let {
                        stringResource(R.string.settings_sync_google_drive_configured, it)
                    } ?: stringResource(R.string.settings_sync_google_drive_description),
                    selected = syncProvider == SyncProvider.GOOGLE_DRIVE_APPDATA &&
                        isGoogleDriveConnected,
                    onClick = {
                        onDismiss()
                        if (
                            syncProvider != SyncProvider.GOOGLE_DRIVE_APPDATA ||
                            !isGoogleDriveConnected
                        ) {
                            onConnectGoogleDrive()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsDialogActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Text(
                text = "✓",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
internal fun KindleSettingsDialog(
    settings: KindleEmailSettings,
    onKindleEmailSave: (String) -> Unit,
    onKindleEmailClear: () -> Unit,
    onKindleSmtpSave: (String, String, String, String, String) -> Unit,
    onKindleSmtpClear: () -> Unit,
    isGmailConnecting: Boolean,
    onConnectGmail: () -> Unit,
    onUseGmail: () -> Unit,
    onDisconnectGmail: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showSmtp by rememberSaveable { mutableStateOf(false) }
    var kindleEmailInput by rememberSaveable { mutableStateOf("") }
    var senderInput by rememberSaveable(settings.senderEmail) {
        mutableStateOf(settings.senderEmail.orEmpty())
    }
    var smtpHostInput by rememberSaveable(settings.smtpHost) {
        mutableStateOf(settings.smtpHost)
    }
    var smtpPortInput by rememberSaveable(settings.smtpPort) {
        mutableStateOf(settings.smtpPort.toString())
    }
    var smtpUserInput by rememberSaveable(settings.smtpUsername) {
        mutableStateOf(settings.smtpUsername.orEmpty())
    }
    var smtpPasswordInput by rememberSaveable { mutableStateOf("") }

    FolioreOptionsDialog(
        title = stringResource(R.string.settings_kindle_dialog_title),
        onDismiss = onDismiss,
    ) {
        Column {
            FolioreDialogSectionCard(label = stringResource(R.string.settings_kindle_email)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = settings.kindleEmail?.let {
                            stringResource(R.string.settings_kindle_email_configured, it)
                        } ?: stringResource(R.string.settings_kindle_email_missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = kindleEmailInput,
                        onValueChange = { kindleEmailInput = it },
                        label = { Text(stringResource(R.string.settings_kindle_email_label)) },
                        placeholder = { Text(stringResource(R.string.settings_kindle_email_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(5.dp),
                    )
                    SettingsDialogButtonPair(
                        primaryLabel = stringResource(R.string.settings_kindle_email_save),
                        primaryEnabled = kindleEmailInput.isNotBlank(),
                        onPrimaryClick = {
                            onKindleEmailSave(kindleEmailInput)
                            kindleEmailInput = ""
                        },
                        secondaryLabel = stringResource(R.string.settings_kindle_email_clear),
                        secondaryEnabled = settings.kindleEmail != null,
                        onSecondaryClick = {
                            onKindleEmailClear()
                            kindleEmailInput = ""
                        },
                    )
                }
            }

            FolioreDialogSectionCard(label = "Gmail") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(settings.gmailAccount ?: stringResource(R.string.gmail_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (settings.gmailAccount != null) {
                        Text(stringResource(if (settings.provider == com.foliore.android.storage.KindleEmailProvider.GMAIL)
                            R.string.gmail_active else R.string.gmail_available), style = MaterialTheme.typography.bodySmall)
                    }
                    SettingsDialogButtonPair(
                        primaryLabel = stringResource(when {
                            isGmailConnecting -> R.string.gmail_connecting
                            settings.gmailAccount == null -> R.string.gmail_connect
                            settings.provider == com.foliore.android.storage.KindleEmailProvider.GMAIL -> R.string.gmail_change_account
                            else -> R.string.gmail_use
                        }),
                        primaryEnabled = !isGmailConnecting,
                        onPrimaryClick = if (settings.gmailAccount != null && settings.provider != com.foliore.android.storage.KindleEmailProvider.GMAIL) onUseGmail else onConnectGmail,
                        secondaryLabel = stringResource(R.string.gmail_disconnect),
                        secondaryEnabled = settings.gmailAccount != null && !isGmailConnecting,
                        onSecondaryClick = onDisconnectGmail,
                    )
                }
            }
            KindleSenderReminder()
            TextButton(onClick = { showSmtp = !showSmtp }) {
                Text(stringResource(if (showSmtp) R.string.gmail_hide_smtp else R.string.gmail_show_smtp))
            }
            if (!showSmtp && settings.provider == com.foliore.android.storage.KindleEmailProvider.SMTP && settings.isSmtpReady) {
                Text(stringResource(R.string.settings_kindle_smtp_configured), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (showSmtp) FolioreDialogSectionCard(label = "SMTP") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = when {
                            settings.isSmtpReady -> stringResource(R.string.settings_kindle_smtp_configured)
                            settings.hasSmtpPassword -> stringResource(R.string.settings_kindle_smtp_incomplete)
                            else -> stringResource(R.string.settings_kindle_smtp_missing)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = senderInput,
                        onValueChange = { senderInput = it },
                        label = { Text(stringResource(R.string.settings_kindle_sender_label)) },
                        placeholder = { Text(stringResource(R.string.settings_kindle_sender_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(5.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = smtpHostInput,
                            onValueChange = { smtpHostInput = it },
                            label = { Text(stringResource(R.string.settings_kindle_smtp_host_label)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(5.dp),
                        )
                        OutlinedTextField(
                            value = smtpPortInput,
                            onValueChange = { smtpPortInput = it },
                            label = { Text(stringResource(R.string.settings_kindle_smtp_port_label)) },
                            modifier = Modifier.weight(0.46f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(5.dp),
                        )
                    }
                    OutlinedTextField(
                        value = smtpUserInput,
                        onValueChange = { smtpUserInput = it },
                        label = { Text(stringResource(R.string.settings_kindle_smtp_user_label)) },
                        placeholder = { Text(stringResource(R.string.settings_kindle_smtp_user_placeholder)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(5.dp),
                    )
                    OutlinedTextField(
                        value = smtpPasswordInput,
                        onValueChange = { smtpPasswordInput = it },
                        label = {
                            Text(
                                if (settings.hasSmtpPassword) {
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
                        shape = RoundedCornerShape(5.dp),
                    )
                    SettingsDialogButtonPair(
                        primaryLabel = stringResource(R.string.gmail_save_smtp),
                        primaryEnabled = !isGmailConnecting && senderInput.isNotBlank() &&
                            smtpHostInput.isNotBlank() &&
                            smtpPortInput.isNotBlank() &&
                            smtpUserInput.isNotBlank() &&
                            (smtpPasswordInput.isNotBlank() || settings.hasSmtpPassword),
                        onPrimaryClick = {
                            onKindleSmtpSave(
                                senderInput,
                                smtpHostInput,
                                smtpPortInput,
                                smtpUserInput,
                                smtpPasswordInput,
                            )
                            smtpPasswordInput = ""
                        },
                        secondaryLabel = stringResource(R.string.settings_kindle_smtp_clear),
                        secondaryEnabled = !isGmailConnecting && (settings.senderEmail != null ||
                            settings.smtpUsername != null ||
                            settings.hasSmtpPassword),
                        onSecondaryClick = {
                            onKindleSmtpClear()
                            senderInput = ""
                            smtpHostInput = settings.smtpHost
                            smtpPortInput = settings.smtpPort.toString()
                            smtpUserInput = ""
                            smtpPasswordInput = ""
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun AiSettingsDialog(
    aiSettings: AiSettingsState,
    onOpenAiApiKeySave: (String) -> Unit,
    onOpenAiApiKeyClear: () -> Unit,
    onChatGptSignIn: (ChatGptSignInMode) -> Unit,
    onChatGptCancelSignIn: () -> Unit,
    onChatGptSignOut: () -> Unit,
    onChatGptModelSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var openAiApiKeyInput by rememberSaveable { mutableStateOf("") }

    FolioreOptionsDialog(
        title = stringResource(R.string.settings_ai_features),
        onDismiss = onDismiss,
    ) {
        Column {
            Text(
                text = stringResource(R.string.settings_ai_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FolioreDialogSectionCard(label = stringResource(R.string.settings_ai_chatgpt)) {
                ChatGptSettingsSection(
                    account = aiSettings.chatGptAccount,
                    hasRegistration = aiSettings.hasRegistration,
                    isSigningIn = aiSettings.isChatGptSigningIn,
                    onSignIn = onChatGptSignIn,
                    onCancelSignIn = onChatGptCancelSignIn,
                    onSignOut = onChatGptSignOut,
                    onModelSelect = onChatGptModelSelect,
                )
            }
            FolioreDialogSectionCard(label = stringResource(R.string.settings_ai_openai_api)) {
                ApiKeySettingsForm(
                    description = stringResource(R.string.settings_ai_openai_api_description),
                    status = if (aiSettings.hasOpenAiApiKey) {
                        stringResource(R.string.settings_ai_api_key_configured)
                    } else {
                        stringResource(R.string.settings_ai_api_key_missing)
                    },
                    value = openAiApiKeyInput,
                    onValueChange = { openAiApiKeyInput = it },
                    label = stringResource(R.string.settings_ai_api_key_label),
                    placeholder = stringResource(R.string.settings_ai_api_key_placeholder),
                    saveLabel = stringResource(R.string.settings_ai_api_key_save),
                    clearLabel = stringResource(R.string.settings_ai_api_key_clear),
                    canClear = aiSettings.hasOpenAiApiKey,
                    onSave = {
                        onOpenAiApiKeySave(openAiApiKeyInput)
                        openAiApiKeyInput = ""
                    },
                    onClear = {
                        onOpenAiApiKeyClear()
                        openAiApiKeyInput = ""
                    },
                )
            }
        }
    }
}

@Composable
private fun ChatGptSettingsSection(
    account: ChatGptAccount?,
    hasRegistration: Boolean,
    isSigningIn: Boolean,
    onSignIn: (ChatGptSignInMode) -> Unit,
    onCancelSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onModelSelect: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            isSigningIn -> {
                Text(
                    text = stringResource(R.string.settings_ai_chatgpt_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsDialogButtonPair(
                    primaryLabel = stringResource(R.string.settings_ai_chatgpt_waiting_button),
                    primaryEnabled = false,
                    onPrimaryClick = {},
                    secondaryLabel = stringResource(R.string.settings_ai_chatgpt_cancel),
                    secondaryEnabled = true,
                    onSecondaryClick = onCancelSignIn,
                )
            }
            account != null -> {
                Text(
                    text = account.label?.let { stringResource(R.string.settings_ai_chatgpt_connected_as, it) }
                        ?: stringResource(R.string.settings_ai_chatgpt_connected),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(if (account.planUsageGranted) R.string.settings_ai_chatgpt_usage_note else R.string.settings_ai_chatgpt_no_plan),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (account.planUsageGranted && account.models.isNotEmpty()) {
                    ChatGptModelPicker(
                        models = account.models,
                        selected = account.model,
                        onSelect = onModelSelect,
                    )
                }
                SettingsDialogButtonPair(
                    primaryLabel = stringResource(if (account.planUsageGranted) R.string.chatgpt_manage_usage else R.string.settings_ai_chatgpt_enable_plan),
                    primaryEnabled = true,
                    onPrimaryClick = {
                        if (account.planUsageGranted) runCatching { uriHandler.openUri(ChatGptOAuth.USAGE_SETTINGS_URL) }
                        else onSignIn(ChatGptSignInMode.ENABLE_PLAN)
                    },
                    secondaryLabel = stringResource(R.string.settings_ai_chatgpt_sign_out),
                    secondaryEnabled = true,
                    onSecondaryClick = onSignOut,
                )
            }
            else -> {
                Text(
                    text = stringResource(R.string.settings_ai_chatgpt_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.settings_ai_chatgpt_plan_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Button(onClick = { onSignIn(ChatGptSignInMode.RETURNING) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_ai_chatgpt_sign_in))
                }
                if (hasRegistration) {
                    TextButton(onClick = { onSignIn(ChatGptSignInMode.DIFFERENT_ACCOUNT) }) {
                        Text(stringResource(R.string.settings_ai_chatgpt_other_account))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatGptModelPicker(
    models: List<ChatGptModel>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.settings_ai_chatgpt_model),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            TextButton(onClick = { expanded = true }) {
                Text((models.firstOrNull { it.slug == selected } ?: models.first()).displayName)
                Icon(imageVector = Icons.Outlined.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                models.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model.displayName) },
                        onClick = {
                            expanded = false
                            onSelect(model.slug)
                        },
                        trailingIcon = if (model.slug == selected) {
                            { Icon(imageVector = Icons.Outlined.Check, contentDescription = null) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiKeySettingsForm(
    description: String,
    status: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    saveLabel: String,
    clearLabel: String,
    canClear: Boolean,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(5.dp),
        )
        SettingsDialogButtonPair(
            primaryLabel = saveLabel,
            primaryEnabled = value.isNotBlank(),
            onPrimaryClick = onSave,
            secondaryLabel = clearLabel,
            secondaryEnabled = canClear,
            onSecondaryClick = onClear,
        )
    }
}

@Composable
private fun SettingsDialogButtonPair(
    primaryLabel: String,
    primaryEnabled: Boolean,
    onPrimaryClick: () -> Unit,
    secondaryLabel: String,
    secondaryEnabled: Boolean,
    onSecondaryClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = onPrimaryClick,
            enabled = primaryEnabled,
            modifier = Modifier.weight(1f),
        ) {
            Text(primaryLabel)
        }
        OutlinedButton(
            onClick = onSecondaryClick,
            enabled = secondaryEnabled,
            modifier = Modifier.weight(1f),
        ) {
            Text(secondaryLabel)
        }
    }
}
