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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fucknotes.data.ink.InkStroke
import com.example.fucknotes.data.ink.PageSpec
import com.example.fucknotes.data.model.DefaultPenTray
import com.example.fucknotes.data.model.FingerInputMode
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.ui.editor.ink.InkCamera
import com.example.fucknotes.ui.editor.ink.InkSurface
import com.example.fucknotes.ui.editor.ink.InkToolbar
import com.example.fucknotes.ui.editor.ink.penLabel
import com.example.fucknotes.ui.theme.FucknotesTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real pointer handling in an isolated surface, without opening or changing notes. */
@RunWith(AndroidJUnit4::class)
class FingerInputTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun fingerModesSeparateNavigationWritingAndDisabledTouch() {
        val camera = InkCamera(1f)
        var mode by mutableStateOf(FingerInputMode.HAND)
        var strokes by mutableStateOf(emptyList<InkStroke>())
        var swaps = 0
        compose.setContent {
            FucknotesTheme {
                InkSurface(
                    strokes = { strokes }, camera = camera, tool = InkTool.PEN,
                    colorArgb = 0xFF2386DC.toInt(), widthDp = 3f, enabled = true,
                    fingerInputMode = mode, zoomLocked = false, notebook = false,
                    paperStyle = PaperStyle.DOTTED, pageSpec = PageSpec.Default,
                    ruleColor = Color.Gray, ruleSpacingDp = 24f, deskColor = Color.Gray,
                    onStrokesFinished = { finished -> strokes = strokes + finished.map { InkStroke(it, InkTool.PEN) } },
                    onErase = { indices -> strokes = strokes.filterIndexed { index, _ -> index !in indices } },
                    onToggleEraser = { swaps++ }, modifier = Modifier.fillMaxSize().testTag("page"),
                )
            }
        }
        val page = compose.onNodeWithTag("page")
        page.performTouchInput { swipe(Offset(200f, 400f), Offset(200f, 650f)) }
        compose.runOnIdle {
            assertTrue("Hand mode must move the page", camera.panY > 100f)
            assertTrue("Hand mode must not create ink", strokes.isEmpty())
            camera.reset()
            mode = FingerInputMode.DISABLED
        }
        page.performTouchInput {
            swipe(Offset(200f, 400f), Offset(200f, 650f))
            doubleClick(Offset(200f, 400f))
            pinch(Offset(100f, 300f), Offset(400f, 300f), Offset(50f, 250f), Offset(500f, 350f))
        }
        compose.runOnIdle {
            assertEquals(0f, camera.panX, 0f)
            assertEquals(0f, camera.panY, 0f)
            assertEquals(1f, camera.zoom, 0f)
            assertEquals("Disabled touch must not toggle eraser", 0, swaps)
            assertTrue("Disabled touch must not draw", strokes.isEmpty())
        }
        // Dispatch an actual stylus stream while finger input is disabled.
        val start = SystemClock.uptimeMillis()
        for (i in 0..12) {
            val action = when (i) { 0 -> MotionEvent.ACTION_DOWN; 12 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
            val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
            val coords = MotionEvent.PointerCoords().apply {
                x = 200f + i * 12f; y = 500f; pressure = if (i == 12) 0f else 0.7f
            }
            val event = MotionEvent.obtain(start, start + i * 16L, action, 1, arrayOf(properties),
                arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
            compose.runOnUiThread { compose.activity.dispatchTouchEvent(event) }
            event.recycle()
        }
        compose.waitUntil(5_000) { strokes.isNotEmpty() }
        val stylusCount = strokes.size
        compose.runOnIdle { mode = FingerInputMode.DRAW }
        page.performTouchInput { swipe(Offset(200f, 800f), Offset(450f, 800f)) }
        compose.waitUntil(5_000) { strokes.size > stylusCount }
        compose.runOnIdle {
            assertEquals("Writing mode must not pan", 0f, camera.panY, 0f)
        }
    }

    @Test fun compactToolbarHasThreePensTwoHighlightersAndThreeTouchChoices() {
        var selected by mutableStateOf(0)
        var mode by mutableStateOf(FingerInputMode.HAND)
        val pens = DefaultPenTray
        compose.setContent {
            FucknotesTheme {
                InkToolbar(
                    pens = pens, selectedPen = selected, tool = pens[selected].tool,
                    fingerInputMode = mode, compact = true, onSelectPen = { selected = it },
                    onEditPen = { _, _, _ -> }, onSelectTool = {}, onFingerInputMode = { mode = it },
                    onToggleCompact = {},
                )
            }
        }
        compose.onAllNodes(hasContentDescription("Pen,", substring = true)).assertCountEquals(3)
        compose.onAllNodes(hasContentDescription("Highlighter,", substring = true)).assertCountEquals(2)
        compose.onNodeWithContentDescription("Pen").assertDoesNotExist()
        compose.onNodeWithContentDescription("Highlighter").assertDoesNotExist()
        for (index in listOf(6, 7, 2)) {
            compose.onNodeWithContentDescription(penLabel(pens[index])).performScrollTo().performClick().assertIsSelected()
            compose.runOnIdle { assertEquals(index, selected) }
        }
        for (next in FingerInputMode.entries) {
            compose.onNodeWithContentDescription("Finger input: ${mode.label}").performClick()
            compose.onNodeWithText(next.label).performClick()
            compose.runOnIdle { assertEquals(next, mode) }
        }
    }
}
