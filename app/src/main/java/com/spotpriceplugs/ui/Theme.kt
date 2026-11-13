package com.spotpriceplugs.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** App palette: dark; blue = cheap, red = expensive. */
object PlugPalette {
    val Background = Color(0xFF0F1319)
    val Surface = Color(0xFF171D26)
    val Inner = Color(0xFF121821)
    val Primary = Color(0xFF3EA6FF)
    val OnPrimary = Color(0xFF04121F)
    val Text = Color(0xFFE8EEF7)
    val Muted = Color(0xFF8FA0B5)
    val Outline = Color(0xFF243040)
    val Error = Color(0xFFFF6B6B)
    val Good = Color(0xFF35D07F)
    val Middle = Color(0xFF5A6B80)

    /** Buy Me a Coffee brand yellow, so the support button is instantly recognisable. */
    val Coffee = Color(0xFFFFDD00)
    val OnCoffee = Color(0xFF0D0C22)
}

private val PlugColors = darkColorScheme(
    primary = PlugPalette.Primary,
    onPrimary = PlugPalette.OnPrimary,
    background = PlugPalette.Background,
    onBackground = PlugPalette.Text,
    surface = PlugPalette.Surface,
    onSurface = PlugPalette.Text,
    surfaceVariant = PlugPalette.Outline,
    onSurfaceVariant = PlugPalette.Muted,
    outline = PlugPalette.Outline,
    error = PlugPalette.Error
)

/**
 * Sizes are in `sp`, so Android scales them with the system font size setting
 * (non-linearly on Android 14+, so large text does not blow up). Base sizes are
 * slightly above Material 3 defaults: the app shows many small numbers that
 * should be readable at a glance.
 */
private val PlugTypography = Typography(
    headlineSmall = TextStyle(fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(
        fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.5.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
)

@Composable
fun PlugTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = PlugColors, typography = PlugTypography, content = content)
}
