package com.example.fucknotes.data.ink

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Hand-drawn outlines for the Shapes tool.
 *
 * One outline with gently bowed edges and soft box corners. Each edge is drawn once; offset
 * return passes would make thin shapes look like two separate lines. Shared endpoints and
 * closed loops keep joins seamless. Each shape remains a single editable Ink stroke.
 *
 * The unevenness comes from a generator seeded by where the drag *began*, and every pass takes a
 * fixed number of values from it. So the same drag always gives the same drawing, and while the
 * far corner is still being dragged the wobble stretches with the shape instead of reshuffling
 * on every pointer move.
 */
object SketchyShapes {
    /** Distance between samples along the path, in page units. */
    private const val STEP = 4f

    fun points(shape: InkShape, start: Offset, end: Offset): List<Offset> {
        val delta = end - start
        val length = delta.getDistance()
        if (length < 2f || !length.isFinite()) return emptyList()
        val hand = Hand(seedFor(shape, start))
        val out = ArrayList<Offset>()
        when (shape) {
            InkShape.LINE -> {
                hand.pass(start, end, out)
            }
            InkShape.ARROW -> {
                val clean = ShapeRecognizer.dragPoints(InkShape.ARROW, start, end)
                hand.pass(start, end, out)
                // An arrow branches at its tip. Retrace this wing's exact samples to return
                // to the tip, so the continuous stroke never creates a second offset line.
                val wing = ArrayList<Offset>()
                hand.pass(end, clean[2], wing)
                out.addAll(wing.drop(1))
                out.addAll(wing.asReversed().drop(1))
                hand.pass(end, clean[4], out)
            }
            InkShape.RECTANGLE -> {
                if (abs(delta.x) < 2f || abs(delta.y) < 2f) return emptyList()
                box(min(start.x, end.x), min(start.y, end.y), max(start.x, end.x), max(start.y, end.y), hand, out)
            }
            InkShape.CIRCLE -> {
                if (abs(delta.x) < 2f || abs(delta.y) < 2f) return emptyList()
                ellipse((start + end) / 2f, abs(delta.x) / 2f, abs(delta.y) / 2f, hand, out)
            }
        }
        return out
    }

    private fun seedFor(shape: InkShape, start: Offset): Int =
        (start.x * 73f).toInt() * 31 + (start.y * 151f).toInt() * 17 + shape.ordinal * 7919

    /** The unsteady hand: values in -1..1, always the same for the same seed. */
    private class Hand(seed: Int) {
        private val random = Random(seed)
        fun next(): Float = random.nextFloat() * 2f - 1f

        /**
         * One pass from [a] to [b], with exact endpoints and a slight bow between them.
         * Takes exactly two values, whatever the length.
         */
        fun pass(a: Offset, b: Offset, out: MutableList<Offset>) {
            val r = FloatArray(2) { next() }
            val length = (b - a).getDistance()
            if (length < 0.01f) { out += a; return }
            val unit = (b - a) / length
            val normal = Offset(-unit.y, unit.x)
            val bow = (length * 0.010f).coerceIn(0.2f, 1.6f) * r[0]
            val waver = (length * 0.004f).coerceIn(0.1f, 0.6f) * r[1]
            val steps = max(2, ceil(length / STEP).toInt())
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                out += a + (b - a) * t +
                    normal * (bow * 4f * t * (1f - t) + waver * sin(2f * PI.toFloat() * t))
            }
        }

        /** A quarter turn that meets each adjacent edge exactly. One value. */
        fun corner(centre: Offset, radius: Float, fromDegrees: Float, out: MutableList<Offset>) {
            val swell = 0.06f * next()
            val steps = max(3, ceil(radius * (PI / 2).toFloat() / STEP).toInt())
            for (i in 1..steps) {
                val t = i / steps.toFloat()
                val r = radius * (1f + swell * sin(PI.toFloat() * t))
                val angle = Math.toRadians((fromDegrees + 90f * t).toDouble())
                out += centre + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * r
            }
        }
    }

    /** One closed box with soft corners, clockwise from the top edge. */
    private fun box(left: Float, top: Float, right: Float, bottom: Float, hand: Hand, out: MutableList<Offset>) {
        val radius = min(0.22f * min(right - left, bottom - top), 14f)
        hand.pass(Offset(left + radius, top), Offset(right - radius, top), out)
        hand.corner(Offset(right - radius, top + radius), radius, -90f, out)
        hand.pass(Offset(right, top + radius), Offset(right, bottom - radius), out)
        hand.corner(Offset(right - radius, bottom - radius), radius, 0f, out)
        hand.pass(Offset(right - radius, bottom), Offset(left + radius, bottom), out)
        hand.corner(Offset(left + radius, bottom - radius), radius, 90f, out)
        hand.pass(Offset(left, bottom - radius), Offset(left, top + radius), out)
        hand.corner(Offset(left + radius, top + radius), radius, 180f, out)
        out += out.first()
    }

    /**
     * One closed ellipse. Periodic radius and centre variations meet at the starting point
     * after one turn, without an overlapping tail or a jump at the join.
     */
    private fun ellipse(centre: Offset, rx: Float, ry: Float, hand: Hand, out: MutableList<Offset>) {
        val r = FloatArray(5) { hand.next() }
        // Ramanujan's approximation, for an even sample spacing whatever the proportions.
        val perimeter = (PI * (3f * (rx + ry) - sqrt((3f * rx + ry) * (rx + 3f * ry)))).toFloat()
        val turn = 2f * PI.toFloat()
        val steps = max(16, ceil(perimeter / STEP).toInt())
        val begin = -turn / 4f + 0.3f * r[0]
        val drift = min(min(rx, ry) * 0.012f, 1f)
        for (i in 0 until steps) {
            val turned = turn * i / steps
            val angle = begin + turned
            val swell = 1f + 0.012f * sin(3f * turned + PI.toFloat() * r[1]) + 0.008f * sin(2f * turned + PI.toFloat() * r[2])
            out += centre +
                Offset(rx * swell * cos(angle), ry * swell * sin(angle)) +
                Offset(sin(turned + PI.toFloat() * r[3]), cos(turned + PI.toFloat() * r[4])) * drift
        }
        out += out.first()
    }
}
