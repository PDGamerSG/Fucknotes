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

    @Test fun finishingAnotherTrailDoesNotRestartTheFirstTimer() {
        val first = TemporaryTrail(listOf(Offset.Zero), 0L)
        val second = TemporaryTrail(listOf(Offset(1f, 2f)), 2000L)
        assertEquals(0f, first.alphaAt(3000L), 0f)
        assertEquals(1f, second.alphaAt(3000L), 0f)
    }
}
