package com.example.fucknotes.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One family, worked hard.
 *
 * A writing app has no room for a display face — the page is the content — so the personality comes
 * from the scale instead: headings sit heavy and tight, rows sit at a readable 15/22, and labels
 * open up a little so a folder count or a zoom percentage reads as instrumentation.
 *
 * [scalePercent] is the user's own type-size setting, applied to every role at once.
 */
internal fun typography(scalePercent: Int): Typography {
    val s = scalePercent / 100f
    val sans = FontFamily.SansSerif
    return Typography(
        displaySmall = TextStyle(
            fontFamily = sans,
            fontSize = 30.sp * s,
            fontWeight = FontWeight.Bold,
            lineHeight = 36.sp * s,
            letterSpacing = (-0.6).sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = sans,
            fontSize = 24.sp * s,
            fontWeight = FontWeight.Bold,
            lineHeight = 30.sp * s,
            letterSpacing = (-0.4).sp,
        ),
        titleLarge = TextStyle(
            fontFamily = sans,
            fontSize = 20.sp * s,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 26.sp * s,
            letterSpacing = (-0.2).sp,
        ),
        titleMedium = TextStyle(
            fontFamily = sans,
            fontSize = 16.sp * s,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 22.sp * s,
        ),
        titleSmall = TextStyle(
            fontFamily = sans,
            fontSize = 14.sp * s,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 20.sp * s,
        ),
        bodyLarge = TextStyle(fontFamily = sans, fontSize = 15.sp * s, lineHeight = 22.sp * s),
        bodyMedium = TextStyle(fontFamily = sans, fontSize = 14.sp * s, lineHeight = 20.sp * s),
        bodySmall = TextStyle(fontFamily = sans, fontSize = 12.sp * s, lineHeight = 16.sp * s),
        labelLarge = TextStyle(fontFamily = sans, fontSize = 14.sp * s, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(
            fontFamily = sans,
            fontSize = 12.sp * s,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.2.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = sans,
            fontSize = 11.sp * s,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
        ),
    )
}

/** Corner radii, in one place. The shell is soft; the page it frames is not. */
object Radii {
    val pill = 999.dp
    val sheet = 22.dp
    val card = 16.dp
    val row = 14.dp
    val chip = 10.dp
}
