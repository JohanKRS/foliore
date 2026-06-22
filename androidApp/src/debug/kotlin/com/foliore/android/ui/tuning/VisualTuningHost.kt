package com.foliore.android.ui.tuning

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun VisualTuningHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("foliore_visual_tuning", Context.MODE_PRIVATE)
    }
    var values by remember(prefs) {
        mutableStateOf(prefs.readVisualTuningValues())
    }
    var isOpen by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalVisualTuning provides values) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
            TuningHandle(
                isOpen = isOpen,
                onClick = { isOpen = !isOpen },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
            AnimatedVisibility(
                visible = isOpen,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                VisualTuningPanel(
                    values = values,
                    onValuesChange = { next ->
                        values = next
                        prefs.writeVisualTuningValues(next)
                    },
                    onClose = { isOpen = false },
                    onReset = {
                        values = VisualTuningValues.Default
                        prefs.edit().clear().apply()
                    },
                )
            }
        }
    }
}

@Composable
private fun TuningHandle(
    isOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(end = 0.dp)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.primary.copy(alpha = if (isOpen) 0.92f else 0.58f),
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp),
        shadowElevation = 8.dp,
    ) {
        Text(
            text = if (isOpen) "Fechar" else "Tune",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun VisualTuningPanel(
    values: VisualTuningValues,
    onValuesChange: (VisualTuningValues) -> Unit,
    onClose: () -> Unit,
    onReset: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 18.dp,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ajustes visuais",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Debug only. Os valores mudam em tempo real.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onClose) {
                    Text("Fechar")
                }
            }

            TuningSection("Topo / vidro")
            TuningSlider("Header altura", values.topChromeHeaderHeight, 56f..96f) {
                onValuesChange(values.copy(topChromeHeaderHeight = it))
            }
            TuningSlider("Divisor offset", values.topChromeDividerHalfHeight, 0f..10f) {
                onValuesChange(values.copy(topChromeDividerHalfHeight = it))
            }

            TuningSection("Biblioteca")
            TuningSlider("Grid top extra", values.libraryGridTopExtra, -24f..48f) {
                onValuesChange(values.copy(libraryGridTopExtra = it))
            }
            TuningSlider("Grid bottom extra", values.libraryGridBottomExtra, 0f..64f) {
                onValuesChange(values.copy(libraryGridBottomExtra = it))
            }
            TuningSlider("Grid padding horizontal", values.libraryGridHorizontalPadding, 8f..48f) {
                onValuesChange(values.copy(libraryGridHorizontalPadding = it))
            }
            TuningSlider("Grid gap horizontal", values.libraryGridHorizontalSpacing, 8f..48f) {
                onValuesChange(values.copy(libraryGridHorizontalSpacing = it))
            }
            TuningSlider("Grid gap vertical", values.libraryGridVerticalSpacing, 8f..56f) {
                onValuesChange(values.copy(libraryGridVerticalSpacing = it))
            }
            TuningSlider("Header start", values.libraryHeaderStartPadding, 12f..44f) {
                onValuesChange(values.copy(libraryHeaderStartPadding = it))
            }
            TuningSlider("Header end", values.libraryHeaderEndPadding, 12f..44f) {
                onValuesChange(values.copy(libraryHeaderEndPadding = it))
            }

            TuningSection("Detalhes")
            TuningSlider("Mobile top extra", values.detailsMobileTopExtra, -24f..48f) {
                onValuesChange(values.copy(detailsMobileTopExtra = it))
            }
            TuningSlider("Mobile horizontal", values.detailsMobileHorizontalPadding, 12f..56f) {
                onValuesChange(values.copy(detailsMobileHorizontalPadding = it))
            }
            TuningSlider("Mobile bottom", values.detailsMobileBottomPadding, 0f..48f) {
                onValuesChange(values.copy(detailsMobileBottomPadding = it))
            }
            TuningSlider("Tablet capa horizontal", values.detailsTabletLeftHorizontalPadding, 16f..56f) {
                onValuesChange(values.copy(detailsTabletLeftHorizontalPadding = it))
            }
            TuningSlider("Tablet capa vertical", values.detailsTabletCoverVerticalPadding, 0f..40f) {
                onValuesChange(values.copy(detailsTabletCoverVerticalPadding = it))
            }
            TuningSlider("Tablet pane horizontal", values.detailsPaneHorizontalPadding, 16f..56f) {
                onValuesChange(values.copy(detailsPaneHorizontalPadding = it))
            }
            TuningSlider("Tablet pane vertical", values.detailsPaneVerticalPadding, 0f..40f) {
                onValuesChange(values.copy(detailsPaneVerticalPadding = it))
            }

            TuningSection("Barra inferior")
            TuningSlider("Nav altura", values.bottomBarHeight, 72f..116f) {
                onValuesChange(values.copy(bottomBarHeight = it))
            }
            TuningSlider("Nav content extra", values.bottomBarContentExtra, 0f..56f) {
                onValuesChange(values.copy(bottomBarContentExtra = it))
            }
            TuningSlider("Nav item top", values.bottomBarItemTopPadding, 0f..20f) {
                onValuesChange(values.copy(bottomBarItemTopPadding = it))
            }
            TuningSlider("Nav item altura", values.bottomBarItemHeight, 40f..72f) {
                onValuesChange(values.copy(bottomBarItemHeight = it))
            }
            TuningSlider("Nav item bottom", values.bottomBarItemBottomPadding, 12f..44f) {
                onValuesChange(values.copy(bottomBarItemBottomPadding = it))
            }
            TuningSlider("Nav largura max", values.bottomNavigationMaxWidth, 420f..720f) {
                onValuesChange(values.copy(bottomNavigationMaxWidth = it))
            }
            TuningSlider("Nav padding horizontal", values.bottomNavigationHorizontalPadding, 12f..48f) {
                onValuesChange(values.copy(bottomNavigationHorizontalPadding = it))
            }
            TuningSlider("Nav gap normal", values.bottomNavigationTopLevelSpacing, 4f..32f) {
                onValuesChange(values.copy(bottomNavigationTopLevelSpacing = it))
            }
            TuningSlider("Nav gap detalhes", values.bottomNavigationEditSpacing, 0f..28f) {
                onValuesChange(values.copy(bottomNavigationEditSpacing = it))
            }

            TuningSection("Ações mobile")
            TuningSlider("Faixa altura", values.bottomEditActionStripHeight, 42f..76f) {
                onValuesChange(values.copy(bottomEditActionStripHeight = it))
            }
            TuningSlider("Botão altura", values.bottomEditActionStripButtonHeight, 32f..56f) {
                onValuesChange(values.copy(bottomEditActionStripButtonHeight = it))
            }
            TuningSlider("Content extra", values.bottomEditContentExtra, 0f..72f) {
                onValuesChange(values.copy(bottomEditContentExtra = it))
            }
            TuningSlider("Ações horizontal", values.bottomActionHorizontalPadding, 12f..56f) {
                onValuesChange(values.copy(bottomActionHorizontalPadding = it))
            }
            TuningSlider("Ações gap", values.bottomActionButtonSpacing, 0f..20f) {
                onValuesChange(values.copy(bottomActionButtonSpacing = it))
            }
            TuningSlider("Ações ícone gap", values.bottomActionIconSpacing, 0f..12f) {
                onValuesChange(values.copy(bottomActionIconSpacing = it))
            }
            TuningSlider("Ações ícone", values.bottomActionIconSize, 14f..24f) {
                onValuesChange(values.copy(bottomActionIconSize = it))
            }
            TuningSlider("Ações texto", values.bottomActionTextSize, 10f..16f) {
                onValuesChange(values.copy(bottomActionTextSize = it))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Resetar")
                }
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(values.toDebugSnapshot()))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Copiar valores")
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun TuningSection(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun TuningSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${(value * 10).toInt() / 10f}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = { onChange((it * 2f).toInt() / 2f) },
            valueRange = range,
        )
    }
}

