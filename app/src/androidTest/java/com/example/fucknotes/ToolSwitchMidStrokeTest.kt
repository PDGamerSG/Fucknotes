package com.example.fucknotes

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fucknotes.data.ink.InkCodec
import com.example.fucknotes.data.ink.InkStroke
import com.example.fucknotes.data.ink.PageSpec
import com.example.fucknotes.data.model.FingerInputMode
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.ui.editor.ink.InkCamera
import com.example.fucknotes.ui.editor.ink.InkSurface
import com.example.fucknotes.ui.theme.FucknotesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A pen button pressed mid-stroke must not lose the stroke: the gesture finishes with the tool it
 * started with, and the new tool takes the next one.
 */
@RunWith(AndroidJUnit4::class)
class ToolSwitchMidStrokeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun send(action: Int, x: Float, y: Float, start: Long, at: Long) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        val coords = MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = if (action == MotionEvent.ACTION_UP) 0f else 0.6f }
        val event = MotionEvent.obtain(start, at, action, 1, arrayOf(properties), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
        try {
            // Ink stamps strokes with real time; an event from the future is not what a pen sends.
            SystemClock.sleep((at - SystemClock.uptimeMillis()).coerceAtLeast(0L))
            compose.runOnUiThread { compose.activity.window.decorView.dispatchTouchEvent(event) }
        } finally { event.recycle() }
    }

    @Test fun switchingToTheEraserMidStrokeStillCommitsTheStroke() {
        var tool by mutableStateOf(InkTool.PEN)
        var strokes by mutableStateOf(emptyList<InkStroke>())
        var erased = 0
        val camera = InkCamera(1f)
        compose.setContent {
            FucknotesTheme {
                InkSurface(
                    strokes = { strokes }, camera = camera, tool = tool,
                    colorArgb = 0xFF171B23.toInt(), widthDp = 3f, enabled = true,
                    fingerInputMode = FingerInputMode.HAND, zoomLocked = false, notebook = false,
                    paperStyle = PaperStyle.PLAIN, pageSpec = PageSpec.Default,
                    ruleColor = Color.Gray, ruleSpacingDp = 24f, deskColor = Color.Gray,
                    onStrokesFinished = { finished -> strokes = strokes + finished.map { InkStroke(it, InkTool.PEN) } },
                    onErase = { erased++ }, onToggleEraser = {},
                    modifier = Modifier.fillMaxSize().testTag("page"),
                )
            }
        }
        compose.onNodeWithTag("page").assertExists()
        val start = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, 300f, 500f, start, start)
        for (i in 1..6) send(MotionEvent.ACTION_MOVE, 300f + i * 20f, 500f, start, start + i * 12L)
        compose.waitForIdle()
        // The button is pressed with the pen still down.
        tool = InkTool.ERASER
        compose.waitForIdle()
        for (i in 7..12) send(MotionEvent.ACTION_MOVE, 300f + i * 20f, 500f, start, start + i * 12L)
        send(MotionEvent.ACTION_UP, 560f, 500f, start, start + 13 * 12L)
        // The wet layer hands the stroke over from its render thread, after the UI is idle.
        compose.waitUntil(5_000) { strokes.isNotEmpty() }
        assertEquals("the stroke the pen was drawing is kept whole", 1, strokes.size)
        assertEquals("nothing was erased by the stroke that was being written", 0, erased)

        // The next gesture is the eraser's: it passes over the stroke and erases it.
        val second = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, 300f, 490f, second, second)
        for (i in 1..8) send(MotionEvent.ACTION_MOVE, 300f + i * 30f, 500f, second, second + i * 12L)
        send(MotionEvent.ACTION_UP, 540f, 500f, second, second + 9 * 12L)
        compose.waitForIdle()
        assertTrue("the eraser gesture reports hits", erased > 0)
        assertEquals("and does not draw", 1, strokes.size)
    }

    @Test fun switchingToThePenMidEraseDoesNotStartAStrokeFromThinAir() {
        var tool by mutableStateOf(InkTool.ERASER)
        val brush = InkCodec.brushFor(InkTool.PEN, 0xFF171B23.toInt(), 3f, 1f)
        val inputs = MutableStrokeInputBatch()
        repeat(6) { inputs.add(InputToolType.STYLUS, 900f + it * 10f, 900f, it * 16L) }
        var strokes by mutableStateOf(listOf(InkStroke(Stroke(brush, inputs), InkTool.PEN)))
        val camera = InkCamera(1f)
        compose.setContent {
            FucknotesTheme {
                InkSurface(
                    strokes = { strokes }, camera = camera, tool = tool,
                    colorArgb = 0xFF171B23.toInt(), widthDp = 3f, enabled = true,
                    fingerInputMode = FingerInputMode.HAND, zoomLocked = false, notebook = false,
                    paperStyle = PaperStyle.PLAIN, pageSpec = PageSpec.Default,
                    ruleColor = Color.Gray, ruleSpacingDp = 24f, deskColor = Color.Gray,
                    onStrokesFinished = { finished -> strokes = strokes + finished.map { InkStroke(it, InkTool.PEN) } },
                    onErase = { hits -> strokes = strokes.filterIndexed { i, _ -> i !in hits } }, onToggleEraser = {},
                    modifier = Modifier.fillMaxSize().testTag("page"),
                )
            }
        }
        compose.onNodeWithTag("page").assertExists()
        val start = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, 300f, 300f, start, start)
        for (i in 1..4) send(MotionEvent.ACTION_MOVE, 300f + i * 20f, 300f, start, start + i * 12L)
        compose.waitForIdle()
        tool = InkTool.PEN
        compose.waitForIdle()
        for (i in 5..10) send(MotionEvent.ACTION_MOVE, 300f + i * 20f, 300f, start, start + i * 12L)
        send(MotionEvent.ACTION_UP, 520f, 300f, start, start + 11 * 12L)
        compose.waitForIdle()
        SystemClock.sleep(500)
        compose.runOnIdle { assertEquals("the erase gesture does not turn into a stroke halfway", 1, strokes.size) }
    }
}
