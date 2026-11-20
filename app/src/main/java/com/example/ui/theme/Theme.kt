package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HudColorScheme = darkColorScheme(
    primary = OpticNeonLime,
    onPrimary = CarbonBlack,
    primaryContainer = OpticNeonLime,
    onPrimaryContainer = CarbonBlack,
    secondary = CyberCyan,
    onSecondary = CarbonBlack,
    secondaryContainer = HudContainer,
    onSecondaryContainer = CyberCyan,
    tertiary = FaultCrimson,
    onTertiary = TextPrimary,
    background = CarbonBlack,
    onBackground = TextSecondary,
    surface = SurfaceDark,
    onSurface = TextSecondary,
    surfaceVariant = HudContainer,
    onSurfaceVariant = TextMuted,
    outline = OutlineMuted,
    outlineVariant = OutlineMuted,
    error = FaultCrimson,
    onError = TextPrimary
)

@Composable
fun AceAiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HudColorScheme,
        typography = Typography,
        content = content
    )
}
