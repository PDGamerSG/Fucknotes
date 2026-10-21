package com.example.fucknotes.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.model.PaperStyle

/** Draws ruled / grid / dotted paper behind whatever content is passed in. */
@Composable
fun PaperBackground(
    style: PaperStyle,
    modifier: Modifier = Modifier,
    spacing: Dp = 28.dp,
    lineColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.drawBehind {
            if (style == PaperStyle.PLAIN) return@drawBehind
            val step = spacing.toPx()
            if (step <= 0f) return@drawBehind

            when (style) {
                PaperStyle.LINED -> {
                    var y = step
                    while (y < size.height) {
                        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.25f)
                        y += step
                    }
                }

                PaperStyle.GRID -> {
                    var y = step
                    while (y < size.height) {
                        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.25f)
                        y += step
                    }
                    var x = step
                    while (x < size.width) {
                        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.25f)
                        x += step
                    }
                }

                PaperStyle.DOTTED -> {
                    var y = step
                    while (y < size.height) {
                        var x = step
                        while (x < size.width) {
                            drawCircle(lineColor, radius = 2f, center = Offset(x, y))
                            x += step
                        }
                        y += step
                    }
                }

                PaperStyle.PLAIN -> Unit
            }
        },
        content = { content() },
    )
}
