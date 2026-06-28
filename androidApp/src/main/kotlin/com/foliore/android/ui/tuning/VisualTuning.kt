package com.foliore.android.ui.tuning

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class VisualTuningValues(
    val topChromeHeaderHeight: Float = 60f,
    val topChromeDividerHalfHeight: Float = 3f,
    val libraryGridTopExtra: Float = -16f,
    val libraryGridBottomExtra: Float = 0f,
    val libraryGridHorizontalPadding: Float = 16f,
    val libraryGridHorizontalSpacing: Float = 16f,
    val libraryGridVerticalSpacing: Float = 16f,
    val libraryHeaderStartPadding: Float = 16f,
    val libraryHeaderEndPadding: Float = 16f,
    val detailsMobileTopExtra: Float = -16f,
    val detailsMobileHorizontalPadding: Float = 16f,
    val detailsMobileBottomPadding: Float = 24f,
    val detailsTabletLeftHorizontalPadding: Float = 36f,
    val detailsTabletCoverVerticalPadding: Float = 24f,
    val detailsPaneHorizontalPadding: Float = 28f,
    val detailsPaneVerticalPadding: Float = 20f,
    val bottomBarHeight: Float = 80f,
    val bottomBarContentExtra: Float = 14f,
    val bottomBarItemTopPadding: Float = 0f,
    val bottomBarItemHeight: Float = 60f,
    val bottomBarItemBottomPadding: Float = 30f,
    val bottomNavigationMaxWidth: Float = 560f,
    val bottomNavigationHorizontalPadding: Float = 26f,
    val bottomNavigationTopLevelSpacing: Float = 18f,
    val bottomNavigationEditSpacing: Float = 5f,
    val bottomEditActionStripHeight: Float = 50f,
    val bottomEditActionStripButtonHeight: Float = 40f,
    val bottomEditContentExtra: Float = 22f,
    val bottomActionHorizontalPadding: Float = 22f,
    val bottomActionButtonSpacing: Float = 8f,
    val bottomActionIconSpacing: Float = 5f,
    val bottomActionIconSize: Float = 18f,
    val bottomActionTextSize: Float = 13f,
) {
    companion object {
        val Default = VisualTuningValues()
    }
}

val LocalVisualTuning = staticCompositionLocalOf { VisualTuningValues.Default }
