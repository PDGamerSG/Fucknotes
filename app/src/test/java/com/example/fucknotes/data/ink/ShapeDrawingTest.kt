package com.example.fucknotes.data.ink

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class ShapeDrawingTest {
    @Test fun rectangleClosesAndSupportsDraggingBackwards() {
        val points = ShapeRecognizer.dragPoints(InkShape.RECTANGLE, Offset(100f, 80f), Offset(20f, 30f))
        assertEquals(points.first(), points.last())
        assertEquals(setOf(Offset(20f, 30f), Offset(100f, 30f), Offset(100f, 80f), Offset(20f, 80f)), points.toSet())
    }

    @Test fun arrowRetainsTheTipAndBothWingsInAnyDirection() {
        for (end in listOf(Offset(100f, 50f), Offset(-100f, -50f), Offset(0f, 100f))) {
            val points = ShapeRecognizer.dragPoints(InkShape.ARROW, Offset.Zero, end)
            assertEquals(Offset.Zero, points.first())
            assertEquals(end, points[1])
            assertEquals(end, points[3])
            assertTrue((points[2] - points[4]).getDistance() > 0f)
        }
    }

    @Test fun circleClosesInsideItsDragBoundsAndTapsCreateNothing() {
        val points = ShapeRecognizer.dragPoints(InkShape.CIRCLE, Offset(20f, 30f), Offset(100f, 110f))
        assertEquals(points.first().x, points.last().x, 0.001f)
        assertEquals(points.first().y, points.last().y, 0.001f)
        assertTrue(points.all { it.x in 19.99f..100.01f && it.y in 29.99f..110.01f })
        InkShape.entries.forEach {
            assertTrue(ShapeRecognizer.dragPoints(it, Offset.Zero, Offset.Zero).isEmpty())
        }
    }
}
