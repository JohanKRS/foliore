package com.foliore.android.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.foliore.android.R

@Composable
internal fun FolioreOptionsDialog(
    title: String,
    onDismiss: () -> Unit,
    scrollContent: Boolean = true,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            val horizontalMargin = if (maxWidth < 480.dp) 10.dp else 24.dp
            val verticalMargin = if (maxHeight < 720.dp) 12.dp else 24.dp
            val dialogMaxHeight = (maxHeight - verticalMargin * 2).coerceAtLeast(280.dp)
            val optionsMaxHeight = (dialogMaxHeight - 82.dp).coerceAtLeast(180.dp)
            val contentHorizontalPadding = if (maxWidth < 480.dp) 20.dp else 26.dp

            Surface(
                modifier = Modifier
                    .padding(horizontal = horizontalMargin, vertical = verticalMargin)
                    .widthIn(max = 780.dp)
                    .fillMaxWidth()
                    .heightIn(max = dialogMaxHeight),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.52f),
                ),
                tonalElevation = 0.dp,
                shadowElevation = 10.dp,
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = contentHorizontalPadding,
                        vertical = 22.dp,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.action_close),
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FolioreDialogDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    BoxWithConstraints(
                        modifier = Modifier
                            .heightIn(max = optionsMaxHeight)
                            .then(if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                        content = content,
                    )

                }
            }
        }
    }
}

@Composable
internal fun FolioreDialogSectionCard(
    label: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f),
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(9.dp))
            content()
        }
    }
}

@Composable
internal fun FolioreDialogOptionGrid(
    options: List<FolioreDialogOption>,
    columns: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(columns).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(columns) { index ->
                    val item = rowItems.getOrNull(index)
                    if (item != null) {
                        FolioreDialogOptionTile(
                            item = item,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

internal data class FolioreDialogOption(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun FolioreDialogDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(6.dp)
                .rotate(45f)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
        )
    }
}

@Composable
private fun FolioreDialogOptionTile(
    item: FolioreDialogOption,
    modifier: Modifier = Modifier,
) {
    val selectedColor = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clickable(onClick = item.onClick),
        shape = RoundedCornerShape(6.dp),
        color = if (item.selected) {
            selectedColor.copy(alpha = 0.1f)
        } else {
            Color.Transparent
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (item.selected) {
                selectedColor.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                ),
                color = if (item.selected) {
                    selectedColor
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.84f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.selected) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = selectedColor,
                )
            }
        }
    }
}
