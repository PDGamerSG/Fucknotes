package com.example.fucknotes.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(paperColor)
            drawPaperPattern(style, lineColor, (spacingDp * zoom * density).coerceAtLeast(2.5f))
        }
    }
}

/** Rules, grid or dots stepping across the whole draw area. */
private fun DrawScope.drawPaperPattern(style: PaperStyle, color: Color, step: Float) {
    if (style == PaperStyle.PLAIN || step <= 1f) return
    val hairline = 1f
    if (style == PaperStyle.LINED || style == PaperStyle.GRID) {
        var y = step
        while (y < size.height) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = hairline)
            y += step
        }
    }
    if (style == PaperStyle.GRID) {
        var x = step
        while (x < size.width) {
            drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = hairline)
            x += step
        }
    }
    if (style == PaperStyle.DOTTED) {
        var y = step
        while (y < size.height) {
            var x = step
            while (x < size.width) {
                drawCircle(color, radius = 1.4f, center = Offset(x, y))
                x += step
            }
            y += step
        }
    }
}
