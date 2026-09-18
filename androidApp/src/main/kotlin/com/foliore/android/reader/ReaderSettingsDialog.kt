package com.foliore.android.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.foliore.android.R
import com.foliore.android.ui.library.FolioreOptionsDialog

@Composable
internal fun ReaderSettingsDialog(value: ReaderPreferences, fixed: Boolean, onChange: (ReaderPreferences) -> Unit, onDismiss: () -> Unit) {
    FolioreOptionsDialog(stringResource(R.string.reader_settings), onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReaderChoice(stringResource(R.string.reader_theme), value.theme, listOf("paper" to R.string.reader_theme_paper, "light" to R.string.reader_theme_light, "dark" to R.string.reader_theme_dark)) { onChange(value.copy(theme = it)) }
            ReaderToggle(stringResource(R.string.reader_adapt_illustrations), value.adaptIllustrations) { onChange(value.copy(adaptIllustrations = it)) }
            ReaderChoice(stringResource(R.string.reader_mode), value.mode, listOf("pages" to R.string.reader_pages, "scroll" to R.string.reader_scroll), !fixed) { onChange(value.copy(mode = it)) }
            ReaderToggle(stringResource(R.string.reader_publisher_styles), value.publisherStyles, !fixed) { onChange(value.copy(publisherStyles = it)) }
            if (fixed) Text(stringResource(R.string.reader_fixed_layout), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val editable = !value.publisherStyles && !fixed
            ReaderChoice(stringResource(R.string.reader_font), value.font, listOf("serif" to R.string.reader_font_serif, "sans-serif" to R.string.reader_font_sans, "monospace" to R.string.reader_font_mono), editable) { onChange(value.copy(font = it)) }
            ReaderSlider(stringResource(R.string.reader_font_size), value.fontSize.toFloat(), 12f..36f, editable) { onChange(value.copy(fontSize = it.toInt())) }
            ReaderSlider(stringResource(R.string.reader_line_height), value.lineHeight.toFloat(), 1.2f..2.2f, editable) { onChange(value.copy(lineHeight = (it * 10).toInt() / 10.0)) }
            ReaderSlider(stringResource(R.string.reader_margins), value.margin.toFloat(), 8f..64f, !fixed) { onChange(value.copy(margin = it.toInt())) }
            ReaderToggle(stringResource(R.string.reader_justify), value.justify, editable) { onChange(value.copy(justify = it)) }
            ReaderToggle(stringResource(R.string.reader_keep_screen), value.keepScreenOn) { onChange(value.copy(keepScreenOn = it)) }
            ReaderToggle(stringResource(R.string.reader_system_brightness), value.brightness < 0) { onChange(value.copy(brightness = if (it) -1f else .5f)) }
            if (value.brightness >= 0) ReaderSlider(stringResource(R.string.reader_brightness), value.brightness, .05f..1f) { onChange(value.copy(brightness = it)) }
            Text(stringResource(R.string.reader_sync_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { onChange(ReaderPreferences()) }) { Text(stringResource(R.string.reader_reset_settings)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderChoice(label: String, current: String, choices: List<Pair<String, Int>>, enabled: Boolean = true, onSelect: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { (key, name) ->
                FilterChip(selected = current == key, onClick = { onSelect(key) }, enabled = enabled, label = { Text(stringResource(name)) },
                    colors = FilterChipDefaults.filterChipColors(containerColor = Color.Transparent,
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = .14f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (current == key) .12f else .4f)))
            }
        }
    }
}

@Composable
private fun ReaderToggle(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.bodyMedium)
        Switch(checked, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = label },
            colors = SwitchDefaults.colors(uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f),
                uncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = .4f)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean = true, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Slider(value = value, onValueChange = onChange, valueRange = range, enabled = enabled, modifier = Modifier.semantics { contentDescription = label },
            thumb = { Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .35f), CircleShape)) },
            track = { state -> SliderDefaults.Track(state, enabled = enabled, modifier = Modifier.height(4.dp), drawStopIndicator = null, thumbTrackGapSize = 0.dp,
                colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))) })
    }
}
