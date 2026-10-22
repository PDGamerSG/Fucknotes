package com.example.fucknotes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
