package com.example.fucknotes.ui.editor.ink

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.InkColors
import com.example.fucknotes.data.model.PenPreset

/**
 * The pen tray.
 *
 * Each slot keeps its own colour and nib width, so switching pens restores exactly the nib that
 * slot was last set to — the behaviour of a real pen case, and of the app this is modelled on.
 * Tapping the already-selected pen opens its colour and width editor.
 */
@Composable
fun InkToolbar(
    pens: List<PenPreset>,
    selectedPen: Int,
    tool: InkTool,
    zoom: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    fingerDrawing: Boolean,
    onToggleFingerDrawing: () -> Unit,
    onSelectPen: (Int) -> Unit,
    onEditPen: (index: Int, colorArgb: Int, widthDp: Float) -> Unit,
    onSelectTool: (InkTool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingPen by remember { mutableIntStateOf(-1) }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "Redo")
            }

            ToolToggle(
                selected = tool == InkTool.ERASER,
                icon = Icons.Outlined.AutoFixNormal,
                label = "Eraser",
                onClick = { onSelectTool(InkTool.ERASER) },
            )
            ToolToggle(
                selected = tool == InkTool.PAN,
                icon = Icons.Outlined.PanTool,
                label = "Pan",
                onClick = { onSelectTool(InkTool.PAN) },
            )
            // Stylus-only mode. Off, a finger scrolls the page instead of drawing on it, so a
            // hand can rest on the glass while the pen writes.
            ToolToggle(
                selected = fingerDrawing,
                icon = if (fingerDrawing) Icons.Outlined.TouchApp else Icons.Outlined.Draw,
                label = if (fingerDrawing) "Finger drawing on" else "Stylus only",
                onClick = onToggleFingerDrawing,
            )

            Spacer(Modifier.width(4.dp))

            LazyRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                itemsIndexed(pens) { index, pen ->
                    Box {
                        PenSlot(
                            pen = pen,
                            active = tool.isBrush && index == selectedPen,
                            onClick = {
                                if (tool.isBrush && index == selectedPen) editingPen = index
                                else onSelectPen(index)
                            },
                        )
                        if (editingPen == index) {
                            PenEditor(
                                pen = pen,
                                onDismiss = { editingPen = -1 },
                                onChange = { color, width -> onEditPen(index, color, width) },
                            )
                        }
                    }
                }
            }

            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onResetView)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.CenterFocusStrong,
                        contentDescription = "Reset view",
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${(zoom * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ToolToggle(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One pen drawn as a pen: a coloured body with a nib, lifted when it is the active tool. Nib width
 * is reflected in the shape so the tray reads at a glance.
 */
@Composable
private fun PenSlot(pen: PenPreset, active: Boolean, onClick: () -> Unit) {
    val body = Color(pen.colorArgb)
    val nibHeight = when (pen.tool) {
        InkTool.HIGHLIGHTER -> 8.dp
        InkTool.MARKER -> 10.dp
        else -> 14.dp
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(26.dp)
            .offset(y = if (active) (-6).dp else 0.dp)
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
    ) {
        // Nib.
        Box(
            Modifier
                .width(if (pen.tool == InkTool.HIGHLIGHTER) 16.dp else 8.dp)
                .height(nibHeight)
                .background(
                    body,
                    RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp),
                )
        )
        // Barrel.
        Box(
            Modifier
                .width(18.dp)
                .height(30.dp)
                .background(
                    body.copy(alpha = if (pen.tool == InkTool.HIGHLIGHTER) 0.45f else 0.85f),
                    RoundedCornerShape(4.dp),
                )
        )
    }
}

@Composable
private fun PenEditor(
    pen: PenPreset,
    onDismiss: () -> Unit,
    onChange: (colorArgb: Int, widthDp: Float) -> Unit,
) {
    var width by remember { mutableStateOf(pen.widthDp) }

    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        Column(Modifier.padding(12.dp).width(240.dp)) {
            Text("Colour", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            InkColors.chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { argb ->
                        Box(
                            Modifier
                                .padding(vertical = 4.dp)
                                .size(28.dp)
                                .background(Color(argb), CircleShape)
                                .border(
                                    width = if (argb == pen.colorArgb) 3.dp else 1.dp,
                                    color = if (argb == pen.colorArgb) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape,
                                )
                                .clickable { onChange(argb, width) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (argb == pen.colorArgb) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text("Nib — ${"%.1f".format(width)} dp", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = width,
                onValueChange = { width = it },
                onValueChangeFinished = { onChange(pen.colorArgb, width) },
                valueRange = if (pen.tool == InkTool.HIGHLIGHTER) 8f..36f else 1f..16f,
            )
        }
    }
}
