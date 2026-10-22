package com.example.fucknotes.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EditorChromeTest {
    @Test fun lockCyclesFullCompactReadingAndBack() {
        assertEquals(EditorChrome.COMPACT, EditorChrome.FULL.next())
        assertEquals(EditorChrome.READING, EditorChrome.COMPACT.next())
        assertEquals(EditorChrome.FULL, EditorChrome.READING.next())
    }

    @Test fun everyStateReachesEveryOtherStateWithinTwoTaps() {
        EditorChrome.entries.forEach { start ->
            val reached = setOf(start.next(), start.next().next())
            assertEquals(EditorChrome.entries.toSet() - start, reached)
        }
    }

    @Test fun lockLabelsAreDistinctSoTestsAndScreenReadersCanTellStatesApart() {
        val labels = EditorChrome.entries.map { it.lockLabel }
        assertEquals(labels.size, labels.toSet().size)
        labels.forEach { assertNotEquals("", it.trim()) }
    }
}
