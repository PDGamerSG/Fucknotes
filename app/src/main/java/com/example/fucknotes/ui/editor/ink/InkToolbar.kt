package com.example.fucknotes.ui.editor.ink

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HighlightAlt
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Gesture
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.model.FingerInputMode
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.PenPreset
import com.example.fucknotes.ui.common.FloatingPanel
import com.example.fucknotes.ui.common.ToolButton
import com.example.fucknotes.ui.common.ToolDivider
import com.example.fucknotes.ui.editor.EditorChrome

/**
 * The pen tray, floating over the page.
 *
 * It is laid out the way a pen case is: what marks the page on the left, the pens themselves in the
 * middle, and the lock on the right. Each slot keeps its own colour and nib width, so switching pens
 * restores exactly the nib that slot was last set to — the behaviour of a real pen case. Tapping
 * the pen already in hand opens its colour and width.
 *
 * The lock is the one control that changes the chrome itself: it steps [chrome] through
 * [EditorChrome.next], so the same button clears the page down to the pen bar and then to nothing
 * but the page.
 */
@Composable
fun InkToolbar(
    pens: List<PenPreset>,
    selectedPen: Int,
    tool: InkTool,
    fingerInputMode: FingerInputMode,
    chrome: EditorChrome,
    onSelectPen: (Int) -> Unit,
    onEditPen: (index: Int, colorArgb: Int, widthDp: Float) -> Unit,
    onSelectTool: (InkTool) -> Unit,
    onFingerInputMode: (FingerInputMode) -> Unit,
    onCycleChrome: () -> Unit,
    modifier: Modifier = Modifier,
    /** Swatches show the colour each pen will actually leave on a night page. */
    darkReader: Boolean = false,
) {
    var editingPen by remember { mutableIntStateOf(-1) }

    // Three pens, two highlighters and three fixed controls; only the swatches scroll on phones.
    // 4 dp of bar beyond the 48 dp targets, each of which keeps 4 dp of its own air around its
    // disc — so the eraser's halo, the lock's halo and a selected pen's ring all sit the same
    // 8 dp in from the bar's rounded ends.
    FloatingPanel(modifier.widthIn(max = 448.dp)) {
        Row(
            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(
                icon = EraserIcon,
                label = "Eraser",
                selected = tool == InkTool.ERASER,
                onClick = { onSelectTool(InkTool.ERASER) },
                size = 48.dp,
            )
            ToolButton(
                icon = Icons.Outlined.HighlightAlt,
                label = "Select: circle strokes to move them",
                selected = tool == InkTool.LASSO,
                onClick = { onSelectTool(InkTool.LASSO) },
                size = 48.dp,
            )
            ToolButton(
                icon = fingerModeIcon(fingerInputMode),
                label = "Finger input: ${fingerInputMode.label}. Tap for ${fingerInputMode.next().label}",
                modifier = Modifier.semantics { stateDescription = fingerInputMode.description },
                onClick = { onFingerInputMode(fingerInputMode.next()) },
                size = 48.dp,
            )
            ToolDivider()

            val penSlots = pens.withIndex().filter { it.value.tool == InkTool.PEN }.take(3)
            val highlighterSlots = pens.withIndex().filter { it.value.tool == InkTool.HIGHLIGHTER }.take(2)
            // Preserve a selected legacy slot without adding a fourth pen to the bar.
            val selected = pens.getOrNull(selectedPen)
            val visiblePens = if (selected?.tool == InkTool.PEN && penSlots.none { it.index == selectedPen }) {
                penSlots.take(2) + IndexedValue(selectedPen, selected)
            } else penSlots
            Row(
                Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()).height(48.dp),
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
                            darkReader = darkReader,
                        )
                        if (editingPen == slotIndex && slotIndex >= 0) {
                            PenEditorPopup(
                                pen = pens.getOrNull(slotIndex) ?: slotPen,
                                onDismiss = { editingPen = -1 },
                                onChange = { nextColor, width -> onEditPen(slotIndex, nextColor, width) },
                                darkReader = darkReader,
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
                            darkReader = darkReader,
                        )
                        if (editingPen == slotIndex) {
                            PenEditorPopup(
                                pen = pens.getOrNull(slotIndex) ?: slotPen,
                                onDismiss = { editingPen = -1 },
                                onChange = { nextColor, width -> onEditPen(slotIndex, nextColor, width) },
                                darkReader = darkReader,
                            )
                        }
                    }
                }
            }

            ToolDivider()

            // The lock stays at the end of the bar in every state so the page can always be
            // cleared — or restored — from the same spot.
            ToolButton(
                icon = if (chrome == EditorChrome.FULL) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                label = chrome.lockLabel,
                selected = chrome != EditorChrome.FULL,
                onClick = onCycleChrome,
                size = 48.dp,
            )
        }
    }
}

private fun fingerModeIcon(mode: FingerInputMode) = when (mode) {
    FingerInputMode.HAND -> Icons.Outlined.PanTool
    FingerInputMode.DRAW -> Icons.Outlined.Gesture
    FingerInputMode.DISABLED -> Icons.Outlined.Edit
}
