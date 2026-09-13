package com.foliore.android.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.foliore.android.R
import com.foliore.android.storage.LauncherIcon

@Composable
internal fun LauncherIconSettingsDialog(
    selectedIcon: LauncherIcon,
    onSelect: (LauncherIcon) -> Boolean,
    onDismiss: () -> Unit,
) {
    var failed by rememberSaveable { mutableStateOf(false) }
    FolioreOptionsDialog(
        title = stringResource(R.string.settings_launcher_icon),
        onDismiss = onDismiss,
    ) {
        val columns = if (maxWidth >= 480.dp) 4 else 2
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LauncherIcon.entries.chunked(columns).forEach { icons ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    icons.forEach { icon ->
                        val selected = selectedIcon == icon
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    BorderStroke(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    ),
                                    RoundedCornerShape(8.dp),
                                )
                                .selectable(selected, role = Role.RadioButton, onClick = {
                                    if (onSelect(icon)) onDismiss() else failed = true
                                })
                                .padding(horizontal = 8.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(
                                painter = painterResource(icon.previewResource()),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selected, onClick = null)
                                Spacer(Modifier.size(6.dp))
                                Text(icon.label, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
            if (failed) {
                Text(
                    stringResource(R.string.settings_launcher_icon_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                stringResource(R.string.settings_launcher_icon_hint),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun LauncherIcon.previewResource(): Int = when (this) {
    LauncherIcon.DAWN -> R.drawable.ic_launcher_dawn_preview
    LauncherIcon.DAY -> R.drawable.ic_launcher_day_preview
    LauncherIcon.DUSK -> R.drawable.ic_launcher_dusk_preview
    LauncherIcon.NIGHT -> R.drawable.ic_launcher_night_preview
}
