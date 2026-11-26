package com.aibuilt.wifihack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val TerminalColors = darkColorScheme(
    primary = TermGreen,
    onPrimary = TermBg,
    secondary = TermGreenDim,
    onSecondary = TermBg,
    tertiary = TermAmber,
    background = TermBg,
    onBackground = TermText,
    surface = TermSurface,
    onSurface = TermText,
    surfaceVariant = TermSurfaceVariant,
    onSurfaceVariant = TermTextDim,
    outline = TermOutline,
    error = TermRed,
    onError = TermBg
)

@Composable
fun WifiHackTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TerminalColors,
        typography = TerminalTypography,
        content = content
    )
}
