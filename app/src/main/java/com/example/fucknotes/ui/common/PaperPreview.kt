package com.example.fucknotes.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.ui.theme.brand

/**
 * A sheet of paper, drawn small.
 *
 * The template chooser, the note tiles and the New Notebook preview all have to show the same four
 * papers, and a preview that guessed at its own spacing would promise a page the editor then did
 * not draw. This renders the real pattern and only shrinks the step, so picking "Dot at 5" looks
 * like the page it produces.
 *
 * The pattern is one draw call. A shelf of fifteen dotted tiles used to issue a circle per dot —
 * thousands of GPU commands a frame — and that, not the UI thread, was what made the shelf scroll
 * at 20 ms. The point list is computed once per size and replayed as a single `drawPoints`.
 */
@Composable
fun PaperPreview(
    style: PaperStyle,
    modifier: Modifier = Modifier,
    paperColor: Color = Color.White,
    lineColor: Color = Color(0xFFD3D6DE),
    /** Rule spacing in points, as stored on the note. */
    spacingDp: Float = 24f,
    /** How much of full size the preview runs at. */
    zoom: Float = 0.30f,
    corner: Dp = 8.dp,
    border: Boolean = true,
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .clip(shape)
            .then(
                if (border) Modifier.border(1.dp, MaterialTheme.brand.tileBorder, shape)
                else Modifier
            )
            .fillMaxSize()
            .drawWithCache {
                val step = (spacingDp * zoom * density).coerceAtLeast(2.5f)
                val pattern = paperPatternPoints(style, step, size.width, size.height)
                onDrawBehind {
                    drawRect(paperColor)
                    pattern?.let { (mode, points, width) ->
                        drawPoints(points, mode, lineColor, strokeWidth = width, cap = StrokeCap.Round)
                    }
                }
            }
    )
}

/** The pattern as one point list: line end-pairs for rules and grids, centres for dots. */
internal fun paperPatternPoints(
    style: PaperStyle,
    step: Float,
    width: Float,
    height: Float,
): Triple<PointMode, List<Offset>, Float>? {
    if (style == PaperStyle.PLAIN || step <= 1f) return null
    val points = ArrayList<Offset>()
    when (style) {
        PaperStyle.LINED, PaperStyle.GRID -> {
            var y = step
            while (y < height) { points += Offset(0f, y); points += Offset(width, y); y += step }
            if (style == PaperStyle.GRID) {
                var x = step
                while (x < width) { points += Offset(x, 0f); points += Offset(x, height); x += step }
            }
            return Triple(PointMode.Lines, points, 1.25f)
        }
        PaperStyle.DOTTED -> {
            var y = step
            while (y < height) {
                var x = step
                while (x < width) { points += Offset(x, y); x += step }
                y += step
            }
            return Triple(PointMode.Points, points, 3.8f)
        }
        PaperStyle.PLAIN -> return null
    }
}
