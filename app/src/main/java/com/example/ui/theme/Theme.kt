package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val TrendLensDarkColorScheme = darkColorScheme(
    primary = EmeraldMint,
    onPrimary = ObsidianBg,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldMint,
    secondary = EmeraldBright,
    onSecondary = ObsidianBg,
    secondaryContainer = TerminalSurfaceElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = AmberWarning,
    onTertiary = ObsidianBg,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = AmberWarning,
    error = BearishRed,
    onError = TextPrimary,
    errorContainer = BearishContainer,
    onErrorContainer = BearishRed,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = TerminalSurface,
    onSurface = TextPrimary,
    surfaceVariant = TerminalSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = TerminalBorder,
    outlineVariant = TerminalBorderStrong
)

private val TrendLensShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun TrendLensTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TrendLensDarkColorScheme,
        typography = TrendLensTypography,
        shapes = TrendLensShapes,
        content = content
    )
}
