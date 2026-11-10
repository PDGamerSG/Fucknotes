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

internal data class TemporaryTrail(val points: List<Offset>, val finishedAtMs: Long) {
    fun alphaAt(nowMs: Long): Float =
        (1f - ((nowMs - finishedAtMs).coerceAtLeast(0L) - 1500L).coerceAtLeast(0L) / 1000f).coerceIn(0f, 1f)
}

internal class DrawingOverlayState {
    var shapePoints by mutableStateOf(emptyList<Offset>())
    var shapeColor by mutableStateOf(Color.Black)
    var shapeWidth by mutableFloatStateOf(2.5f)
    var laserPoints by mutableStateOf(emptyList<Offset>())
    val trails = mutableStateListOf<TemporaryTrail>()

    fun finishLaser(nowMs: Long) {
        if (laserPoints.isNotEmpty()) trails += TemporaryTrail(laserPoints, nowMs)
        laserPoints = emptyList()
    }
}

/** Reads animation time only in drawing; the native wet-ink surface never rebuilds per frame. */
@Composable
internal fun DrawingToolOverlay(state: DrawingOverlayState, camera: InkCamera) {
    var nowMs by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(state.trails.isNotEmpty()) {
        while (state.trails.isNotEmpty()) {
            withFrameMillis {
                nowMs = SystemClock.uptimeMillis()
                state.trails.removeAll { trail -> trail.alphaAt(nowMs) <= 0f }
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
        fun laser(points: List<Offset>, alpha: Float) {
            drawPathPoints(points, Color(0xFFFF303F).copy(alpha = 0.26f * alpha), 18f * camera.scale)
            drawPathPoints(points, Color(0xFFFF303F).copy(alpha = 0.9f * alpha), 4f * camera.scale)
        }
        state.trails.forEach { laser(it.points, it.alphaAt(nowMs)) }
        laser(state.laserPoints, 1f)
    }
}
