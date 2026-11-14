package com.example.fucknotes.ui.editor.ink

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.HighlightAlt
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.ink.InkShape
import com.example.fucknotes.data.model.FingerInputMode
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.PenPreset
import com.example.fucknotes.ui.common.FloatingPanel
import com.example.fucknotes.ui.common.ToolButton
import com.example.fucknotes.ui.common.ToolDivider
import com.example.fucknotes.ui.editor.EditorChrome

/** What the red highlighter's button says; tests and the ⋯ menu's list use the same words. */
const val RED_HIGHLIGHTER_LABEL = "Temporary red highlighter (fades after you stop writing)"

/**
 * The pen tray, floating over the page.
 *
 * It is laid out the way a pen case is: what marks the page on the left, the pens themselves in the
 * middle, and the lock on the right. Each slot keeps its own colour and nib width, so switching pens
 * restores exactly the nib that slot was last set to — the behaviour of a real pen case. Tapping
 * the pen already in hand opens its colour and width.
 *
 * The bar is always one row. It sits beside the note bar, so on an upright tablet it has about
 * half the width it has on a tablet lying down; rather than drop to a second row it tightens its
 * buttons and folds the tools that no longer fit behind one "More tools" button
 * ([ToolbarLayout.fit]). The pens stay out: they are what the bar is for. Anything in [hidden]
 * has been put away from the ⋯ menu and is not on the bar at all.
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
    shape: InkShape = InkShape.RECTANGLE,
    onSelectShape: (InkShape) -> Unit = {},
    canUndo: Boolean = false,
    onUndo: () -> Unit = {},
    /** Items put away from the ⋯ menu: [ToolbarTool.key]s and [ToolbarLayout.penKey]s. */
    hidden: Set<String> = emptySet(),
    /** Shapes are drawn hand-drawn style; the Shapes menu's "Clean lines" switch turns it off. */
    sketchyShapes: Boolean = true,
    onSketchyShapes: (Boolean) -> Unit = {},
) {
    var editingPen by remember { mutableIntStateOf(-1) }
    var shapesOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }

    val slots = ToolbarLayout.visible(pens, selectedPen, hidden)
    // The compact bar stands alone, so it carries the one note action the hand reaches for most.
    val undoShown = chrome == EditorChrome.COMPACT

    BoxWithConstraints(modifier.widthIn(max = 736.dp)) {
        val fit = ToolbarLayout.fit(
            availableDp = maxWidth.value,
            toolCount = slots.tools.size,
            penCount = slots.pens.size,
            highlighterCount = slots.highlighters.size,
            undo = undoShown,
        )
        val slot = fit.slotDp.dp
        val inline = slots.tools.take(fit.inlineTools)
        val folded = slots.tools.drop(fit.inlineTools)

        fun inHand(item: ToolbarTool): Boolean = when (item) {
            ToolbarTool.ERASER -> tool == InkTool.ERASER
            ToolbarTool.SELECT -> tool == InkTool.LASSO
            ToolbarTool.SHAPES -> tool == InkTool.SHAPE
            ToolbarTool.RED_HIGHLIGHTER -> tool == InkTool.LASER
            ToolbarTool.FINGER -> false
        }
        fun iconOf(item: ToolbarTool): ImageVector = when (item) {
            ToolbarTool.ERASER -> EraserIcon
            ToolbarTool.SELECT -> Icons.Outlined.HighlightAlt
            ToolbarTool.FINGER -> fingerModeIcon(fingerInputMode)
            ToolbarTool.SHAPES -> Icons.Outlined.Category
            ToolbarTool.RED_HIGHLIGHTER -> Icons.Outlined.Highlight
        }
        val shapesMenu: @Composable () -> Unit = {
            ShapesMenu(
                expanded = shapesOpen,
                current = shape.takeIf { tool == InkTool.SHAPE },
                sketchy = sketchyShapes,
                onDismiss = { shapesOpen = false },
                onPick = { shapesOpen = false; onSelectShape(it) },
                onSketchy = onSketchyShapes,
            )
        }

        FloatingPanel {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (undoShown) ToolButton(
                    icon = Icons.AutoMirrored.Outlined.Undo, label = "Undo",
                    enabled = canUndo, onClick = onUndo, size = slot,
                )

                inline.forEach { item ->
                    when (item) {
                        ToolbarTool.ERASER -> ToolButton(
                            icon = EraserIcon, label = "Eraser", selected = tool == InkTool.ERASER,
                            onClick = { onSelectTool(InkTool.ERASER) }, size = slot,
                        )
                        ToolbarTool.SELECT -> ToolButton(
                            icon = Icons.Outlined.HighlightAlt, label = "Select: circle strokes to move them",
                            selected = tool == InkTool.LASSO, onClick = { onSelectTool(InkTool.LASSO) }, size = slot,
                        )
                        ToolbarTool.FINGER -> ToolButton(
                            icon = fingerModeIcon(fingerInputMode),
                            label = "Finger input: ${fingerInputMode.label}. Tap for ${fingerInputMode.next().label}",
                            modifier = Modifier.semantics { stateDescription = fingerInputMode.description },
                            onClick = { onFingerInputMode(fingerInputMode.next()) }, size = slot,
                        )
                        ToolbarTool.SHAPES -> Box {
                            ToolButton(
                                icon = Icons.Outlined.Category, label = "Shapes", selected = tool == InkTool.SHAPE,
                                modifier = Modifier.semantics { stateDescription = shape.label },
                                onClick = { shapesOpen = true }, size = slot,
                            )
                            shapesMenu()
                        }
                        ToolbarTool.RED_HIGHLIGHTER -> ToolButton(
                            icon = Icons.Outlined.Highlight, label = RED_HIGHLIGHTER_LABEL,
                            selected = tool == InkTool.LASER, tint = Color(0xFFE84158),
                            onClick = { onSelectTool(InkTool.LASER) }, size = slot,
                        )
                    }
                }

                if (folded.isNotEmpty()) {
                    // The button wears the folded tool that is in hand, so the page never
                    // erases or selects without the bar saying so.
                    val held = folded.firstOrNull(::inHand)
                    Box {
                        ToolButton(
                            icon = held?.let(::iconOf) ?: Icons.Outlined.Handyman,
                            label = "More tools",
                            selected = held != null,
                            modifier = Modifier.semantics { stateDescription = held?.label ?: "No tool from this menu in hand" },
                            onClick = { moreOpen = true }, size = slot,
                        )
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            folded.forEach { item ->
                                DropdownMenuItem(
                                    text = {
                                        Text(if (item == ToolbarTool.FINGER) "Finger input: ${fingerInputMode.label}" else item.label)
                                    },
                                    leadingIcon = {
                                        Icon(
                                            iconOf(item), contentDescription = null,
                                            tint = if (item == ToolbarTool.RED_HIGHLIGHTER) Color(0xFFE84158)
                                            else androidx.compose.material3.LocalContentColor.current,
                                        )
                                    },
                                    trailingIcon = { if (inHand(item)) Icon(Icons.Filled.Check, contentDescription = "In hand") },
                                    onClick = {
                                        when (item) {
                                            ToolbarTool.ERASER -> { moreOpen = false; onSelectTool(InkTool.ERASER) }
                                            ToolbarTool.SELECT -> { moreOpen = false; onSelectTool(InkTool.LASSO) }
                                            // Stays open: the mode is cycled, and its name is the row's text.
                                            ToolbarTool.FINGER -> onFingerInputMode(fingerInputMode.next())
                                            ToolbarTool.SHAPES -> { moreOpen = false; shapesOpen = true }
                                            ToolbarTool.RED_HIGHLIGHTER -> { moreOpen = false; onSelectTool(InkTool.LASER) }
                                        }
                                    },
                                )
                            }
                        }
                        if (ToolbarTool.SHAPES in folded) shapesMenu()
                    }
                }

                if (slots.tools.isNotEmpty()) ToolDivider()

                Row(
                    Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()).height(slot),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val swatch: @Composable (Int, Boolean) -> Unit = { slotIndex, highlighter ->
                        pens.getOrNull(slotIndex)?.let { pen ->
                            val active = tool == pen.tool && selectedPen == slotIndex
                            Box {
                                PenGlyph(
                                    pen = pen,
                                    active = active,
                                    highlighter = highlighter,
                                    onClick = { if (active) editingPen = slotIndex else onSelectPen(slotIndex) },
                                    darkReader = darkReader,
                                    size = slot,
                                )
                                if (editingPen == slotIndex) {
                                    PenEditorPopup(
                                        pen = pen,
                                        onDismiss = { editingPen = -1 },
                                        onChange = { nextColor, width -> onEditPen(slotIndex, nextColor, width) },
                                        darkReader = darkReader,
                                    )
                                }
                            }
                        }
                    }
                    slots.pens.forEach { swatch(it, false) }
                    // The highlighters are real tray slots too, so each keeps its own translucent
                    // width and colour.
                    if (slots.pens.isNotEmpty() && slots.highlighters.isNotEmpty()) ToolDivider()
                    slots.highlighters.forEach { swatch(it, true) }
                }

                ToolDivider()
                ToolButton(
                    icon = if (chrome == EditorChrome.FULL) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                    label = chrome.lockLabel, selected = chrome != EditorChrome.FULL,
                    onClick = onCycleChrome, size = slot,
                )
            }
        }
    }
}

