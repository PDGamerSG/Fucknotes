package com.example.fucknotes.ui.editor.ink

import com.example.fucknotes.data.model.DefaultPenTray
import com.example.fucknotes.data.model.InkTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolbarLayoutTest {
    private val tray = DefaultPenTray

    @Test fun nothingPutAwayShowsFourPensTwoHighlightersAndEveryTool() {
        val slots = ToolbarLayout.visible(tray, selectedPen = 0, hidden = emptySet())
        assertEquals(listOf(0, 1, 2, 3), slots.pens)
        assertEquals(listOf(6, 7), slots.highlighters)
        assertEquals(ToolbarTool.entries.toList(), slots.tools)
    }

    @Test fun whatIsPutAwayLeavesTheBarAndNothingTakesItsPlace() {
        val hidden = setOf(ToolbarLayout.penKey(1), ToolbarLayout.penKey(7), ToolbarTool.SHAPES.key, ToolbarTool.FINGER.key)
        val slots = ToolbarLayout.visible(tray, selectedPen = 0, hidden = hidden)
        assertEquals("the fifth pen must not slide into the gap", listOf(0, 2, 3), slots.pens)
        assertEquals(listOf(6), slots.highlighters)
        assertEquals(listOf(ToolbarTool.ERASER, ToolbarTool.SELECT, ToolbarTool.RED_HIGHLIGHTER), slots.tools)
    }

    @Test fun thePenInHandIsAlwaysOnTheBar() {
        // A pen button or an old tray can select a slot that is not shown; it must still be seen.
        val beyond = ToolbarLayout.visible(tray, selectedPen = 4, hidden = emptySet())
        assertTrue(4 in beyond.pens)
        assertEquals(4, beyond.pens.size)
        val putAway = ToolbarLayout.visible(tray, selectedPen = 1, hidden = setOf(ToolbarLayout.penKey(1)))
        assertTrue(1 in putAway.pens)
    }

    @Test fun theLastPenCannotBePutAway() {
        val hidden = setOf(ToolbarLayout.penKey(0), ToolbarLayout.penKey(1), ToolbarLayout.penKey(2))
        assertFalse(ToolbarLayout.canHide(ToolbarLayout.penKey(3), tray, hidden))
        assertTrue(ToolbarLayout.canHide(ToolbarLayout.penKey(3), tray, hidden - ToolbarLayout.penKey(0)))
        assertTrue("a highlighter is not the last pen", ToolbarLayout.canHide(ToolbarLayout.penKey(6), tray, hidden))
        assertTrue(ToolbarLayout.canHide(ToolbarTool.ERASER.key, tray, hidden))
    }

    @Test fun aWideBarShowsEveryToolAtFullSize() {
        // Landscape on the tablet: 1164 dp less the note bar leaves far more than the bar needs.
        val fit = ToolbarLayout.fit(availableDp = 736f, toolCount = 5, penCount = 4, highlighterCount = 2, undo = false)
        assertEquals(ToolbarLayout.Fit(ToolbarLayout.SLOT_DP, 5), fit)
    }

    @Test fun portraitKeepsTheEraserAndFoldsTheRestBehindOneButton() {
        // Portrait on the tablet: 777 dp wide, 16 dp of margin, a 314 dp note bar and an 8 dp gap.
        val fit = ToolbarLayout.fit(availableDp = 439f, toolCount = 5, penCount = 4, highlighterCount = 2, undo = false)
        assertEquals(ToolbarLayout.TIGHT_SLOT_DP, fit.slotDp, 0f)
        assertEquals("the eraser stays; the others go behind More tools", 1, fit.inlineTools)
        // And the whole bar, with its More tools button, is no wider than the room it has.
        val buttons = 1 + 1 + 4 + 2 + 1 // eraser, more tools, pens, highlighters, lock
        val width = ToolbarLayout.PANEL_PADDING_DP + buttons * fit.slotDp + 3 * ToolbarLayout.DIVIDER_DP
        assertTrue("bar is $width dp wide", width <= 439f)
    }

    @Test fun puttingPensAwayMakesRoomForTools() {
        val four = ToolbarLayout.fit(439f, toolCount = 5, penCount = 4, highlighterCount = 2, undo = false)
        val two = ToolbarLayout.fit(439f, toolCount = 5, penCount = 2, highlighterCount = 1, undo = false)
        assertTrue(two.inlineTools > four.inlineTools)
    }

    @Test fun aBarWithNoRoomStillOffersTheToolsButton() {
        val fit = ToolbarLayout.fit(availableDp = 200f, toolCount = 5, penCount = 4, highlighterCount = 2, undo = true)
        assertEquals(0, fit.inlineTools)
        assertEquals(ToolbarLayout.TIGHT_SLOT_DP, fit.slotDp, 0f)
    }

    @Test fun noToolsNeedNoRoom() {
        assertEquals(0, ToolbarLayout.fit(439f, toolCount = 0, penCount = 4, highlighterCount = 2, undo = false).inlineTools)
    }

    @Test fun toolKeysAreStableBecauseTheyAreStored() {
        assertEquals(listOf("eraser", "select", "finger", "shapes", "laser"), ToolbarTool.entries.map { it.key })
        assertEquals("pen:3", ToolbarLayout.penKey(3))
        assertEquals(InkTool.PEN, tray[3].tool)
    }
}
