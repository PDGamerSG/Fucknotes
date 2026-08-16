package com.example.fucknotes.ui.editor.ink

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.model.InkColors
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.PenPreset
import com.example.fucknotes.ui.common.ColorSwatchRow

/**
 * The colour and nib of one tray slot.
 *
 * There is no local copy of the width: the slot itself is the value being edited, so the slider
 * reads and writes it directly. A local mirror went stale the moment the slot changed from anywhere
 * else, and left the editor showing a nib the pen no longer had.
 */
@Composable
fun PenEditorPopup(
    pen: PenPreset,
    onDismiss: () -> Unit,
    onChange: (colorArgb: Int, widthDp: Float) -> Unit,
) {
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        Column(Modifier.padding(14.dp).width(250.dp)) {
            Text("Colour", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            InkColors.chunked(6).forEach { row ->
                ColorSwatchRow(
                    colors = row,
                    selectedArgb = pen.colorArgb,
                    onPick = { onChange(it, pen.widthDp) },
                    modifier = Modifier.padding(vertical = 4.dp),
                    swatchSize = 28.dp,
                )
            }

            Spacer(Modifier.height(10.dp))
            Text("Nib — ${"%.1f".format(pen.widthDp)} pt", style = MaterialTheme.typography.labelMedium)
            // Committed as it moves, not on release: the preview below and the next stroke on the
            // page both follow the slider, so the width is something the user can see.
            Slider(
                value = pen.widthDp,
                onValueChange = { onChange(pen.colorArgb, it) },
                valueRange = if (pen.tool == InkTool.HIGHLIGHTER) 8f..36f else 1f..16f,
            )
            NibPreview(pen)
        }
    }
}

/** The nib at its real width, on the paper tone it will be drawn on. */
@Composable
private fun NibPreview(pen: PenPreset) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(PaperWhite, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.85f)
                .height(pen.widthDp.dp)
                .background(
                    Color(pen.colorArgb).copy(alpha = if (pen.tool == InkTool.HIGHLIGHTER) 0.4f else 1f),
                    CircleShape,
                )
        )
    }
}
