package com.example.fucknotes.ui.editor.ink

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.PenPreset
import com.example.fucknotes.ui.common.FloatingPanel
import com.example.fucknotes.ui.common.ToolButton
import com.example.fucknotes.ui.common.ToolDivider

/**
 * The pen tray, floating over the page.
 *
 * It is laid out the way a pen case is: what marks the page on the left, the pens themselves in the
 * middle, and the page's own settings on the right. Each slot keeps its own colour and nib width,
 * so switching pens restores exactly the nib that slot was last set to — the behaviour of a real
 * pen case. Tapping the pen already in hand opens its colour and width.
 */
@Composable
fun InkToolbar(
    pens: List<PenPreset>,
    selectedPen: Int,
    tool: InkTool,
    fingerDrawing: Boolean,
    zoomLocked: Boolean,
    onSelectPen: (Int) -> Unit,
    onEditPen: (index: Int, colorArgb: Int, widthDp: Float) -> Unit,
    onSelectTool: (InkTool) -> Unit,
    onToggleFingerDrawing: () -> Unit,
    onToggleZoomLock: () -> Unit,
    onAddImage: () -> Unit,
    onPaperStyle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingPen by remember { mutableIntStateOf(-1) }

    FloatingPanel(modifier) {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(
                icon = Icons.Outlined.AutoFixNormal,
                label = "Eraser",
                selected = tool == InkTool.ERASER,
                onClick = { onSelectTool(InkTool.ERASER) },
            )
            ToolButton(
                icon = Icons.Outlined.PanTool,
                label = "Move the page",
                selected = tool == InkTool.PAN,
                onClick = { onSelectTool(InkTool.PAN) },
            )
            // Stylus-only mode. Off, a finger scrolls the page instead of drawing on it, so a hand
            // can rest on the glass while the pen writes.
            ToolButton(
                icon = if (fingerDrawing) Icons.Outlined.TouchApp else Icons.Outlined.Draw,
                label = if (fingerDrawing) "Finger drawing is on" else "Stylus only",
                selected = fingerDrawing,
                onClick = onToggleFingerDrawing,
            )

            ToolDivider()

            Row(
                Modifier.height(52.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                pens.forEachIndexed { index, pen ->
                    Box {
                        PenGlyph(
                            pen = pen,
                            active = tool.isBrush && index == selectedPen,
                            onClick = {
                                if (tool.isBrush && index == selectedPen) editingPen = index
                                else onSelectPen(index)
                            },
                        )
                        if (editingPen == index) {
                            PenEditorPopup(
                                pen = pen,
                                onDismiss = { editingPen = -1 },
                                onChange = { color, width -> onEditPen(index, color, width) },
                            )
                        }
                    }
                }
            }

            ToolDivider()

            ToolButton(icon = Icons.Outlined.Image, label = "Add a picture", onClick = onAddImage)
            ToolButton(icon = Icons.Outlined.FormatPaint, label = "Paper", onClick = onPaperStyle)
            // Locked, the page still scrolls but keeps its scale, so handwriting stays the size the
            // user set it to.
            ToolButton(
                icon = if (zoomLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                label = if (zoomLocked) "Zoom is locked" else "Lock the zoom",
                selected = zoomLocked,
                onClick = onToggleZoomLock,
            )
        }
    }
}
