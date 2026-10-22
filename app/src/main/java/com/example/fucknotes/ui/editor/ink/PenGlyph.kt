package com.example.fucknotes.ui.editor.ink

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import com.example.fucknotes.ui.theme.brand
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
    /** Show the pen as it will write on a night page, so a black pen reads as the off-white it draws. */
    darkReader: Boolean = false,
) {
    val color = Color(if (darkReader) darkReaderArgb(pen.colorArgb, pen.tool) else pen.colorArgb)
    val scheme = MaterialTheme.colorScheme
    Box(modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics {
        contentDescription = penLabel(pen)
        selected = active
    }, contentAlignment = Alignment.Center) {
        // The ring sits outside a disc that never changes size, so selecting a pen adds a halo
        // instead of eating into the colour; the ring's inner edge is one hairline of bar tone
        // so a dark pen still reads against a light ring and a light pen against the bar.
        if (active) Box(
            Modifier
                .size(38.dp)
                .border(2.dp, scheme.onSurface, CircleShape)
                .padding(2.dp)
                .border(2.dp, MaterialTheme.brand.chrome, CircleShape),
        )
        Box(Modifier.size(30.dp).background(color, CircleShape)
            .border(1.dp, if (active) Color.Transparent else scheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center) {
            if (active) Icon(Icons.Filled.Check, null, Modifier.size(16.dp),
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
    InkTool.LASSO -> "Select"
} + ", #${(pen.colorArgb and 0xFFFFFF).toString(16).padStart(6, '0')}, ${"%.1f".format(pen.widthDp)} point nib"
