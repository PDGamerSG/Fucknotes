package com.example.fucknotes.ui.editor.ink

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.PenPreset

/** A consistent color swatch; selection stays readable on both pale and dark inks. */
@Composable
fun PenGlyph(
    pen: PenPreset,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Int = 42,
    highlighter: Boolean = false,
) {
    val color = Color(pen.colorArgb)
    Box(modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick).semantics {
        contentDescription = penLabel(pen)
        selected = active
    }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(30.dp).background(color, CircleShape)
            .border(if (active) 2.dp else 1.dp,
                if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center) {
            if (active) Icon(Icons.Filled.Check, null, Modifier.size(18.dp),
                tint = if (color.luminance() > 0.45f) Color(0xFF182332) else Color.White)
            else if (highlighter) Icon(
                Icons.Outlined.BorderColor,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (color.luminance() > 0.45f) Color(0xFF182332) else Color.White,
            )
        }
    }
}

fun penLabel(pen: PenPreset): String = when (pen.tool) {
    InkTool.PEN -> "Pen"
    InkTool.MARKER -> "Marker"
    InkTool.HIGHLIGHTER -> "Highlighter"
    InkTool.DASHED -> "Dashed pen"
    InkTool.ERASER -> "Eraser"
    InkTool.PAN -> "Pan"
} + ", #${(pen.colorArgb and 0xFFFFFF).toString(16).padStart(6, '0')}, ${"%.1f".format(pen.widthDp)} point nib"
