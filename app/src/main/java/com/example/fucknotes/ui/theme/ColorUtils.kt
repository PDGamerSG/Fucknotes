package com.example.fucknotes.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Channel maths shared by the scheme and the brand tokens.
 *
 * Everything the shell wears is derived from one accent seed, so these four operations are the
 * whole palette generator: a preset only has to supply a colour, not a set of twenty.
 */

internal fun Color.lighten(amount: Float) = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha,
)

internal fun Color.darken(amount: Float) = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = alpha,
)

/** Moves [amount] of the way toward [other]. */
internal fun Color.mix(other: Color, amount: Float) = Color(
    red = red + (other.red - red) * amount,
    green = green + (other.green - green) * amount,
    blue = blue + (other.blue - blue) * amount,
    alpha = alpha,
)

internal fun Color.desaturate(amount: Float): Color {
    val grey = luminance()
    return Color(
        red = red + (grey - red) * amount,
        green = green + (grey - green) * amount,
        blue = blue + (grey - blue) * amount,
        alpha = alpha,
    )
}

/** Nudges the seed toward amber so a tertiary reads as a companion rather than a clash. */
internal fun Color.rotateWarm() = Color(
    red = (red * 0.6f + 0.4f).coerceIn(0f, 1f),
    green = (green * 0.7f + 0.2f).coerceIn(0f, 1f),
    blue = (blue * 0.5f).coerceIn(0f, 1f),
    alpha = alpha,
)
