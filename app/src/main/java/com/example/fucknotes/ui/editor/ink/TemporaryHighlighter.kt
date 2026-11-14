package com.example.fucknotes.ui.editor.ink

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

private val LaserRed = Color(0xFFFF2D3D)
private val LaserCore = Color(0xFFFFF3F1)

internal data class TemporaryTrail(val points: List<Offset>, val finishedAtMs: Long) {
    fun alphaAt(nowMs: Long): Float =
        (1f - ((nowMs - finishedAtMs).coerceAtLeast(0L) - HOLD_MS).coerceAtLeast(0L) / FADE_MS.toFloat()).coerceIn(0f, 1f)

    companion object {
        /** Give the finished writing a moment to be read before it starts fading. */
        const val HOLD_MS = 1500L
        const val FADE_MS = 1000L
    }
}

internal class DrawingOverlayState {
    var shapePoints by mutableStateOf(emptyList<Offset>())
    var shapeColor by mutableStateOf(Color.Black)
    var shapeWidth by mutableFloatStateOf(2.5f)
    var laserPoints by mutableStateOf(emptyList<Offset>())
    val trails = mutableStateListOf<TemporaryTrail>()

    fun finishLaser(nowMs: Long, cancelled: Boolean = false) {
        if (laserPoints.isEmpty()) return
        // Letters belong to one writing session. Wait for the most recent pen lift rather
        // than erasing the first letters while the user is still finishing the word.
        for (index in trails.indices) trails[index] = trails[index].copy(finishedAtMs = nowMs)
        if (!cancelled) trails += TemporaryTrail(laserPoints, nowMs)
        laserPoints = emptyList()
    }
}

/** Reads animation time only in drawing; the native wet-ink surface never rebuilds per frame. */
@Composable
internal fun DrawingToolOverlay(state: DrawingOverlayState, camera: InkCamera) {
    var nowMs by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val laserActive by remember(state) { derivedStateOf { state.laserPoints.isNotEmpty() } }
    LaunchedEffect(state.trails.isNotEmpty(), laserActive) {
        while (!laserActive && state.trails.isNotEmpty()) {
            withFrameMillis {
                nowMs = SystemClock.uptimeMillis()
                if (state.laserPoints.isEmpty()) state.trails.removeAll { trail -> trail.alphaAt(nowMs) <= 0f }
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        fun drawPathPoints(points: List<Offset>, color: Color, width: Float) {
            if (points.isEmpty()) return
            fun screen(point: Offset) = Offset(point.x * camera.scale + camera.panX, point.y * camera.scale + camera.panY)
            val first = screen(points.first())
            if (points.size == 1) {
                drawCircle(color, width / 2f, first)
                return
            }
            val path = Path().apply {
                moveTo(first.x, first.y)
                points.drop(1).forEach { point -> val p = screen(point); lineTo(p.x, p.y) }
            }
            drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        drawPathPoints(state.shapePoints, state.shapeColor, state.shapeWidth * camera.scale)
        // A lit tube: a wide soft glow, a red body, and a near-white core down its middle,
        // so it reads as light on the page rather than as one more red pen.
        fun laser(points: List<Offset>, alpha: Float) {
            drawPathPoints(points, LaserRed.copy(alpha = 0.10f * alpha), 15f * camera.scale)
            drawPathPoints(points, LaserRed.copy(alpha = 0.20f * alpha), 9.5f * camera.scale)
            drawPathPoints(points, LaserRed.copy(alpha = 0.95f * alpha), 5.2f * camera.scale)
            drawPathPoints(points, LaserCore.copy(alpha = alpha), 2.2f * camera.scale)
        }
        state.trails.forEach { laser(it.points, if (laserActive) 1f else it.alphaAt(nowMs)) }
        laser(state.laserPoints, 1f)
    }
}
