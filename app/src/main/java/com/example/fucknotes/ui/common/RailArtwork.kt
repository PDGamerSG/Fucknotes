package com.example.fucknotes.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.fucknotes.ui.theme.brand
import androidx.compose.material3.MaterialTheme

/**
 * The scene at the foot of the folder rail.
 *
 * This is the one piece of the app that is decoration rather than instrumentation, and it earns its
 * place by giving the rail a bottom: folder lists end raggedly wherever the last folder happens to
 * fall, and a horizon under them turns that into a deliberate edge. It is drawn in the accent's own
 * tones, so it re-tints with the theme instead of being a fixed picture pasted onto every palette.
 */
@Composable
fun RailArtwork(modifier: Modifier = Modifier) {
    val brand = MaterialTheme.brand
    Canvas(modifier) {
        val w = size.width
        val h = size.height

        drawStars(brand.artInk.copy(alpha = 0.30f), w, h)
        drawCircle(brand.artInk.copy(alpha = 0.16f), radius = h * 0.10f, center = Offset(w * 0.76f, h * 0.20f))

        drawRange(
            color = brand.artFar.copy(alpha = 0.85f),
            baseline = h * 0.72f,
            peaks = listOf(0.06f to 0.34f, 0.34f to 0.16f, 0.66f to 0.38f, 0.95f to 0.26f),
            width = w,
            height = h,
        )
        drawRange(
            color = brand.artMid,
            baseline = h * 0.82f,
            peaks = listOf(0.02f to 0.52f, 0.26f to 0.34f, 0.58f to 0.50f, 0.88f to 0.40f),
            width = w,
            height = h,
        )
        // Snow on the second range's tallest peak: two short strokes, not a white cap, so it reads
        // at 20 dp tall without turning into a blob.
        drawSnow(Color.White.copy(alpha = 0.55f), w * 0.26f, h * 0.34f, w * 0.055f)

        drawRange(
            color = brand.artNear,
            baseline = h + 1f,
            peaks = listOf(0.0f to 0.78f, 0.40f to 0.66f, 0.75f to 0.74f, 1.0f to 0.70f),
            width = w,
            height = h,
        )

        drawSignpost(brand.artInk, w * 0.70f, h * 0.62f, h)
        drawCat(brand.artInk, w * 0.30f, h * 0.90f, h * 0.11f)
    }
}

/** A ridge line: peaks at ([x], [y]) fractions, filled down to [baseline]. */
private fun DrawScope.drawRange(
    color: Color,
    baseline: Float,
    peaks: List<Pair<Float, Float>>,
    width: Float,
    height: Float,
) {
    val path = Path().apply {
        moveTo(0f, baseline)
        peaks.forEach { (x, y) -> lineTo(width * x, height * y) }
        lineTo(width, baseline)
        lineTo(width, height + 2f)
        lineTo(0f, height + 2f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawSnow(color: Color, peakX: Float, peakY: Float, spread: Float) {
    val path = Path().apply {
        moveTo(peakX - spread, peakY + spread * 0.9f)
        lineTo(peakX, peakY)
        lineTo(peakX + spread, peakY + spread * 0.9f)
        lineTo(peakX + spread * 0.35f, peakY + spread * 0.5f)
        lineTo(peakX, peakY + spread * 0.85f)
        lineTo(peakX - spread * 0.4f, peakY + spread * 0.45f)
        close()
    }
    drawPath(path, color)
}

/** A bus-stop sign: the only man-made thing on the horizon, so the scene reads as a place. */
private fun DrawScope.drawSignpost(color: Color, x: Float, top: Float, height: Float) {
    val postWidth = height * 0.022f
    drawRect(
        color = color.copy(alpha = 0.85f),
        topLeft = Offset(x - postWidth / 2f, top),
        size = Size(postWidth, height - top),
    )
    val boardWidth = height * 0.30f
    val boardHeight = height * 0.17f
    drawRoundRect(
        color = color,
        topLeft = Offset(x - boardWidth / 2f, top - boardHeight * 0.75f),
        size = Size(boardWidth, boardHeight),
        cornerRadius = CornerRadius(boardHeight * 0.28f),
    )
}

/**
 * A cat, sitting.
 *
 * Five primitives: a haunch, a head, two ears and a tail. Any more detail and it stops reading as a
 * silhouette at this size.
 */
private fun DrawScope.drawCat(color: Color, x: Float, baseline: Float, scale: Float) {
    val bodyHeight = scale
    val bodyWidth = scale * 0.85f

    // Haunch — a teardrop, flat on the ground.
    val body = Path().apply {
        moveTo(x - bodyWidth / 2f, baseline)
        cubicTo(
            x - bodyWidth / 2f, baseline - bodyHeight * 0.8f,
            x + bodyWidth / 2f, baseline - bodyHeight * 0.9f,
            x + bodyWidth / 2f, baseline,
        )
        close()
    }
    drawPath(body, color)

    val headRadius = scale * 0.34f
    val headY = baseline - bodyHeight * 0.78f
    drawCircle(color, radius = headRadius, center = Offset(x, headY))

    listOf(-1f, 1f).forEach { side ->
        val ear = Path().apply {
            moveTo(x + side * headRadius * 0.85f, headY - headRadius * 0.45f)
            lineTo(x + side * headRadius * 0.55f, headY - headRadius * 1.45f)
            lineTo(x + side * headRadius * 0.15f, headY - headRadius * 0.80f)
            close()
        }
        drawPath(ear, color)
    }

    val tail = Path().apply {
        moveTo(x + bodyWidth * 0.45f, baseline - bodyHeight * 0.12f)
        cubicTo(
            x + bodyWidth * 1.10f, baseline - bodyHeight * 0.05f,
            x + bodyWidth * 1.05f, baseline - bodyHeight * 0.75f,
            x + bodyWidth * 0.72f, baseline - bodyHeight * 0.70f,
        )
    }
    drawPath(tail, color, style = Stroke(width = scale * 0.16f))
}

private fun DrawScope.drawStars(color: Color, width: Float, height: Float) {
    val positions = listOf(
        0.12f to 0.16f, 0.30f to 0.09f, 0.52f to 0.20f,
        0.62f to 0.06f, 0.88f to 0.13f, 0.94f to 0.30f,
    )
    positions.forEach { (x, y) ->
        drawCircle(color, radius = height * 0.012f, center = Offset(width * x, height * y))
    }
}
