package com.foliore.android.ui.library

import androidx.compose.runtime.Composable
import com.foliore.android.ui.theme.ThemeMode

@Composable
fun SettingsRoute(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    SettingsScreen(
        themeMode = themeMode,
        onThemeModeChange = onThemeModeChange,
    )
}