private fun android.content.SharedPreferences.readVisualTuningValues(): VisualTuningValues {
    val default = VisualTuningValues.Default
    return VisualTuningValues(
        topChromeHeaderHeight = getFloat("topChromeHeaderHeight", default.topChromeHeaderHeight),
        topChromeDividerHalfHeight = getFloat("topChromeDividerHalfHeight", default.topChromeDividerHalfHeight),
        libraryGridTopExtra = getFloat("libraryGridTopExtra", default.libraryGridTopExtra),
        libraryGridBottomExtra = getFloat("libraryGridBottomExtra", default.libraryGridBottomExtra),
        libraryGridHorizontalPadding = getFloat("libraryGridHorizontalPadding", default.libraryGridHorizontalPadding),
        libraryGridHorizontalSpacing = getFloat("libraryGridHorizontalSpacing", default.libraryGridHorizontalSpacing),
        libraryGridVerticalSpacing = getFloat("libraryGridVerticalSpacing", default.libraryGridVerticalSpacing),
        libraryHeaderStartPadding = getFloat("libraryHeaderStartPadding", default.libraryHeaderStartPadding),
        libraryHeaderEndPadding = getFloat("libraryHeaderEndPadding", default.libraryHeaderEndPadding),
        detailsMobileTopExtra = getFloat("detailsMobileTopExtra", default.detailsMobileTopExtra),
        detailsMobileHorizontalPadding = getFloat("detailsMobileHorizontalPadding", default.detailsMobileHorizontalPadding),
        detailsMobileBottomPadding = getFloat("detailsMobileBottomPadding", default.detailsMobileBottomPadding),
        detailsTabletLeftHorizontalPadding = getFloat("detailsTabletLeftHorizontalPadding", default.detailsTabletLeftHorizontalPadding),
        detailsTabletCoverVerticalPadding = getFloat("detailsTabletCoverVerticalPadding", default.detailsTabletCoverVerticalPadding),
        detailsPaneHorizontalPadding = getFloat("detailsPaneHorizontalPadding", default.detailsPaneHorizontalPadding),
        detailsPaneVerticalPadding = getFloat("detailsPaneVerticalPadding", default.detailsPaneVerticalPadding),
        bottomBarHeight = getFloat("bottomBarHeight", default.bottomBarHeight),
        bottomBarContentExtra = getFloat("bottomBarContentExtra", default.bottomBarContentExtra),
        bottomBarItemTopPadding = getFloat("bottomBarItemTopPadding", default.bottomBarItemTopPadding),
        bottomBarItemHeight = getFloat("bottomBarItemHeight", default.bottomBarItemHeight),
        bottomBarItemBottomPadding = getFloat("bottomBarItemBottomPadding", default.bottomBarItemBottomPadding),
        bottomNavigationMaxWidth = getFloat("bottomNavigationMaxWidth", default.bottomNavigationMaxWidth),
        bottomNavigationHorizontalPadding = getFloat("bottomNavigationHorizontalPadding", default.bottomNavigationHorizontalPadding),
        bottomNavigationTopLevelSpacing = getFloat("bottomNavigationTopLevelSpacing", default.bottomNavigationTopLevelSpacing),
        bottomNavigationEditSpacing = getFloat("bottomNavigationEditSpacing", default.bottomNavigationEditSpacing),
        bottomEditActionStripHeight = getFloat("bottomEditActionStripHeight", default.bottomEditActionStripHeight),
        bottomEditActionStripButtonHeight = getFloat("bottomEditActionStripButtonHeight", default.bottomEditActionStripButtonHeight),
        bottomEditContentExtra = getFloat("bottomEditContentExtra", default.bottomEditContentExtra),
        bottomActionHorizontalPadding = getFloat("bottomActionHorizontalPadding", default.bottomActionHorizontalPadding),
        bottomActionButtonSpacing = getFloat("bottomActionButtonSpacing", default.bottomActionButtonSpacing),
        bottomActionIconSpacing = getFloat("bottomActionIconSpacing", default.bottomActionIconSpacing),
        bottomActionIconSize = getFloat("bottomActionIconSize", default.bottomActionIconSize),
        bottomActionTextSize = getFloat("bottomActionTextSize", default.bottomActionTextSize),
    )
}