/** The shapes to drag out, and whether they come out hand-drawn or exact. */
@Composable
private fun ShapesMenu(
    expanded: Boolean,
    current: InkShape?,
    sketchy: Boolean,
    onDismiss: () -> Unit,
    onPick: (InkShape) -> Unit,
    onSketchy: (Boolean) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        InkShape.entries.forEach { choice ->
            DropdownMenuItem(
                text = { Text(choice.label) },
                trailingIcon = { if (choice == current) Icon(Icons.Filled.Check, contentDescription = "In hand") },
                onClick = { onPick(choice) },
            )
        }
        HorizontalDivider()
        // Off by default: shapes look drawn by the hand that wrote the page. On gives ruler lines.
        DropdownMenuItem(
            text = { Text("Clean lines") },
            trailingIcon = { Switch(checked = !sketchy, onCheckedChange = { onSketchy(!it) }) },
            onClick = { onSketchy(!sketchy) },
        )
    }
}

private val InkShape.label: String get() = when (this) {
    InkShape.LINE -> "Line"
    InkShape.ARROW -> "Arrow"
    InkShape.RECTANGLE -> "Rectangle"
    InkShape.CIRCLE -> "Circle"
}

internal fun fingerModeIcon(mode: FingerInputMode) = when (mode) {
    FingerInputMode.HAND -> Icons.Outlined.PanTool
    FingerInputMode.DRAW -> Icons.Outlined.Gesture
    FingerInputMode.DISABLED -> Icons.Outlined.Edit
}
