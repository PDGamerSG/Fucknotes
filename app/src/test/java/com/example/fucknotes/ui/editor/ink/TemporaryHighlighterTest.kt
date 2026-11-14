package com.example.fucknotes.ui.editor.ink

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class TemporaryHighlighterTest {
    @Test fun trailStaysVisibleThenFadesAndExpires() {
        val trail = TemporaryTrail(listOf(Offset.Zero, Offset(100f, 20f)), finishedAtMs = 1000L)
        assertEquals(1f, trail.alphaAt(1000L), 0f)
        assertEquals(1f, trail.alphaAt(2500L), 0f)
        assertEquals(0.5f, trail.alphaAt(3000L), 0.001f)
        assertEquals(0f, trail.alphaAt(3500L), 0f)
        assertEquals(0f, trail.alphaAt(Long.MAX_VALUE), 0f)
    }

    @Test fun finishingAnotherStrokeGivesAllTheWritingTimeToBeRead() {
        val state = DrawingOverlayState()
        state.laserPoints = listOf(Offset.Zero, Offset(20f, 0f))
        state.finishLaser(1000L)
        state.laserPoints = listOf(Offset(1f, 2f), Offset(20f, 2f))
        state.finishLaser(3000L)
        assertEquals(2, state.trails.size)
        state.trails.forEach { trail ->
            assertEquals("Earlier letters must wait for the last pen lift", 1f, trail.alphaAt(4400L), 0f)
            assertEquals(0f, trail.alphaAt(5500L), 0f)
        }
        assertTrue(state.laserPoints.isEmpty())
    }
}
