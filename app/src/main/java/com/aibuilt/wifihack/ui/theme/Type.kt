package com.aibuilt.wifihack.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

private val Mono = FontFamily.Monospace

val TerminalTypography = Typography(
    titleLarge = TextStyle(fontFamily = Mono, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Mono, fontSize = 18.sp),
    titleSmall = TextStyle(fontFamily = Mono, fontSize = 15.sp),
    bodyLarge = TextStyle(fontFamily = Mono, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = Mono, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = Mono, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = Mono, fontSize = 14.sp),
    labelSmall = TextStyle(fontFamily = Mono, fontSize = 11.sp)
)
