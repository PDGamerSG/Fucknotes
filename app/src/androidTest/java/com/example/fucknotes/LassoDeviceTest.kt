package com.example.fucknotes

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fucknotes.data.ink.InkCodec
import com.example.fucknotes.data.ink.InkStroke
import com.example.fucknotes.data.ink.PageSpec
import com.example.fucknotes.data.model.FingerInputMode
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.ui.editor.ink.InkCamera
import com.example.fucknotes.ui.editor.ink.InkSurface
import com.example.fucknotes.ui.editor.ink.Lasso
import com.example.fucknotes.ui.editor.ink.LassoState
import com.example.fucknotes.ui.theme.FucknotesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Circle a stroke with the stylus and it is selected; drag inside the box and the move is
 * reported in world units; the moved stroke keeps every input, shifted.
 */
@RunWith(AndroidJUnit4::class)
class LassoDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun line(x0: Float, y0: Float, x1: Float, y1: Float): InkStroke {
        val brush = InkCodec.brushFor(InkTool.PEN, 0xFF171B23.toInt(), 3f, 1f)
        val inputs = MutableStrokeInputBatch()
        repeat(10) { i ->
            val t = i / 9f
            inputs.add(InputToolType.STYLUS, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, i * 16L)
        }
        return InkStroke(Stroke(brush, inputs), InkTool.PEN)
    }

    private fun stylus(points: List<Offset>) {
        val start = SystemClock.uptimeMillis()
        points.forEachIndexed { i, p ->
            val action = when (i) { 0 -> MotionEvent.ACTION_DOWN; points.lastIndex -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
            val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
            val coords = MotionEvent.PointerCoords().apply { x = p.x; y = p.y; pressure = if (action == MotionEvent.ACTION_UP) 0f else 0.6f }
            val event = MotionEvent.obtain(start, start + i * 12L, action, 1, arrayOf(properties), arrayOf(coords),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
            try { compose.runOnUiThread { compose.activity.window.decorView.dispatchTouchEvent(event) } } finally { event.recycle() }
        }
        compose.waitForIdle()
    }

    @Test fun circleSelectsAndDragMoves() {
        val camera = InkCamera(1f) // scale 1 and no pan: screen units are world units
        val lasso = LassoState()
        val strokes = listOf(line(300f, 300f, 400f, 300f), line(900f, 900f, 1000f, 900f))
        var moved: Triple<Set<Int>, Float, Float>? = null
        compose.setContent {
            FucknotesTheme {
                InkSurface(
                    strokes = { strokes }, camera = camera, tool = InkTool.LASSO,
                    colorArgb = 0xFF171B23.toInt(), widthDp = 3f, enabled = true,
                    fingerInputMode = FingerInputMode.HAND, zoomLocked = false, notebook = false,
                    paperStyle = PaperStyle.PLAIN, pageSpec = PageSpec.Default,
                    ruleColor = Color.Gray, ruleSpacingDp = 24f, deskColor = Color.Gray,
                    onStrokesFinished = {}, onErase = {}, onToggleEraser = {},
                    lasso = lasso, onMoveStrokes = { s, dx, dy -> moved = Triple(s, dx, dy) },
                    modifier = Modifier.fillMaxSize().testTag("page"),
                )
            }
        }
        compose.onNodeWithTag("page").assertExists()

        // A loop around the first line only.
        val loop = (0..24).map { i ->
            val a = i / 24f * 2 * Math.PI
            Offset(350f + 120f * Math.cos(a).toFloat(), 300f + 80f * Math.sin(a).toFloat())
        }
        stylus(loop)
        assertEquals(setOf(0), lasso.selection)
        assertTrue(lasso.loop.isEmpty())

        // Drag from inside the box 60 right, 40 down.
        stylus(listOf(Offset(350f, 300f), Offset(370f, 310f), Offset(390f, 330f), Offset(410f, 340f), Offset(410f, 340f)))
        val (indices, dx, dy) = requireNotNull(moved)
        assertEquals(setOf(0), indices)
        assertEquals(60f, dx, 1f)
        assertEquals(40f, dy, 1f)
        assertEquals(Offset.Zero, lasso.drag)

        // A tap away from the selection lets it go.
        stylus(listOf(Offset(1500f, 1500f), Offset(1500f, 1500f)))
        assertTrue(lasso.selection.isEmpty())
    }

    @Test fun translateShiftsEveryInputAndKeepsTheBrush() {
        val original = line(10f, 20f, 110f, 20f)
        val moved = Lasso.translate(original, 5f, -7f)
        assertEquals(original.stroke.inputs.size, moved.stroke.inputs.size)
        assertEquals(original.stroke.brush, moved.stroke.brush)
        val a = StrokeInput(); val b = StrokeInput()
        for (i in 0 until original.stroke.inputs.size) {
            original.stroke.inputs.populate(i, a); moved.stroke.inputs.populate(i, b)
            assertEquals(a.x + 5f, b.x, 0.001f)
            assertEquals(a.y - 7f, b.y, 0.001f)
            assertEquals(a.elapsedTimeMillis, b.elapsedTimeMillis)
        }
        assertEquals(setOf(0), Lasso.select(listOf(Offset(0f, 0f), Offset(200f, 0f), Offset(200f, 40f), Offset(0f, 40f)), listOf(original)))
    }
}
