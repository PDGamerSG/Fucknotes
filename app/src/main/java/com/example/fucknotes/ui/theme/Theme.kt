package com.example.fucknotes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.fucknotes.data.model.AccentPreset
import com.example.fucknotes.data.model.ThemeMode

/**
 * The app's theme.
 *
 * Colour lives in [schemeFor] and [brandFor], type in [typography]; this only picks the mode and
 * publishes both. Everything is derived from a single accent seed, so a preset change repaints the
 * rail, the artwork and the buttons together.
 */
@Composable
fun FucknotesTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentPreset = AccentPreset.INDIGO,
    dynamicColor: Boolean = false,
    darkReader: Boolean = false,
    fontScalePercent: Int = 100,
    content: @Composable () -> Unit,
) {
    val spec = ThemeSpec(themeMode, accent, dynamicColor, darkReader, fontScalePercent)
    CompositionLocalProvider(LocalThemeSpec provides spec) {
        ThemedContent(spec, content)
    }
}

/** The choices the theme was built from, kept so a screen can rebuild it with one of them changed. */
data class ThemeSpec(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentPreset = AccentPreset.INDIGO,
    val dynamicColor: Boolean = false,
    val darkReader: Boolean = false,
    val fontScalePercent: Int = 100,
)

val LocalThemeSpec = staticCompositionLocalOf { ThemeSpec() }

/**
 * The app theme with the night flag taken from one note instead of the app-wide setting.
 *
 * Wrapped around the editor, so its bars and sheets sit on the same dark or light ground as the
 * page being read. Everything else about the theme — accent, type size, dynamic colour — is kept.
 */
@Composable
fun PageTheme(dark: Boolean, content: @Composable () -> Unit) {
    val spec = LocalThemeSpec.current.copy(darkReader = dark)
    // Always the same tree, whether or not the flag differs from the app's: switching between a
    // bare `content()` and a wrapped one would recreate the editor and drop its open sheets, its
    // camera and its chrome state the moment the note changed mode.
    CompositionLocalProvider(LocalThemeSpec provides spec) { ThemedContent(spec, content) }
}

@Composable
private fun ThemedContent(spec: ThemeSpec, content: @Composable () -> Unit) {
    val themeMode = spec.themeMode
    val accent = spec.accent
    val dynamicColor = spec.dynamicColor
    val darkReader = spec.darkReader
    val fontScalePercent = spec.fontScalePercent
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    } || darkReader
    val context = LocalContext.current
    val seed = Color(accent.seedArgb)
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        else -> schemeFor(seed, dark)
    }
    // Under dynamic colour the brand tones follow the wallpaper's primary, so the rail and the
    // artwork stay in the same family as the buttons rather than holding the app's own violet.
    val brand = brandFor(if (dynamicColor) scheme.primary else seed, dark)

    CompositionLocalProvider(LocalBrand provides brand) {
        MaterialTheme(
            colorScheme = scheme,
            typography = typography(fontScalePercent),
            content = content,
        )
    }
}
