package com.example.fucknotes.ui.editor.ink

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.PenPreset

/**
 * One pen, drawn as a pen.
 *
 * A tray of coloured dots tells you the ink but not the instrument, and this app has four
 * instruments that behave differently — a nib that answers to pressure, a flat felt tip, a chisel
 * highlighter, a dashed liner. Each gets its own silhouette, so the tray is read at a glance and
 * the selected pen is the one standing proud of the row.
 */
@Composable
fun PenGlyph(
    pen: PenPreset,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Int = 42,
) {
    val lift by animateDpAsState(if (active) (-7).dp else 0.dp, label = "pen-lift")
    val body = Color(pen.colorArgb)
    val scale = height / 42f

    val nibWidth = when (pen.tool) {
        InkTool.HIGHLIGHTER -> 15.dp
        InkTool.MARKER -> 9.dp
        else -> 6.dp
    } * scale
    val nibHeight = when (pen.tool) {
        InkTool.HIGHLIGHTER -> 7.dp
        InkTool.MARKER -> 9.dp
        else -> 11.dp
    } * scale
    val barrelWidth = when (pen.tool) {
        InkTool.HIGHLIGHTER -> 19.dp
        else -> 15.dp
    } * scale

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(barrelWidth + 6.dp)
            .offset(y = lift)
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = penLabel(pen) },
    ) {
        // Nib.
        Box(
            Modifier
                .width(nibWidth)
                .height(nibHeight)
                .background(
                    body,
                    RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 1.dp, bottomEnd = 1.dp),
                )
        )
        // Ferrule — a pale collar, so the nib reads as a separate part rather than as a tapering bar.
        Box(
            Modifier
                .width(barrelWidth)
                .height(4.dp * scale)
                .background(Color.White.copy(alpha = 0.9f), RoundedCornerShape(1.dp))
        )
        // Barrel.
        Box(
            Modifier
                .width(barrelWidth)
                .height(24.dp * scale)
                .background(
                    body.copy(alpha = if (pen.tool == InkTool.HIGHLIGHTER) 0.45f else 0.88f),
                    RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                )
                .padding(top = 3.dp * scale),
        ) {
            // A dashed pen carries its dashes on the barrel, so the tray shows what it draws.
            if (pen.tool == InkTool.DASHED) {
                Column(
                    Modifier.width(barrelWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    repeat(3) {
                        Box(
                            Modifier
                                .padding(top = 3.dp * scale)
                                .width(barrelWidth * 0.5f)
                                .height(2.dp)
                                .background(Color.White.copy(alpha = 0.75f))
                        )
                    }
                }
            }
        }
    }
}

/** What a tray slot is called out loud. */
fun penLabel(pen: PenPreset): String = when (pen.tool) {
    InkTool.PEN -> "Pen"
    InkTool.MARKER -> "Marker"
    InkTool.HIGHLIGHTER -> "Highlighter"
    InkTool.DASHED -> "Dashed pen"
    InkTool.ERASER -> "Eraser"
    InkTool.PAN -> "Pan"
} + ", ${"%.1f".format(pen.widthDp)} point nib"
