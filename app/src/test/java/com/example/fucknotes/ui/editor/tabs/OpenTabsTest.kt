package com.example.fucknotes.ui.editor.tabs

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class OpenTabsTest {
    @Before fun reset() = OpenTabs.clear()
    @After fun cleanup() = OpenTabs.clear()

    @Test fun openingKeepsOrderAndReopeningOnlyRenames() {
        OpenTabs.open(1, "Lecture")
        OpenTabs.open(2, "Scratch")
        OpenTabs.open(1, "Lecture 1")
        assertEquals(listOf(1L, 2L), OpenTabs.tabs.map { it.noteId })
        assertEquals("Lecture 1", OpenTabs.tabs.first().title)
    }

    @Test fun blankTitleNeverOverwritesAName() {
        OpenTabs.open(1, "Lecture")
        OpenTabs.rename(1, "")
        assertEquals("Lecture", OpenTabs.tabs.single().title)
    }

    @Test fun closingPicksTheNeighbourOnTheLeftThenTheRightThenNothing() {
        listOf(1L, 2L, 3L).forEach { OpenTabs.open(it, "n$it") }
        assertEquals(1L, OpenTabs.close(2))
        assertEquals(3L, OpenTabs.close(1))
        assertNull(OpenTabs.close(3))
        assertEquals(emptyList<OpenTabs.Tab>(), OpenTabs.tabs)
    }

    @Test fun closingAnUnknownIdLeavesTheBarAloneAndSuggestsTheFirstTab() {
        OpenTabs.open(5, "Only")
        assertEquals(5L, OpenTabs.close(99))
        assertEquals(listOf(5L), OpenTabs.tabs.map { it.noteId })
    }

    @Test fun forgetDropsOnlyThatTab() {
        OpenTabs.open(1, "a"); OpenTabs.open(2, "b")
        OpenTabs.forget(1)
        assertEquals(listOf(2L), OpenTabs.tabs.map { it.noteId })
    }

    @Test fun theOldestTabFallsOffPastTheLimit() {
        (1..OpenTabs.MAX_TABS + 2).forEach { OpenTabs.open(it.toLong(), "n$it") }
        assertEquals(OpenTabs.MAX_TABS, OpenTabs.tabs.size)
        assertEquals(3L, OpenTabs.tabs.first().noteId)
    }
}
