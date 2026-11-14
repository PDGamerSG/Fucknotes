package com.example.fucknotes.data.ink

import androidx.compose.ui.geometry.Offset
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Small, optional tags kept beside a vector stroke in the note payload. */
enum class InkShape {
    LINE,
    ARROW,
    RECTANGLE,
    CIRCLE,
}

/**
 * Conservative recognition for the shapes people commonly draw in notes.
 *
 * A stroke is only replaced when its geometry has a strong signal. This deliberately favours
 * leaving a rough mark alone over changing handwriting that merely happens to have four corners.
 */
object ShapeRecognizer {
    /** The same geometry drives the live drag preview and the saved vector stroke. */
    fun dragPoints(shape: InkShape, start: Offset, end: Offset, sketchy: Boolean = false): List<Offset> {
        if (sketchy) return SketchyShapes.points(shape, start, end)
        val delta = end - start
        val length = delta.getDistance()
        if (length < 2f || !length.isFinite()) return emptyList()
        return when (shape) {
            InkShape.LINE -> listOf(start, end)
            InkShape.ARROW -> {
                val direction = delta / length
                val wing = length.times(0.22f).coerceAtMost(28f)
                val base = end - direction * wing
                val side = Offset(-direction.y, direction.x) * (wing * 0.55f)
                listOf(start, end, base + side, end, base - side)
            }
            InkShape.RECTANGLE, InkShape.CIRCLE -> {
                if (kotlin.math.abs(delta.x) < 2f || kotlin.math.abs(delta.y) < 2f) emptyList()
                else normalizedPoints(listOf(start, end), shape)
            }
        }
    }

    private const val MIN_POINTS = 4
    private const val MIN_EXTENT = 8f
    private const val CLOSE_FRACTION = 0.24f
    private const val RECTANGLE_PERIMETER_TOLERANCE = 0.30f
    private const val CIRCLE_RADIAL_VARIATION = 0.22f
    private const val STRAIGHTNESS = 0.93f

    fun recognize(points: List<Offset>): InkShape? {
        val clean = simplify(points)
        if (clean.size < 2) return null
        val box = bounds(clean)
        val width = box.right - box.left
        val height = box.bottom - box.top
        val diagonal = hypot(width, height)
        if (max(width, height) < MIN_EXTENT) return null

        val path = pathLength(clean)
        if (path <= 0f) return null

        val closed = clean.size >= MIN_POINTS &&
            (clean.first() - clean.last()).getDistance() <= max(6f, diagonal * CLOSE_FRACTION)
        // A loop must enclose most of its bounds. Retraced scribbles, triangles and diamonds
        // can have the same length as a box or circle but must keep their original geometry.
        if (closed && enclosedArea(clean) >= width * height * 0.62f) {
            val expectedPerimeter = 2f * (width + height)
            val rectangleFit = expectedPerimeter > 0f &&
                kotlin.math.abs(path - expectedPerimeter) / expectedPerimeter <= RECTANGLE_PERIMETER_TOLERANCE &&
                followsBoxEdges(clean, box)
            if (rectangleFit) return InkShape.RECTANGLE

            val aspect = min(width, height) / max(width, height)
            if (aspect >= 0.65f && radialVariation(clean, box) <= CIRCLE_RADIAL_VARIATION) {
                return InkShape.CIRCLE
            }
        }

        if (isArrow(clean)) return InkShape.ARROW
        val straightness = (clean.first() - clean.last()).getDistance() / path
        return InkShape.LINE.takeIf { straightness >= STRAIGHTNESS }
    }

    /** Returns the input locations of an Ink stroke in page/world coordinates. */
    fun points(stroke: Stroke): List<Offset> {
        val input = StrokeInput()
        return buildList(stroke.inputs.size) {
            for (index in 0 until stroke.inputs.size) {
                stroke.inputs.populate(index, input)
                add(Offset(input.x, input.y))
            }
        }
    }

    /** Replaces a confident rough path with a small, clean vector path. */
    fun clean(stroke: Stroke, shape: InkShape): Stroke {
        val rough = points(stroke)
        val normalized = normalizedPoints(rough, shape)
        if (normalized.size < 2) return stroke
        val batch = MutableStrokeInputBatch()
        normalized.forEachIndexed { index, point ->
            batch.add(InputToolType.STYLUS, point.x, point.y, index * 16L)
        }
        return Stroke(brush = stroke.brush, inputs = batch)
    }

