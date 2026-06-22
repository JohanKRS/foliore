package com.foliore.android.ui.tuning

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class VisualTuningValues(
    val topChromeHeaderHeight: Float = 74f,
    val topChromeDividerHalfHeight: Float = 4f,
    val libraryGridTopExtra: Float = 0f,
    val libraryGridBottomExtra: Float = 8f,
    val libraryGridHorizontalPadding: Float = 28f,
    val libraryGridHorizontalSpacing: Float = 28f,
    val libraryGridVerticalSpacing: Float = 28f,
    val libraryHeaderStartPadding: Float = 28f,
    val libraryHeaderEndPadding: Float = 20f,
    val detailsMobileTopExtra: Float = 0f,
    val detailsMobileHorizontalPadding: Float = 36f,
    val detailsMobileBottomPadding: Float = 24f,
    val detailsTabletLeftHorizontalPadding: Float = 36f,
    val detailsTabletCoverVerticalPadding: Float = 24f,
    val detailsPaneHorizontalPadding: Float = 28f,
    val detailsPaneVerticalPadding: Float = 20f,
    val bottomBarHeight: Float = 94f,
    val bottomBarContentExtra: Float = 28f,
    val bottomBarItemTopPadding: Float = 8f,
    val bottomBarItemHeight: Float = 56f,
    val bottomBarItemBottomPadding: Float = 30f,
    val bottomNavigationMaxWidth: Float = 560f,
    val bottomNavigationHorizontalPadding: Float = 28f,
    val bottomNavigationTopLevelSpacing: Float = 18f,
    val bottomNavigationEditSpacing: Float = 10f,
    val bottomEditActionStripHeight: Float = 58f,
    val bottomEditActionStripButtonHeight: Float = 44f,
    val bottomEditContentExtra: Float = 48f,
    val bottomActionHorizontalPadding: Float = 36f,
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