private fun android.content.SharedPreferences.writeVisualTuningValues(values: VisualTuningValues) {
    edit()
        .putFloat("topChromeHeaderHeight", values.topChromeHeaderHeight)
        .putFloat("topChromeDividerHalfHeight", values.topChromeDividerHalfHeight)
        .putFloat("libraryGridTopExtra", values.libraryGridTopExtra)
        .putFloat("libraryGridBottomExtra", values.libraryGridBottomExtra)
        .putFloat("libraryGridHorizontalPadding", values.libraryGridHorizontalPadding)
        .putFloat("libraryGridHorizontalSpacing", values.libraryGridHorizontalSpacing)
        .putFloat("libraryGridVerticalSpacing", values.libraryGridVerticalSpacing)
        .putFloat("libraryHeaderStartPadding", values.libraryHeaderStartPadding)
        .putFloat("libraryHeaderEndPadding", values.libraryHeaderEndPadding)
        .putFloat("detailsMobileTopExtra", values.detailsMobileTopExtra)
        .putFloat("detailsMobileHorizontalPadding", values.detailsMobileHorizontalPadding)
        .putFloat("detailsMobileBottomPadding", values.detailsMobileBottomPadding)
        .putFloat("detailsTabletLeftHorizontalPadding", values.detailsTabletLeftHorizontalPadding)
        .putFloat("detailsTabletCoverVerticalPadding", values.detailsTabletCoverVerticalPadding)
        .putFloat("detailsPaneHorizontalPadding", values.detailsPaneHorizontalPadding)
        .putFloat("detailsPaneVerticalPadding", values.detailsPaneVerticalPadding)
        .putFloat("bottomBarHeight", values.bottomBarHeight)
        .putFloat("bottomBarContentExtra", values.bottomBarContentExtra)
        .putFloat("bottomBarItemTopPadding", values.bottomBarItemTopPadding)
        .putFloat("bottomBarItemHeight", values.bottomBarItemHeight)
        .putFloat("bottomBarItemBottomPadding", values.bottomBarItemBottomPadding)
        .putFloat("bottomNavigationMaxWidth", values.bottomNavigationMaxWidth)
        .putFloat("bottomNavigationHorizontalPadding", values.bottomNavigationHorizontalPadding)
        .putFloat("bottomNavigationTopLevelSpacing", values.bottomNavigationTopLevelSpacing)
        .putFloat("bottomNavigationEditSpacing", values.bottomNavigationEditSpacing)
        .putFloat("bottomEditActionStripHeight", values.bottomEditActionStripHeight)
        .putFloat("bottomEditActionStripButtonHeight", values.bottomEditActionStripButtonHeight)
        .putFloat("bottomEditContentExtra", values.bottomEditContentExtra)
        .putFloat("bottomActionHorizontalPadding", values.bottomActionHorizontalPadding)
        .putFloat("bottomActionButtonSpacing", values.bottomActionButtonSpacing)
        .putFloat("bottomActionIconSpacing", values.bottomActionIconSpacing)
        .putFloat("bottomActionIconSize", values.bottomActionIconSize)
        .putFloat("bottomActionTextSize", values.bottomActionTextSize)
        .apply()
}

