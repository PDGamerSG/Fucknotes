package com.example.fucknotes.ui.editor.ink

import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.PenPreset

/** The tools on the pen bar, in the order they sit there. Each can be put away and brought back. */
enum class ToolbarTool(val key: String, val label: String) {
    ERASER("eraser", "Eraser"),
    SELECT("select", "Select"),
    FINGER("finger", "Finger input"),
    SHAPES("shapes", "Shapes"),
    RED_HIGHLIGHTER("laser", "Red highlighter"),
}

/**
 * What the pen bar shows and how it fits.
 *
 * Kept free of Compose so the rules can be checked on the JVM: which pens and tools are on the
 * bar once the user has put some away, and how many tool buttons fit beside the pens before the
 * rest go behind one "More tools" button.
 */
object ToolbarLayout {
    /** A button on a roomy bar, and the smallest it may shrink to on a tight one. */
    const val SLOT_DP = 48f
    const val TIGHT_SLOT_DP = 44f
    const val DIVIDER_DP = 9f
    const val PANEL_PADDING_DP = 8f

    fun penKey(slot: Int): String = "pen:$slot"

    data class Slots(val pens: List<Int>, val highlighters: List<Int>, val tools: List<ToolbarTool>)

    data class Fit(val slotDp: Float, val inlineTools: Int)

    /** The tray slots the bar has room to offer: the first four pens and the first two highlighters. */
    fun penSlots(pens: List<PenPreset>): List<Int> =
        pens.withIndex().filter { it.value.tool == InkTool.PEN }.take(MAX_PENS).map { it.index }

    fun highlighterSlots(pens: List<PenPreset>): List<Int> =
        pens.withIndex().filter { it.value.tool == InkTool.HIGHLIGHTER }.take(MAX_HIGHLIGHTERS).map { it.index }

    /**
     * What is on the bar: every slot and tool less what was put away. A hidden slot leaves a gap
     * that closes up — the next pen in the tray does not slide in, or putting a pen away would
     * only swap it for another. The pen in hand is always shown, even when it is one the bar
     * would not otherwise carry: a pen button or an older tray can select such a slot, and a
     * page must never be written on with a pen that cannot be seen.
     */
    fun visible(pens: List<PenPreset>, selectedPen: Int, hidden: Set<String>): Slots {
        fun withInHand(shown: List<Int>, tool: InkTool, limit: Int): List<Int> {
            if (pens.getOrNull(selectedPen)?.tool != tool || selectedPen in shown) return shown
            return (if (shown.size >= limit) shown.take(limit - 1) else shown) + selectedPen
        }
        return Slots(
            pens = withInHand(penSlots(pens).filter { penKey(it) !in hidden }, InkTool.PEN, MAX_PENS),
            highlighters = withInHand(
                highlighterSlots(pens).filter { penKey(it) !in hidden }, InkTool.HIGHLIGHTER, MAX_HIGHLIGHTERS,
            ),
            tools = ToolbarTool.entries.filter { it.key !in hidden },
        )
    }

    /**
     * How the bar fits in [availableDp]: at full size with every tool if it can; otherwise with
     * tighter buttons, as many tools as fit in their usual order, and one "More tools" button
     * for the rest. The pens are never folded away — they are what the bar is for — and scroll
     * if even they do not fit.
     */
    fun fit(availableDp: Float, toolCount: Int, penCount: Int, highlighterCount: Int, undo: Boolean): Fit {
        if (toolCount <= 0) return Fit(SLOT_DP, 0)
        fun withoutTools(slot: Float): Float = PANEL_PADDING_DP +
            slot + DIVIDER_DP + // the lock and the rule before it
            penCount * slot +
            (if (highlighterCount > 0) DIVIDER_DP + highlighterCount * slot else 0f) +
            (if (undo) slot else 0f) +
            DIVIDER_DP // the rule between the tools and the pens
        if (withoutTools(SLOT_DP) + toolCount * SLOT_DP <= availableDp) return Fit(SLOT_DP, toolCount)
        val room = availableDp - withoutTools(TIGHT_SLOT_DP)
        if (toolCount * TIGHT_SLOT_DP <= room) return Fit(TIGHT_SLOT_DP, toolCount)
        // One place goes to the More tools button.
        val inline = kotlin.math.floor((room - TIGHT_SLOT_DP) / TIGHT_SLOT_DP).toInt().coerceIn(0, toolCount - 1)
        return Fit(TIGHT_SLOT_DP, inline)
    }

    /** Anything may be put away except the last pen: a pen bar with no pen cannot write. */
    fun canHide(key: String, pens: List<PenPreset>, hidden: Set<String>): Boolean {
        val slots = penSlots(pens)
        if (slots.none { penKey(it) == key }) return true
        return slots.any { penKey(it) != key && penKey(it) !in hidden }
    }

    private const val MAX_PENS = 4
    private const val MAX_HIGHLIGHTERS = 2
}
