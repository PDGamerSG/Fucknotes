package com.example.fucknotes.ui.editor.ink

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The loop geometry on its own: what counts as inside, and how much of a stroke has to be. */
class LassoTest {
    private val square = listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 100f), Offset(0f, 100f))

    @Test fun pointsInsideAndOutsideAConvexLoop() {
        assertTrue(Lasso.contains(square, 50f, 50f))
        assertTrue(Lasso.contains(square, 1f, 99f))
        assertFalse(Lasso.contains(square, 150f, 50f))
        assertFalse(Lasso.contains(square, 50f, -1f))
    }

    @Test fun aLoopIsClosedByItself() {
        // Only three sides drawn; the gap between the last and first point still closes it.
        val open = listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 100f), Offset(0f, 100f), Offset(0f, 60f))
        assertTrue(Lasso.contains(open, 50f, 50f))
    }

    @Test fun aConcaveLoopKeepsItsNotchOutside() {
        // A "C" shape: the mouth on the right is outside.
        val c = listOf(
            Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 30f), Offset(40f, 30f),
            Offset(40f, 70f), Offset(100f, 70f), Offset(100f, 100f), Offset(0f, 100f),
        )
        assertTrue(Lasso.contains(c, 20f, 50f))
        assertFalse(Lasso.contains(c, 80f, 50f))
    }

    @Test fun fractionInsideCountsTheStrokesPoints() {
        val half = floatArrayOf(10f, 10f, 20f, 20f, 200f, 200f, 300f, 300f)
        assertEquals(0.5f, Lasso.fractionInside(square, half), 0f)
        assertEquals(0f, Lasso.fractionInside(square, floatArrayOf()), 0f)
        assertEquals(1f, Lasso.fractionInside(square, floatArrayOf(5f, 5f)), 0f)
    }

    @Test fun boundsAndTooSmallLoops() {
        assertNull(Lasso.bounds(emptyList()))
        val b = Lasso.bounds(square)!!
        assertEquals(0f, b.left, 0f); assertEquals(100f, b.right, 0f); assertEquals(100f, b.bottom, 0f)
        assertTrue(Lasso.select(listOf(Offset(1f, 1f), Offset(2f, 2f), Offset(1f, 3f)), emptyList()).isEmpty())
    }

    @Test fun degeneratePolygonsSelectNothing() {
        assertFalse(Lasso.contains(listOf(Offset(0f, 0f), Offset(10f, 10f)), 5f, 5f))
        assertEquals(0f, Lasso.fractionInside(emptyList(), floatArrayOf(1f, 1f)), 0f)
    }
}
