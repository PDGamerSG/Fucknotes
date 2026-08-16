package com.example.fucknotes.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Builds a Material scheme from an accent seed without pulling in a palette generator.
 *
 * The neutrals are not grey: they each carry a trace of the seed, so the shell reads as one tinted
 * material rather than as a coloured button dropped onto a grey app.
 */
internal fun schemeFor(seed: Color, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = seed.lighten(0.34f),
        onPrimary = Color(0xFF16121F),
        primaryContainer = seed.darken(0.45f),
        onPrimaryContainer = seed.lighten(0.62f),
        secondary = seed.lighten(0.12f).desaturate(0.35f),
        onSecondary = Color(0xFF16121F),
        secondaryContainer = seed.darken(0.58f).desaturate(0.25f),
        onSecondaryContainer = seed.lighten(0.66f),
        tertiary = seed.rotateWarm().lighten(0.20f),
        background = Color(0xFF14121C),
        onBackground = Color(0xFFEDEAF6),
        surface = Color(0xFF14121C),
        onSurface = Color(0xFFEDEAF6),
        surfaceVariant = Color(0xFF262032),
        onSurfaceVariant = Color(0xFFB6AECC),
        outline = Color(0xFF4C4460),
        outlineVariant = Color(0xFF2E2841),
        error = Color(0xFFFFB4AB),
    )
} else {
    lightColorScheme(
        primary = seed.darken(0.04f),
        onPrimary = Color.White,
        primaryContainer = seed.lighten(0.80f),
        onPrimaryContainer = seed.darken(0.52f),
        secondary = seed.darken(0.10f).desaturate(0.25f),
        onSecondary = Color.White,
        secondaryContainer = seed.lighten(0.86f).desaturate(0.15f),
        onSecondaryContainer = seed.darken(0.52f),
        tertiary = seed.rotateWarm().darken(0.10f),
        background = Color.White,
        onBackground = seed.darken(0.66f).desaturate(0.55f),
        surface = Color.White,
        onSurface = seed.darken(0.66f).desaturate(0.55f),
        surfaceVariant = seed.lighten(0.90f),
        onSurfaceVariant = seed.darken(0.26f).desaturate(0.45f),
        outline = seed.lighten(0.62f),
        outlineVariant = seed.lighten(0.84f),
    )
}
