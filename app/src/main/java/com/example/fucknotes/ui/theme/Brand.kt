package com.example.fucknotes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Tokens the Material scheme has no slot for.
 *
 * The shell of this app is a tinted rail against a white sheet, with chrome that floats over a page
 * — none of which maps onto `surface` / `surfaceVariant` without one of them having to mean two
 * things at once. Keeping them here lets the rail stay lavender while the note sheet stays paper
 * white, in both themes and under every accent preset.
 */
@Immutable
data class BrandColors(
    /** Rail wash, top to bottom. */
    val railTop: Color,
    val railBottom: Color,
    /** The pill behind the selected folder row. */
    val railSelected: Color,
    val railText: Color,
    val railMuted: Color,
    /** Section headings and note titles — accent-tinted, not black. */
    val heading: Color,
    /** The sheet notes are laid out on. */
    val sheet: Color,
    /** A note thumbnail and its hairline. */
    val tile: Color,
    val tileBorder: Color,
    /** The desk a notebook's pages float on. */
    val desk: Color,
    /** Floating toolbar / menu fill and its hairline. */
    val chrome: Color,
    val chromeBorder: Color,
    /** The four tones the rail artwork is painted in, far to near. */
    val artFar: Color,
    val artMid: Color,
    val artNear: Color,
    val artInk: Color,
)

val LocalBrand = staticCompositionLocalOf { brandFor(Color(0xFF6C4CE0), dark = false) }

/** Reachable as `MaterialTheme.brand`, alongside `MaterialTheme.colorScheme`. */
val MaterialTheme.brand: BrandColors
    @Composable get() = LocalBrand.current

internal fun brandFor(seed: Color, dark: Boolean): BrandColors = if (dark) {
    BrandColors(
        railTop = seed.darken(0.72f).mix(Color(0xFF14121C), 0.55f),
        railBottom = seed.darken(0.60f).mix(Color(0xFF14121C), 0.35f),
        railSelected = seed.darken(0.30f).copy(alpha = 0.55f),
        railText = Color(0xFFEDEAF6),
        railMuted = Color(0xFF9A93B5),
        heading = seed.lighten(0.42f),
        sheet = Color(0xFF14121C),
        tile = Color(0xFF211D2E),
        tileBorder = Color(0xFF2E2941),
        desk = Color(0xFF15131D),
        chrome = Color(0xFF241F33),
        chromeBorder = Color(0x33FFFFFF),
        artFar = seed.darken(0.55f),
        artMid = seed.darken(0.38f),
        artNear = seed.darken(0.20f),
        artInk = seed.lighten(0.35f),
    )
} else {
    BrandColors(
        railTop = seed.lighten(0.92f),
        railBottom = seed.lighten(0.78f),
        railSelected = Color.White.copy(alpha = 0.82f),
        railText = seed.darken(0.52f),
        railMuted = seed.darken(0.16f).desaturate(0.30f),
        heading = seed.darken(0.28f),
        sheet = Color.White,
        tile = Color.White,
        tileBorder = seed.lighten(0.80f),
        desk = seed.lighten(0.93f).desaturate(0.40f),
        chrome = Color.White,
        chromeBorder = seed.lighten(0.84f),
        artFar = seed.lighten(0.62f),
        artMid = seed.lighten(0.44f),
        artNear = seed.lighten(0.24f),
        artInk = seed.darken(0.30f),
    )
}
