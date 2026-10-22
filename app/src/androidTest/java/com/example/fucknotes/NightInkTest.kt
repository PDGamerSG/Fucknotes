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
import com.example.fucknotes.ui.editor.ink.daylight
import com.example.fucknotes.ui.editor.ink.darkReaderArgb
import com.example.fucknotes.ui.editor.ink.forNight
import com.example.fucknotes.ui.theme.FucknotesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ink written with dark mode on is stored in the colour the user chose, not the night colour it
 * was drawn in. The old path stored the night colour, so the dry layer inverted it a second time
 * and the stroke changed shade on pen lift.
 */
@RunWith(AndroidJUnit4::class)
class NightInkTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun nightBrushRoundTripsToItsDaylightBrush() {
        val black = 0xFF171B23.toInt()
        val day = InkCodec.brushFor(InkTool.PEN, black, 3f, 1f)
        val night = day.forNight(InkTool.PEN)
        assertNotEquals(black, night.colorIntArgb)
        assertEquals(darkReaderArgb(black, InkTool.PEN), night.colorIntArgb)

        val inputs = MutableStrokeInputBatch()
        repeat(6) { inputs.add(InputToolType.STYLUS, 10f + it * 8f, 20f, it * 16L) }
        val wet = Stroke(night, inputs)
        val stored = wet.daylight()
        assertEquals("the stored stroke carries the chosen colour", black, stored.brush.colorIntArgb)
        assertEquals("the stroke's tool survives", InkTool.PEN, InkCodec.toolForStroke(stored, InkTool.MARKER))
        // A stroke that never went through a night brush is untouched.
        val plain = Stroke(day, inputs)
        assertTrue(plain.daylight() === plain)
    }

    @Test fun strokeWrittenInDarkModeIsCommittedInTheChosenColour() {
        val camera = InkCamera(1f)
        val chosen = 0xFF171B23.toInt()
        var strokes by mutableStateOf(emptyList<InkStroke>())
        compose.setContent {
            FucknotesTheme(darkReader = true) {
                InkSurface(
                    strokes = { strokes }, camera = camera, tool = InkTool.PEN,
                    colorArgb = chosen, widthDp = 3f, enabled = true,
                    fingerInputMode = FingerInputMode.HAND, zoomLocked = false, notebook = false,
                    paperStyle = PaperStyle.DOTTED, pageSpec = PageSpec.Default,
                    ruleColor = Color.Gray, ruleSpacingDp = 24f, deskColor = Color.Gray,
                    onStrokesFinished = { finished -> strokes = strokes + finished.map { InkStroke(it, InkTool.PEN) } },
                    onErase = {}, onToggleEraser = {}, darkReader = true,
                    modifier = Modifier.fillMaxSize().testTag("night-page"),
                )
            }
        }
        val start = SystemClock.uptimeMillis()
        for (i in 0..12) {
            val action = when (i) { 0 -> MotionEvent.ACTION_DOWN; 12 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
            val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
            val coords = MotionEvent.PointerCoords().apply { x = 200f + i * 12f; y = 500f; pressure = if (i == 12) 0f else 0.7f }
            val event = MotionEvent.obtain(start, start + i * 16L, action, 1, arrayOf(properties), arrayOf(coords),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
            try {
                SystemClock.sleep((event.eventTime - SystemClock.uptimeMillis()).coerceAtLeast(0L))
                compose.runOnUiThread { assertTrue(compose.activity.dispatchTouchEvent(event)) }
            } finally { event.recycle() }
        }
        compose.waitUntil(5_000) { strokes.isNotEmpty() }
        compose.runOnIdle {
            assertEquals("stored in the chosen colour, not the night colour", chosen, strokes.single().stroke.brush.colorIntArgb)
        }
    }
}
