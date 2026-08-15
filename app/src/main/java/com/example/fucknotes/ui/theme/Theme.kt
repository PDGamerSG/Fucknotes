package com.example.fucknotes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.fucknotes.data.model.AccentPreset
import com.example.fucknotes.data.model.ThemeMode

/**
 * Builds a scheme from an accent seed without pulling in a palette generator: the seed drives
 * primary, and secondary/tertiary are hue-neighbours derived by blending it toward the surface.
 */
private fun schemeFor(seed: Color, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = seed.lighten(0.28f),
        onPrimary = Color(0xFF17181F),
        primaryContainer = seed.darken(0.45f),
        onPrimaryContainer = seed.lighten(0.55f),
        secondary = seed.lighten(0.1f).desaturate(0.35f),
        onSecondary = Color(0xFF17181F),
        secondaryContainer = seed.darken(0.55f).desaturate(0.3f),
        onSecondaryContainer = seed.lighten(0.6f),
        tertiary = seed.rotateWarm().lighten(0.2f),
        background = Color(0xFF101116),
        onBackground = Color(0xFFE6E6EC),
        surface = Color(0xFF101116),
        onSurface = Color(0xFFE6E6EC),
        surfaceVariant = Color(0xFF24252E),
        onSurfaceVariant = Color(0xFFBFC0CB),
        outline = Color(0xFF4A4B57),
        outlineVariant = Color(0xFF2C2D37),
        error = Color(0xFFFFB4AB),
    )
} else {
    lightColorScheme(
        primary = seed.darken(0.08f),
        onPrimary = Color.White,
        primaryContainer = seed.lighten(0.72f),
        onPrimaryContainer = seed.darken(0.55f),
        secondary = seed.darken(0.1f).desaturate(0.3f),
        onSecondary = Color.White,
        secondaryContainer = seed.lighten(0.8f).desaturate(0.2f),
        onSecondaryContainer = seed.darken(0.55f),
        tertiary = seed.rotateWarm().darken(0.1f),
        background = Color(0xFFFBFAFD),
        onBackground = Color(0xFF1A1B21),
        surface = Color(0xFFFBFAFD),
        onSurface = Color(0xFF1A1B21),
        surfaceVariant = Color(0xFFEDECF2),
        onSurfaceVariant = Color(0xFF4A4B55),
        outline = Color(0xFFB9B9C4),
        outlineVariant = Color(0xFFDEDEE6),
    )
}

private fun Color.lighten(amount: Float) = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha,
)

private fun Color.darken(amount: Float) = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = alpha,
)

private fun Color.desaturate(amount: Float): Color {
    val grey = luminance()
    return Color(
        red = red + (grey - red) * amount,
        green = green + (grey - green) * amount,
        blue = blue + (grey - blue) * amount,
        alpha = alpha,
    )
}

/** Nudges the seed toward amber so tertiary reads as a companion rather than a clash. */
private fun Color.rotateWarm() = Color(
    red = (red * 0.6f + 0.4f).coerceIn(0f, 1f),
    green = (green * 0.7f + 0.2f).coerceIn(0f, 1f),
    blue = (blue * 0.5f).coerceIn(0f, 1f),
    alpha = alpha,
)

private fun typography(scalePercent: Int): Typography {
    val s = scalePercent / 100f
    return Typography(
        headlineSmall = TextStyle(fontSize = 24.sp * s, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp * s),
        titleLarge = TextStyle(fontSize = 22.sp * s, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp * s),
        titleMedium = TextStyle(fontSize = 17.sp * s, fontWeight = FontWeight.Medium, lineHeight = 24.sp * s),
        titleSmall = TextStyle(fontSize = 14.sp * s, fontWeight = FontWeight.Medium, lineHeight = 20.sp * s),
        bodyLarge = TextStyle(fontSize = 16.sp * s, lineHeight = 24.sp * s),
        bodyMedium = TextStyle(fontSize = 14.sp * s, lineHeight = 20.sp * s),
        bodySmall = TextStyle(fontSize = 12.sp * s, lineHeight = 16.sp * s),
        labelLarge = TextStyle(fontSize = 14.sp * s, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontSize = 12.sp * s, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 11.sp * s, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun FucknotesTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentPreset = AccentPreset.INDIGO,
    dynamicColor: Boolean = true,
    fontScalePercent: Int = 100,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        else -> schemeFor(Color(accent.seedArgb), dark)
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = typography(fontScalePercent),
        content = content,
    )
}