private fun VisualTuningValues.toDebugSnapshot(): String {
    return """
        VisualTuningValues(
            topChromeHeaderHeight = ${topChromeHeaderHeight}f,
            topChromeDividerHalfHeight = ${topChromeDividerHalfHeight}f,
            libraryGridTopExtra = ${libraryGridTopExtra}f,
            libraryGridBottomExtra = ${libraryGridBottomExtra}f,
            libraryGridHorizontalPadding = ${libraryGridHorizontalPadding}f,
            libraryGridHorizontalSpacing = ${libraryGridHorizontalSpacing}f,
            libraryGridVerticalSpacing = ${libraryGridVerticalSpacing}f,
            libraryHeaderStartPadding = ${libraryHeaderStartPadding}f,
            libraryHeaderEndPadding = ${libraryHeaderEndPadding}f,
            detailsMobileTopExtra = ${detailsMobileTopExtra}f,
            detailsMobileHorizontalPadding = ${detailsMobileHorizontalPadding}f,
            detailsMobileBottomPadding = ${detailsMobileBottomPadding}f,
            detailsTabletLeftHorizontalPadding = ${detailsTabletLeftHorizontalPadding}f,
            detailsTabletCoverVerticalPadding = ${detailsTabletCoverVerticalPadding}f,
            detailsPaneHorizontalPadding = ${detailsPaneHorizontalPadding}f,
            detailsPaneVerticalPadding = ${detailsPaneVerticalPadding}f,
            bottomBarHeight = ${bottomBarHeight}f,
            bottomBarContentExtra = ${bottomBarContentExtra}f,
            bottomBarItemTopPadding = ${bottomBarItemTopPadding}f,
            bottomBarItemHeight = ${bottomBarItemHeight}f,
            bottomBarItemBottomPadding = ${bottomBarItemBottomPadding}f,
            bottomNavigationMaxWidth = ${bottomNavigationMaxWidth}f,
            bottomNavigationHorizontalPadding = ${bottomNavigationHorizontalPadding}f,
            bottomNavigationTopLevelSpacing = ${bottomNavigationTopLevelSpacing}f,
            bottomNavigationEditSpacing = ${bottomNavigationEditSpacing}f,
            bottomEditActionStripHeight = ${bottomEditActionStripHeight}f,
            bottomEditActionStripButtonHeight = ${bottomEditActionStripButtonHeight}f,
            bottomEditContentExtra = ${bottomEditContentExtra}f,
            bottomActionHorizontalPadding = ${bottomActionHorizontalPadding}f,
            bottomActionButtonSpacing = ${bottomActionButtonSpacing}f,
            bottomActionIconSpacing = ${bottomActionIconSpacing}f,
            bottomActionIconSize = ${bottomActionIconSize}f,
            bottomActionTextSize = ${bottomActionTextSize}f,
        )
    """.trimIndent()
}
