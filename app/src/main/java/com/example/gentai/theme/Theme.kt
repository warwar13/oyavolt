package com.warwar13.gentai.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GentaiGreenDark,
    onPrimary = Color(0xFF003828),
    primaryContainer = GentaiGreenContainerDark,
    onPrimaryContainer = Color(0xFF82F4C3),
    secondary = GentaiBlueDark,
    secondaryContainer = GentaiBlueContainerDark,
    tertiary = GentaiOrangeDark,
    background = GentaiDarkBackground,
    onBackground = Color(0xFFF1F7ED),
    surface = GentaiDarkSurface,
    onSurface = Color(0xFFF1F7ED),
    surfaceVariant = Color(0xFF202820),
    onSurfaceVariant = Color(0xFFADB8A9),
    outline = Color(0xFF667063),
    error = Color(0xFFFF6B6B),
)

@Composable
fun GentaiTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = DarkColorScheme, typography = Typography, content = content)
}
