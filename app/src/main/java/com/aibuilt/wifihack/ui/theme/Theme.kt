package com.aibuilt.wifihack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = NeonGreen,
    onPrimary = Surface0,
    secondary = CyberBlue,
    error = DangerRed,
    background = Surface0,
    onBackground = OnDark,
    surface = Surface1,
    onSurface = OnDark,
    surfaceVariant = Surface1,
    tertiary = WarnAmber
)

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightSurface,
    secondary = LightSecondary,
    error = DangerRed,
    background = LightBg,
    onBackground = OnLight,
    surface = LightSurface,
    onSurface = OnLight,
    tertiary = WarnAmber
)

@Composable
fun WifiHackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
