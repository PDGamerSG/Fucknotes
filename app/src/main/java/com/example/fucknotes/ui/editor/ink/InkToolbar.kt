package com.example.fucknotes.ui.editor.ink

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.InkColors
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
    compact: Boolean,
    onSelectPen: (Int) -> Unit,
    onEditPen: (index: Int, colorArgb: Int, widthDp: Float) -> Unit,
    onSelectTool: (InkTool) -> Unit,
    onToggleCompact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingPen by remember { mutableIntStateOf(-1) }

    FloatingPanel(modifier) {
        Row(
            Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!compact) {
                ToolButton(icon = Icons.Outlined.Edit, label = "Pen", selected = tool == InkTool.PEN,
                    onClick = { onSelectTool(InkTool.PEN) })
                ToolButton(icon = Icons.Outlined.BorderColor, label = "Highlighter", selected = tool == InkTool.HIGHLIGHTER,
                    onClick = { onSelectTool(InkTool.HIGHLIGHTER) })
            }
            ToolButton(
                icon = EraserIcon,
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
            ToolDivider()

            val pen = pens.getOrElse(selectedPen) { pens.first() }
            val colors = (InkColors + pen.colorArgb).distinct()
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()).height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                colors.forEachIndexed { index, color ->
                    Box {
                        PenGlyph(
                            pen = pen.copy(colorArgb = color),
                            active = tool.isBrush && color == pen.colorArgb,
                            onClick = {
                                if (tool.isBrush && color == pen.colorArgb) editingPen = index
                                else {
                                    onSelectPen(selectedPen)
                                    onEditPen(selectedPen, color, pen.widthDp)
                                }
                            },
                        )
                        if (editingPen == index) {
                            PenEditorPopup(
                                pen = pen,
                                onDismiss = { editingPen = -1 },
                                onChange = { nextColor, width -> onEditPen(selectedPen, nextColor, width) },
                            )
                        }
                    }
                }
            }

            ToolDivider()

            // Keep the compact-mode escape visible while the color tray scrolls.
            ToolButton(
                icon = if (compact) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                label = if (compact) "Show all controls" else "Hide extra controls",
                selected = compact,
                onClick = onToggleCompact,
            )
        }
    }
}
