package com.example.fucknotes.ui.editor.ink

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import com.example.fucknotes.data.model.DefaultPenTray
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
    compact: Boolean,
    onSelectPen: (Int) -> Unit,
    onEditPen: (index: Int, colorArgb: Int, widthDp: Float) -> Unit,
    onSelectTool: (InkTool) -> Unit,
    onToggleCompact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingPen by remember { mutableIntStateOf(-1) }

    // The expanded tray needs room for five pens, two highlighters and the fixed controls. The
    // compact tray omits the family buttons, so it stays a little shorter on narrow windows.
    FloatingPanel(modifier.widthIn(max = if (compact) 520.dp else 580.dp)) {
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

            val pen = pens.getOrNull(selectedPen) ?: pens.firstOrNull() ?: DefaultPenTray.first()
            val penSlots = pens.withIndex().filter { it.value.tool == InkTool.PEN }.take(5)
            val highlighterSlots = pens.withIndex().filter { it.value.tool == InkTool.HIGHLIGHTER }.take(2)

            // Keep five pen colors on the bar. A color is tied to a real pen slot so selecting it
            // also changes the active family when a highlighter or eraser was in hand. A custom
            // active pen color occupies the last slot, preserving the full color editor without
            // growing the toolbar.
            val quickColors = listOf(0xFF171B23.toInt(), 0xFF2386DC.toInt(), 0xFFE84158.toInt(),
                0xFF299B62.toInt(), 0xFFFFDF43.toInt())
            val penColors = if (pen.tool == InkTool.PEN && pen.colorArgb !in quickColors) {
                quickColors.take(4) + pen.colorArgb
            } else quickColors
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()).height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                penColors.forEachIndexed { index, color ->
                    val slot = penSlots.getOrNull(index)
                    val slotIndex = slot?.index ?: -1
                    val slotPen = slot?.value ?: pen.copy(tool = InkTool.PEN, colorArgb = color)
                    Box {
                        PenGlyph(
                            pen = slotPen.copy(colorArgb = color),
                            active = tool == InkTool.PEN && selectedPen == slotIndex && color == pen.colorArgb,
                            onClick = {
                                if (slotIndex >= 0) {
                                    if (tool == InkTool.PEN && selectedPen == slotIndex && color == pen.colorArgb) {
                                        editingPen = slotIndex
                                    } else {
                                        onSelectPen(slotIndex)
                                        onEditPen(slotIndex, color, slotPen.widthDp)
                                    }
                                }
                            },
                        )
                        if (editingPen == slotIndex && slotIndex >= 0) {
                            PenEditorPopup(
                                pen = pens.getOrNull(slotIndex) ?: slotPen,
                                onDismiss = { editingPen = -1 },
                                onChange = { nextColor, width -> onEditPen(slotIndex, nextColor, width) },
                            )
                        }
                    }
                }

                // Two dedicated highlighter choices stay visible in compact mode as well. Each is
                // a real tray slot, so its translucent width and colour persist independently.
                if (highlighterSlots.isNotEmpty()) ToolDivider()
                highlighterSlots.forEach { slot ->
                    val slotIndex = slot.index
                    val slotPen = slot.value
                    Box {
                        PenGlyph(
                            pen = slotPen,
                            active = tool == InkTool.HIGHLIGHTER && selectedPen == slotIndex,
                            highlighter = true,
                            onClick = {
                                if (tool == InkTool.HIGHLIGHTER && selectedPen == slotIndex) editingPen = slotIndex
                                else onSelectPen(slotIndex)
                            },
                        )
                        if (editingPen == slotIndex) {
                            PenEditorPopup(
                                pen = pens.getOrNull(slotIndex) ?: slotPen,
                                onDismiss = { editingPen = -1 },
                                onChange = { nextColor, width -> onEditPen(slotIndex, nextColor, width) },
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
