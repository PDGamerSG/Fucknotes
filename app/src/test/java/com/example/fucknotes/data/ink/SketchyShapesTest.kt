package com.example.fucknotes.data.ink

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/** Hand-drawn shapes follow one outline, with no second offset pass. */
class SketchyShapesTest {
    private val start = Offset(100f, 200f)
    private val end = Offset(340f, 360f)

    private fun length(points: List<Offset>) = points.zipWithNext().sumOf { (a, b) -> (b - a).getDistance().toDouble() }.toFloat()

    @Test fun theSameDragGivesTheSameDrawing() {
        InkShape.entries.forEach { shape ->
            assertEquals(SketchyShapes.points(shape, start, end), SketchyShapes.points(shape, start, end))
        }
    }

    @Test fun aLineReachesTheEndWithoutDoublingBack() {
        val points = SketchyShapes.points(InkShape.LINE, start, end)
        val direct = (end - start).getDistance()
        assertTrue("one pass, was ${length(points) / direct}", length(points) in direct * 0.99f..direct * 1.05f)
        val unit = (end - start) / direct
        points.forEach { p ->
            val off = abs((p.x - start.x) * unit.y - (p.y - start.y) * unit.x)
            assertTrue("a point strays $off units from the line", off <= 5f)
        }
        assertTrue((points.first() - start).getDistance() <= 3f)
        assertTrue("the line must end where the pen lifted", (points.last() - end).getDistance() <= 3f)
        points.zipWithNext().forEach { (a, b) ->
            val forward = (b.x - a.x) * unit.x + (b.y - a.y) * unit.y
            assertTrue("the line doubles back", forward >= -0.01f)
        }
    }

    @Test fun aBoxClosesOneOutlineWithSoftCornersInsideItsFrame() {
        val points = SketchyShapes.points(InkShape.RECTANGLE, start, end)
        val perimeter = 2 * (240f + 160f)
        assertTrue("one lap, was ${length(points) / perimeter}", length(points) in perimeter * 0.9f..perimeter * 1.05f)
        assertEquals("the outline must close", points.first(), points.last())
        points.forEach { p ->
            assertTrue("outside the frame: $p", p.x in 96f..344f && p.y in 196f..364f)
            val toEdge = minOf(abs(p.x - 100f), abs(p.x - 340f), abs(p.y - 200f), abs(p.y - 360f))
            assertTrue("a point is $toEdge units inside the box", toEdge <= 12f)
        }
        // Rounded: nothing sits in the very corner, and every edge is reached at its middle.
        assertTrue(points.none { (it - Offset(340f, 200f)).getDistance() < 3f })
        listOf(Offset(220f, 200f), Offset(340f, 280f), Offset(220f, 360f), Offset(100f, 280f)).forEach { mid ->
            assertTrue("no ink near the middle of an edge at $mid", points.any { (it - mid).getDistance() <= 5f })
        }
    }

    @Test fun aCircleClosesOneLapCloseToTheEllipseAsked() {
        val points = SketchyShapes.points(InkShape.CIRCLE, start, end)
        val centre = Offset(220f, 280f)
        points.forEach { p ->
            val r = hypot((p.x - centre.x) / 120f, (p.y - centre.y) / 80f)
            assertTrue("radius off by ${abs(r - 1f)}", abs(r - 1f) <= 0.06f)
        }
        // Ramanujan's perimeter for a 120 x 80 ellipse is about 634.
        assertTrue("one lap, was ${length(points) / 634f}", length(points) in 634f * 0.94f..634f * 1.08f)
        assertEquals("the outline must close without an extra overlapping tail", points.first(), points.last())
    }

    @Test fun anArrowHasOneShaftAndBothWings() {
        val points = SketchyShapes.points(InkShape.ARROW, start, end)
        val clean = ShapeRecognizer.dragPoints(InkShape.ARROW, start, end)
        val (wingA, wingB) = clean[2] to clean[4]
        assertTrue((points.first() - start).getDistance() <= 3f)
        assertTrue((points.last() - wingB).getDistance() <= 4f)
        assertTrue("the tip is reached", points.any { (it - end).getDistance() <= 3f })
        assertTrue("the tail is reached", points.any { (it - start).getDistance() <= 3f })
        val shaft = (end - start).getDistance()
        assertTrue("both arrow wings must be present", points.any { (it - wingA).getDistance() <= 3f })
        // One shaft; one wing is retraced exactly to connect the other in a single Ink stroke.
        val expected = shaft + 3f * (wingA - end).getDistance()
        assertTrue("the shaft must not be retraced with a second line", abs(length(points) - expected) <= 8f)
    }

    @Test fun thePenNeverJumps() {
        InkShape.entries.forEach { shape ->
            SketchyShapes.points(shape, start, end).zipWithNext().forEach { (a, b) ->
                assertTrue("$shape jumps ${(b - a).getDistance()} units", (b - a).getDistance() <= 8f)
            }
        }
    }

    @Test fun draggingFurtherDoesNotReshuffleTheWobble() {
        // The preview is redrawn on every pointer move; the unevenness must stretch with the
        // shape, not jump about. Three drags a unit apart: where the path begins must move
        // evenly from one to the next, which it cannot if the wobble is dealt afresh each time.
        InkShape.entries.forEach { shape ->
            val a = SketchyShapes.points(shape, start, end).first()
            val b = SketchyShapes.points(shape, start, end + Offset(1f, 1f)).first()
            val c = SketchyShapes.points(shape, start, end + Offset(2f, 2f)).first()
            val jolt = (a - b * 2f + c).getDistance()
            assertTrue("$shape flickers by $jolt", jolt <= 0.2f)
        }
    }

    @Test fun aDragTooSmallToBeAShapeIsNothing() {
        InkShape.entries.forEach { assertTrue(SketchyShapes.points(it, start, start + Offset(0.5f, 0.5f)).isEmpty()) }
        assertTrue(SketchyShapes.points(InkShape.RECTANGLE, start, start + Offset(80f, 1f)).isEmpty())
    }

    @Test fun anyDragDirectionGivesTheSameFrame() {
        val reversed = SketchyShapes.points(InkShape.RECTANGLE, end, start)
        assertTrue(reversed.all { it.x in 96f..344f && it.y in 196f..364f })
    }

    @Test fun cleanLinesAreStillExact() {
        assertEquals(listOf(start, end), ShapeRecognizer.dragPoints(InkShape.LINE, start, end, sketchy = false))
        assertEquals(SketchyShapes.points(InkShape.LINE, start, end), ShapeRecognizer.dragPoints(InkShape.LINE, start, end, sketchy = true))
    }
}
