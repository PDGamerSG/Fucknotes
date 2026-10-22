package com.example.fucknotes.ui.editor.ink

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import com.example.fucknotes.data.ink.InkStroke

/**
 * Circle a part of the page, then move it.
 *
 * The loop the user draws is a polygon in world units. A stroke is inside when most of its input
 * points are — so a word circled a little carelessly still comes along whole, and a long
 * underline that only dips into the loop stays put. The selection is a set of indices into the
 * stroke list; moving keeps the list order, so the same indices still name the same strokes after
 * a move, and the loop can be dragged again and again.
 */
object Lasso {
    /** Share of a stroke's points that must fall inside the loop for the stroke to be selected. */
    const val INSIDE_FRACTION = 0.5f

    /** A loop shorter than this (world units) is a tap: it clears the selection instead. */
    const val MIN_LOOP_EXTENT = 6f

    /** Room around the selection box, in world units, where a drag still counts as a move. */
    const val GRAB_MARGIN = 14f

    /** Even-odd test: is ([x], [y]) inside the polygon? Open polygons are treated as closed. */
    fun contains(polygon: List<Offset>, x: Float, y: Float): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            if ((a.y > y) != (b.y > y)) {
                val crossX = (b.x - a.x) * (y - a.y) / (b.y - a.y) + a.x
                if (x < crossX) inside = !inside
            }
            j = i
        }
        return inside
    }

    /** Fraction of the points (x0, y0, x1, y1, …) inside the polygon; 0 for no points. */
    fun fractionInside(polygon: List<Offset>, points: FloatArray): Float {
        val count = points.size / 2
        if (count == 0) return 0f
        var inside = 0
        for (i in 0 until count) if (contains(polygon, points[2 * i], points[2 * i + 1])) inside++
        return inside.toFloat() / count
    }

    fun bounds(polygon: List<Offset>): Rect? {
        if (polygon.isEmpty()) return null
        var left = Float.MAX_VALUE; var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE; var bottom = -Float.MAX_VALUE
        polygon.forEach { p ->
            if (p.x < left) left = p.x; if (p.x > right) right = p.x
            if (p.y < top) top = p.y; if (p.y > bottom) bottom = p.y
        }
        return Rect(left, top, right, bottom)
    }

    /** The strokes the loop takes, by index. */
    fun select(polygon: List<Offset>, strokes: List<InkStroke>): Set<Int> {
        val loop = bounds(polygon) ?: return emptySet()
        if (loop.width < MIN_LOOP_EXTENT && loop.height < MIN_LOOP_EXTENT) return emptySet()
        val scratch = StrokeInput()
        val result = mutableSetOf<Int>()
        strokes.forEachIndexed { index, inkStroke ->
            val box = inkStroke.stroke.shape.computeBoundingBox()
            if (box != null && (box.xMax < loop.left || box.xMin > loop.right || box.yMax < loop.top || box.yMin > loop.bottom)) return@forEachIndexed
            val inputs = inkStroke.stroke.inputs
            if (inputs.size == 0) return@forEachIndexed
            var inside = 0
            for (i in 0 until inputs.size) {
                inputs.populate(i, scratch)
                if (contains(polygon, scratch.x, scratch.y)) inside++
            }
            if (inside.toFloat() / inputs.size >= INSIDE_FRACTION) result += index
        }
        return result
    }

    /** The box around the selected strokes, in world units; null when nothing is selected. */
    fun selectionBounds(strokes: List<InkStroke>, selected: Set<Int>): Rect? {
        var rect: Rect? = null
        selected.forEach { index ->
            val box = strokes.getOrNull(index)?.stroke?.shape?.computeBoundingBox() ?: return@forEach
            val r = Rect(box.xMin, box.yMin, box.xMax, box.yMax)
            rect = rect?.let { Rect(minOf(it.left, r.left), minOf(it.top, r.top), maxOf(it.right, r.right), maxOf(it.bottom, r.bottom)) } ?: r
        }
        return rect
    }

    /** The same stroke, every input shifted by ([dx], [dy]); brush, timing and pressure kept. */
    fun translate(stroke: Stroke, dx: Float, dy: Float): Stroke {
        val inputs = stroke.inputs
        val batch = MutableStrokeInputBatch()
        val scratch = StrokeInput()
        val moved = StrokeInput()
        for (i in 0 until inputs.size) {
            inputs.populate(i, scratch)
            moved.update(
                scratch.x + dx, scratch.y + dy, scratch.elapsedTimeMillis, scratch.toolType,
                scratch.strokeUnitLengthCm, scratch.pressure, scratch.tiltRadians, scratch.orientationRadians,
            )
            batch.add(moved)
        }
        return Stroke(stroke.brush, batch)
    }

    fun translate(inkStroke: InkStroke, dx: Float, dy: Float): InkStroke =
        inkStroke.copy(stroke = translate(inkStroke.stroke, dx, dy))
}

/**
 * What the lasso is doing right now, shared between the ink surface (which drives it) and the
 * editor overlay (which shows the Delete / Duplicate buttons next to it). Everything is in world
 * units so a pan or zoom never moves the selection off its strokes.
 */
class LassoState {
    /** Indices into the stroke list. */
    var selection: Set<Int> by mutableStateOf(emptySet())

    /** The loop being drawn, until the pointer lifts. */
    var loop: List<Offset> by mutableStateOf(emptyList())

    /** How far the selection has been dragged so far, before the move is committed. */
    var drag: Offset by mutableStateOf(Offset.Zero)

    val active: Boolean get() = selection.isNotEmpty()

    fun clear() {
        selection = emptySet()
        loop = emptyList()
        drag = Offset.Zero
    }
}
