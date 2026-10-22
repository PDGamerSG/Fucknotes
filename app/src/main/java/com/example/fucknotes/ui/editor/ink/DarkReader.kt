package com.example.fucknotes.ui.editor.ink

import androidx.compose.ui.graphics.Color
import androidx.ink.brush.Brush
import androidx.ink.strokes.Stroke
import com.example.fucknotes.data.ink.InkStroke
import com.example.fucknotes.data.model.InkTool
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Night colours for pages, display-only.
 *
 * Nothing here touches what is stored or exported: the note keeps the colours it was written in,
 * and every reader — the page, its thumbnails, imported PDF and slide images, the attachment
 * strip — repaints through these rules when dark mode is on.
 *
 * The rules invert *lightness* and keep *hue*, so black ink reads as off-white, white paper as a
 * dark slate, and blue stays blue rather than turning orange the way a raw RGB inversion does.
 * Lightness is capped short of pure white and saturation eased a little, which is what keeps a
 * page of text from glaring in a dark room.
 */
object NightPalette {
    /** The sheet, a dark slate rather than black: enough depth for the chrome to float on. */
    val paper: Color = Color(0xFF181C24)

    /** Rules, grid and dots on the dark sheet. */
    val rule: Color = Color(0xFF4A5566)

    /** Brightest an inverted colour may become; pure white glares. */
    const val MAX_LIGHTNESS = 0.88f

    /** How much saturation is kept; a touch less reads softer on a dark ground. */
    const val SATURATION = 0.92f

    /** A highlight must stay light to read as a highlight over dark paper. */
    const val MIN_HIGHLIGHT_LIGHTNESS = 0.60f

    /**
     * A 4x5 colour matrix (Android layout, offsets in 0–255) for bitmaps: invert, then rotate hue
     * by a half turn so hues survive, then ease towards the slate so white paper lands on
     * [paper] and black text on a soft off-white instead of the extremes.
     */
    val imageMatrix: FloatArray = run {
        // Hue-preserving inversion: invert followed by a 180° hue rotation.
        val a = 0.333f
        val b = -0.667f
        // Ease: v' = SCALE * v + offset, with the offset tinted like the paper.
        val scale = 0.86f
        val offset = floatArrayOf(24f, 28f, 36f)
        floatArrayOf(
            a * scale, b * scale, b * scale, 0f, 255f * scale + offset[0],
            b * scale, a * scale, b * scale, 0f, 255f * scale + offset[1],
            b * scale, b * scale, a * scale, 0f, 255f * scale + offset[2],
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Applies [imageMatrix] to one pixel; pure Kotlin so the matrix can be checked on the JVM. */
    fun applyImageMatrix(argb: Int): Int {
        val m = imageMatrix
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val bl = argb and 0xFF
        fun row(i: Int) = (m[i * 5] * r + m[i * 5 + 1] * g + m[i * 5 + 2] * bl + m[i * 5 + 4])
            .toInt().coerceIn(0, 255)
        return (argb and 0xFF000000.toInt()) or (row(0) shl 16) or (row(1) shl 8) or row(2)
    }
}

/**
 * The night version of one ink colour.
 *
 * Lightness is inverted and capped, hue kept, saturation eased. A highlighter is the exception:
 * it is already a light, translucent wash and inverting it would leave a dark smear, so it keeps
 * its colour and is only lifted if it was dark.
 */
internal fun darkReaderArgb(argb: Int, tool: InkTool = InkTool.PEN): Int {
    val alpha = argb ushr 24
    val (h, s, l) = rgbToHsl(argb)
    val lightness = if (tool == InkTool.HIGHLIGHTER) max(l, NightPalette.MIN_HIGHLIGHT_LIGHTNESS)
    else min(NightPalette.MAX_LIGHTNESS, 1f - l)
    val rgb = hslToRgb(h, s * NightPalette.SATURATION, lightness)
    return (alpha shl 24) or rgb
}

/**
 * Wet ink is drawn in night colour so it matches the page while the pen is down, but what is
 * stored must be the colour the user chose. Each night brush remembers the daylight brush it was
 * made from, and a finished stroke is handed back in that brush before it reaches the note —
 * otherwise the saved colour was already inverted, the dry layer inverted it again, and the
 * stroke changed shade the moment the pen lifted.
 */
private val daylightBrushes: MutableMap<Brush, Brush> = Collections.synchronizedMap(WeakHashMap())

/** This brush in night colour, remembering [this] as the colour to store. */
internal fun Brush.forNight(tool: InkTool): Brush {
    val night = copyWithColorIntArgb(darkReaderArgb(colorIntArgb, tool))
    daylightBrushes[night] = this
    return night
}

/** A stroke drawn with a night brush, back in the brush it stands for; any other stroke as is. */
internal fun Stroke.daylight(): Stroke {
    val real = daylightBrushes[brush] ?: return this
    return Stroke(brush = real, inputs = inputs)
}

/**
 * Re-tessellating a stroke every frame is what made the old inversion stutter; the night copy is
 * built once per stroke and dropped with it.
 */
private val nightStrokes: MutableMap<Stroke, Stroke> = Collections.synchronizedMap(WeakHashMap())

internal fun InkStroke.forDarkReader(): Stroke = nightStrokes.getOrPut(stroke) {
    Stroke(
        brush = stroke.brush.copyWithColorIntArgb(darkReaderArgb(stroke.brush.colorIntArgb, tool)),
        inputs = stroke.inputs,
    )
}

// ---- colour maths, dependency-free so it runs in JVM tests ----------------

internal fun rgbToHsl(argb: Int): Triple<Float, Float, Float> {
    val r = ((argb ushr 16) and 0xFF) / 255f
    val g = ((argb ushr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val l = (maxC + minC) / 2f
    if (maxC == minC) return Triple(0f, 0f, l)
    val d = maxC - minC
    val s = if (l > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
    val h = when (maxC) {
        r -> ((g - b) / d + (if (g < b) 6f else 0f)) / 6f
        g -> ((b - r) / d + 2f) / 6f
        else -> ((r - g) / d + 4f) / 6f
    }
    return Triple(h, s, l)
}

internal fun hslToRgb(h: Float, s: Float, l: Float): Int {
    fun hue(p: Float, q: Float, t0: Float): Float {
        var t = t0
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    val r: Float; val g: Float; val b: Float
    if (s <= 0f) { r = l; g = l; b = l } else {
        val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
        val p = 2f * l - q
        r = hue(p, q, h + 1f / 3f); g = hue(p, q, h); b = hue(p, q, h - 1f / 3f)
    }
    fun channel(v: Float) = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
}

/** Hue distance on the colour wheel, for tests and tuning. */
internal fun hueDistance(a: Float, b: Float): Float {
    val d = abs(a - b)
    return min(d, 1f - d)
}
