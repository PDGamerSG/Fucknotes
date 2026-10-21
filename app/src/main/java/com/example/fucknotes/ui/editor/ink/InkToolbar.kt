package com.example.fucknotes.ui.editor.ink

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.model.FingerInputMode
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
    fingerInputMode: FingerInputMode,
    compact: Boolean,
    onSelectPen: (Int) -> Unit,
    onEditPen: (index: Int, colorArgb: Int, widthDp: Float) -> Unit,
    onSelectTool: (InkTool) -> Unit,
    onFingerInputMode: (FingerInputMode) -> Unit,
    onToggleCompact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingPen by remember { mutableIntStateOf(-1) }
    var showFingerModes by remember { mutableStateOf(false) }

    // Three pens, two highlighters and three fixed controls; only the swatches scroll on phones.
    FloatingPanel(modifier.widthIn(max = 420.dp)) {
        Row(
            Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(
                icon = EraserIcon,
                label = "Eraser",
                selected = tool == InkTool.ERASER,
                onClick = { onSelectTool(InkTool.ERASER) },
                size = 48.dp,
            )
            Box {
                ToolButton(
                    icon = fingerModeIcon(fingerInputMode),
                    label = "Finger input: ${fingerInputMode.label}",
                    selected = showFingerModes,
                    onClick = { showFingerModes = true },
                    size = 48.dp,
                )
                DropdownMenu(expanded = showFingerModes, onDismissRequest = { showFingerModes = false }) {
                    FingerInputMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            leadingIcon = { Icon(fingerModeIcon(mode), contentDescription = null) },
                            trailingIcon = {
                                if (mode == fingerInputMode) Icon(Icons.Filled.Check, contentDescription = "Selected")
                            },
                            onClick = {
                                showFingerModes = false
                                onFingerInputMode(mode)
                            },
                        )
                    }
                }
            }
            ToolDivider()

            val penSlots = pens.withIndex().filter { it.value.tool == InkTool.PEN }.take(3)
            val highlighterSlots = pens.withIndex().filter { it.value.tool == InkTool.HIGHLIGHTER }.take(2)
            // Preserve a selected legacy slot without adding a fourth pen to the bar.
            val selected = pens.getOrNull(selectedPen)
            val visiblePens = if (selected?.tool == InkTool.PEN && penSlots.none { it.index == selectedPen }) {
                penSlots.take(2) + IndexedValue(selectedPen, selected)
            } else penSlots
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()).height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                visiblePens.forEach { slot ->
                    val slotIndex = slot.index
                    val slotPen = slot.value
                    Box {
                        PenGlyph(
                            pen = slotPen,
                            active = tool == InkTool.PEN && selectedPen == slotIndex,
                            onClick = {
                                if (tool == InkTool.PEN && selectedPen == slotIndex) editingPen = slotIndex
                                else onSelectPen(slotIndex)
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
                size = 48.dp,
            )
        }
    }
}

private fun fingerModeIcon(mode: FingerInputMode) = when (mode) {
    FingerInputMode.HAND -> Icons.Outlined.PanTool
    FingerInputMode.DRAW -> Icons.Outlined.Gesture
    FingerInputMode.DISABLED -> Icons.Outlined.Block
}