    private fun normalizedPoints(points: List<Offset>, shape: InkShape): List<Offset> {
        if (points.isEmpty()) return emptyList()
        val box = bounds(points)
        val centre = Offset((box.left + box.right) / 2f, (box.top + box.bottom) / 2f)
        return when (shape) {
            InkShape.LINE -> listOf(points.first(), points.last())
            InkShape.RECTANGLE -> listOf(
                Offset(box.left, box.top), Offset(box.right, box.top),
                Offset(box.right, box.bottom), Offset(box.left, box.bottom),
                Offset(box.left, box.top),
            )
            InkShape.CIRCLE -> (0..32).map { index ->
                val angle = (index / 32f) * (Math.PI * 2.0)
                Offset(
                    centre.x + (box.right - box.left) / 2f * kotlin.math.cos(angle).toFloat(),
                    centre.y + (box.bottom - box.top) / 2f * kotlin.math.sin(angle).toFloat(),
                )
            }
            InkShape.ARROW -> {
                val tipIndex = points.indices.maxByOrNull { (points[it] - points.first()).getDistance() }
                    ?: points.lastIndex
                val tip = points[tipIndex]
                val axis = tip - points.first()
                fun side(point: Offset): Float = axis.x * (point.y - tip.y) - axis.y * (point.x - tip.x)
                val head = points.drop(tipIndex + 1)
                val firstSide = head.firstOrNull { side(it) != 0f }?.let { side(it) } ?: 0f
                val direction = if (firstSide < 0f) -1f else 1f
                // The next sample is usually only a tiny step away from the tip. Keep the
                // outermost point of each wing so a dense input stream retains the whole head.
                val firstWing = head.maxByOrNull { side(it) * direction }
                    ?.takeIf { side(it) * direction > 0f }
                val lastWing = head.minByOrNull { side(it) * direction }
                    ?.takeIf { side(it) * direction < 0f }
                when {
                    firstWing != null && lastWing != null -> listOf(points.first(), tip, firstWing, tip, lastWing)
                    firstWing != null -> listOf(points.first(), tip, firstWing)
                    else -> listOf(points.first(), tip)
                }
            }
        }
    }

    private fun simplify(points: List<Offset>): List<Offset> {
        if (points.isEmpty()) return emptyList()
        val result = ArrayList<Offset>(points.size)
        points.forEach { point ->
            if (result.isEmpty() || (point - result.last()).getDistance() >= 1f) result += point
        }
        return result
    }

    private data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

    private fun bounds(points: List<Offset>): Bounds {
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        points.forEach {
            left = min(left, it.x); top = min(top, it.y)
            right = max(right, it.x); bottom = max(bottom, it.y)
        }
        return Bounds(left, top, right, bottom)
    }

    private fun pathLength(points: List<Offset>): Float = points.zipWithNext()
        .sumOf { (a, b) -> (b - a).getDistance().toDouble() }
        .toFloat()

    private fun enclosedArea(points: List<Offset>): Float {
        // Translate first so notes far down a notebook do not lose precision in the products.
        val origin = points.first()
        var twiceArea = 0.0
        points.indices.forEach { index ->
            val a = points[index] - origin
            val b = points[(index + 1) % points.size] - origin
            twiceArea += a.x.toDouble() * b.y - b.x.toDouble() * a.y
        }
        return (kotlin.math.abs(twiceArea) / 2.0).toFloat()
    }

    private fun followsBoxEdges(points: List<Offset>, box: Bounds): Boolean {
        val tolerance = min(box.right - box.left, box.bottom - box.top) * 0.14f
        // Inspect the segments too: sparse corners alone can make diagonal edges look box-like.
        return points.zipWithNext().all { (a, b) ->
            (0..4).all { step ->
                val point = a + (b - a) * (step / 4f)
                minOf(point.x - box.left, box.right - point.x, point.y - box.top, box.bottom - point.y) <= tolerance
            }
        }
    }

    private fun radialVariation(points: List<Offset>, box: Bounds): Float {
        val centre = Offset((box.left + box.right) / 2f, (box.top + box.bottom) / 2f)
        val radii = points.map { (it - centre).getDistance() }.filter { it > 0f }
        if (radii.isEmpty()) return 1f
        val mean = radii.average().toFloat()
        if (mean <= 0f) return 1f
        val variance = radii.sumOf { ((it - mean) * (it - mean)).toDouble() } / radii.size
        return sqrt(variance).toFloat() / mean
    }

    private fun isArrow(points: List<Offset>): Boolean {
        if (points.size < 5) return false
        val tail = points.first()
        val tipIndex = points.indices.maxByOrNull { (points[it] - tail).getDistance() } ?: return false
        if (tipIndex <= 0 || tipIndex >= points.lastIndex) return false
        val tailPath = pathLength(points.subList(0, tipIndex + 1))
        val tailDirect = (points[tipIndex] - tail).getDistance()
        if (tailPath <= 0f || tailDirect / tailPath < STRAIGHTNESS) return false
        val wingPath = pathLength(points.subList(tipIndex, points.size))
        if (wingPath < tailDirect * 0.08f || wingPath > tailDirect * 0.90f) return false
        val a = points[tipIndex] - points[max(0, tipIndex - 1)]
        val b = points[min(points.lastIndex, tipIndex + 1)] - points[tipIndex]
        val denominator = a.getDistance() * b.getDistance()
        if (denominator <= 0f) return false
        val angle = Math.toDegrees(acos(((a.x * b.x + a.y * b.y) / denominator).coerceIn(-1f, 1f)).toDouble()).toFloat()
        return angle >= 28f
    }
}
