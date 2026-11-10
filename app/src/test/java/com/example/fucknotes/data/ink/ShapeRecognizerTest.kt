package com.example.fucknotes.data.ink

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShapeRecognizerTest {
    @Test fun recognizesAShortStraightLine() {
        assertEquals(
            InkShape.LINE,
            ShapeRecognizer.recognize(listOf(Offset(0f, 0f), Offset(80f, 2f))),
        )
    }

    @Test fun recognizesAConfidentRectangle() {
        val points = listOf(
            Offset(10f, 10f), Offset(110f, 10f), Offset(110f, 70f),
            Offset(10f, 70f), Offset(10f, 10f),
        )

        assertEquals(InkShape.RECTANGLE, ShapeRecognizer.recognize(points))
    }

    @Test fun recognizesAConfidentCircle() {
        val points = (0..32).map { i ->
            val angle = (i / 32f) * (Math.PI * 2.0)
            Offset(60f + 40f * kotlin.math.cos(angle).toFloat(), 60f + 40f * kotlin.math.sin(angle).toFloat())
        }

        assertEquals(InkShape.CIRCLE, ShapeRecognizer.recognize(points))
    }

    @Test fun recognizesAnArrowWithTwoHeadWings() {
        val points = listOf(
            Offset(0f, 0f), Offset(45f, 0f), Offset(100f, 0f),
            Offset(82f, -16f), Offset(100f, 0f), Offset(82f, 16f),
        )

        assertEquals(InkShape.ARROW, ShapeRecognizer.recognize(points))
    }

    @Test fun recognizesAnArrowWhenItsHeadHasMoreSamplesThanItsShaft() {
        val points = listOf(Offset(0f, 0f), Offset(45f, 0f), Offset(100f, 0f)) +
            (1..10).map { Offset(100f - it * 1.8f, -it * 1.6f) } +
            (1..10).map { Offset(82f + it * 1.8f, -16f + it * 1.6f) } +
            (1..10).map { Offset(100f - it * 1.8f, it * 1.6f) }

        assertEquals(InkShape.ARROW, ShapeRecognizer.recognize(points))
    }

    @Test fun leavesAmbiguousHandwritingAlone() {
        val points = listOf(Offset(0f, 0f), Offset(18f, 30f), Offset(6f, 58f), Offset(40f, 92f))

        assertNull(ShapeRecognizer.recognize(points))
    }

    @Test fun recognizesARoughRectangleWithRoundedCorners() {
        val points = listOf(
            Offset(18f, 10f), Offset(55f, 9f), Offset(105f, 11f),
            Offset(113f, 13f), Offset(118f, 18f), Offset(120f, 25f),
            Offset(121f, 45f), Offset(119f, 68f), Offset(117f, 74f),
            Offset(112f, 78f), Offset(105f, 80f), Offset(65f, 81f),
            Offset(25f, 79f), Offset(18f, 77f), Offset(13f, 72f),
            Offset(10f, 65f), Offset(9f, 45f), Offset(11f, 22f),
            Offset(13f, 16f), Offset(18f, 10f),
        )

        assertEquals(InkShape.RECTANGLE, ShapeRecognizer.recognize(points))
    }

    @Test fun doesNotTurnADiamondIntoAnAxisAlignedBox() {
        val points = listOf(
            Offset(0f, 50f), Offset(50f, 0f), Offset(100f, 50f),
            Offset(50f, 100f), Offset(0f, 50f),
        )

        assertNull(ShapeRecognizer.recognize(points))
    }

    @Test fun doesNotTurnATriangleStartingMidEdgeIntoABox() {
        val points = listOf(
            Offset(25f, 50f), Offset(50f, 0f), Offset(100f, 100f),
            Offset(0f, 100f), Offset(25f, 50f),
        )

        assertNull(ShapeRecognizer.recognize(points))
    }

    @Test fun doesNotTurnARetracedScribbleIntoAClosedShape() {
        val points = listOf(
            Offset(0f, 0f), Offset(100f, 100f), Offset(0f, 0f),
            Offset(100f, 100f), Offset(0f, 0f),
        )

        assertNull(ShapeRecognizer.recognize(points))
    }
}
