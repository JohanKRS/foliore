package com.foliore.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightScheme = lightColorScheme(
    primary = FolioreBrass,
    onPrimary = FoliorePaper,
    secondary = FolioreMoss,
    onSecondary = FoliorePaper,
    background = FolioreIvory,
    onBackground = FolioreInk,
    surface = FoliorePaper,
    onSurface = FolioreInk,
    surfaceVariant = ColorTokens.lightSurfaceVariant,
    onSurfaceVariant = FolioreMuted,
    outline = FolioreLine,
    error = FolioreTerracotta,
)

private val DarkScheme = darkColorScheme(
    primary = FolioreDarkBrass,
    onPrimary = FolioreDarkBackground,
    secondary = FolioreDarkMoss,
    onSecondary = FolioreDarkBackground,
    background = FolioreDarkBackground,
    onBackground = FolioreDarkInk,
    surface = FolioreDarkSurface,
    onSurface = FolioreDarkInk,
    surfaceVariant = ColorTokens.darkSurfaceVariant,
    onSurfaceVariant = FolioreDarkMuted,
    outline = FolioreDarkLine,
    error = ColorTokens.darkError,
)

@Composable
fun FolioreTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = FolioreTypography,
        content = content,
    )
}

private object ColorTokens {
    val lightSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFF0E7DA)
    val darkSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF242820)
    val darkError = androidx.compose.ui.graphics.Color(0xFFE9A18B)
}

