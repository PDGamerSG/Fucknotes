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
import com.example.fucknotes.ui.editor.EditorChrome
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
        // Pen only: a finger still moves the page, but never draws or swaps tools.
        page.performTouchInput { swipe(Offset(200f, 400f), Offset(200f, 650f)) }
        compose.runOnIdle {
            assertTrue("Pen only must still scroll by finger", camera.panY > 100f)
            assertTrue("Pen only must not draw", strokes.isEmpty())
            camera.reset()
        }
        page.performTouchInput {
            doubleClick(Offset(200f, 400f))
            pinch(Offset(100f, 300f), Offset(400f, 300f), Offset(50f, 250f), Offset(500f, 350f))
        }
        compose.runOnIdle {
            assertEquals("Pen only must not toggle eraser", 0, swaps)
            assertTrue("Pen only may pinch to zoom", camera.zoom > 1f)
            assertTrue("Pen only must not draw", strokes.isEmpty())
            camera.reset()
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
            dispatchAtEventTime(event)
        }
        compose.waitUntil(5_000) { strokes.isNotEmpty() }
        val beforePalmStroke = strokes.size
        // A palm lands before the pen, stays while it writes, then moves after the pen lifts.
        // No touch in that sequence may pan, add ink, or change the tool.
        val palmStart = SystemClock.uptimeMillis()
        val palm = MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER }
        val pen = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
        for (i in 0..14) {
            val withPen = i in 1..12
            val action = when (i) {
                0 -> MotionEvent.ACTION_DOWN
                1 -> MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                12 -> MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                14 -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val palmCoords = MotionEvent.PointerCoords().apply {
                x = 500f + i * 4f; y = 850f + i * 3f; pressure = if (i == 14) 0f else 1f
            }
            val penCoords = MotionEvent.PointerCoords().apply {
                x = 200f + i * 12f; y = 650f; pressure = if (i == 12) 0f else 0.7f
            }
            val event = MotionEvent.obtain(palmStart, palmStart + i * 16L, action,
                if (withPen) 2 else 1, if (withPen) arrayOf(palm, pen) else arrayOf(palm),
                if (withPen) arrayOf(palmCoords, penCoords) else arrayOf(palmCoords),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN, 0)
            dispatchAtEventTime(event)
        }
        compose.waitUntil(5_000) { strokes.size > beforePalmStroke }
        compose.runOnIdle {
            assertEquals("Only the stylus may add ink with a resting palm", beforePalmStroke + 1, strokes.size)
            assertEquals("A resting palm must not pan", 0f, camera.panY, 0f)
            assertEquals(0f, camera.panX, 0f)
            assertEquals("A resting palm must not zoom", 1f, camera.zoom, 0f)
            assertEquals(0, swaps)
        }
        val stylusCount = strokes.size
        compose.runOnIdle { mode = FingerInputMode.DRAW }
        page.performTouchInput { swipe(Offset(200f, 800f), Offset(450f, 800f)) }
        compose.waitUntil(5_000) { strokes.size > stylusCount }
        compose.runOnIdle {
            assertEquals("Writing mode must not pan", 0f, camera.panY, 0f)
        }
    }

    // Ink measures elapsed time against the real clock on its rendering thread. Dispatching a
    // complete future-dated gesture immediately can give it negative elapsed times and drop ink.
    private fun dispatchAtEventTime(event: MotionEvent) {
        try {
            SystemClock.sleep((event.eventTime - SystemClock.uptimeMillis()).coerceAtLeast(0L))
            compose.runOnUiThread {
                assertTrue("The page must handle the pointer stream", compose.activity.dispatchTouchEvent(event))
            }
        } finally {
            event.recycle()
        }
    }

    @Test fun toolbarCyclesTouchModesWithoutOpeningAMenu() {
        var selected by mutableStateOf(0)
        var mode by mutableStateOf(FingerInputMode.HAND)
        var chrome by mutableStateOf(EditorChrome.COMPACT)
        val pens = DefaultPenTray
        compose.setContent {
            FucknotesTheme {
                InkToolbar(
                    pens = pens, selectedPen = selected, tool = pens[selected].tool,
                    fingerInputMode = mode, chrome = chrome, onSelectPen = { selected = it },
                    onEditPen = { _, _, _ -> }, onSelectTool = {}, onFingerInputMode = { mode = it },
                    onCycleChrome = { chrome = chrome.next() },
                )
            }
        }
        compose.onAllNodes(hasContentDescription("Pen,", substring = true)).assertCountEquals(4)
        compose.onAllNodes(hasContentDescription("Highlighter,", substring = true)).assertCountEquals(2)
        compose.onNodeWithContentDescription("Pen").assertDoesNotExist()
        compose.onNodeWithContentDescription("Highlighter").assertDoesNotExist()
        for (index in listOf(6, 7, 2)) {
            compose.onNodeWithContentDescription(penLabel(pens[index])).performScrollTo().performClick().assertIsSelected()
            compose.runOnIdle { assertEquals(index, selected) }
        }
        // The lock steps COMPACT -> READING -> FULL; the bar is shown in COMPACT and FULL.
        compose.onNodeWithContentDescription(EditorChrome.COMPACT.lockLabel).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(EditorChrome.READING, chrome); chrome = EditorChrome.FULL }
        compose.onNodeWithContentDescription(EditorChrome.FULL.lockLabel).assertIsDisplayed()
        for (expanded in listOf(false, true)) {
            compose.runOnIdle { chrome = if (expanded) EditorChrome.FULL else EditorChrome.COMPACT }
            repeat(2) {
                for (next in listOf(FingerInputMode.DRAW, FingerInputMode.DISABLED, FingerInputMode.HAND)) {
                    compose.onNode(hasContentDescription("Finger input:", substring = true)).performClick()
                    compose.runOnIdle {
                        assertEquals(next, mode)
                        assertEquals("Cycling must preserve the selected pen", 2, selected)
                    }
                    compose.onNode(hasContentDescription("Finger input: ${next.label}.", substring = true))
                        .assertIsDisplayed()
                    FingerInputMode.entries.forEach { compose.onNodeWithText(it.label).assertDoesNotExist() }
                }
            }
        }
    }

    @Test fun readingModeLetsFingersScrollWithPenOnlyAndZoomLockOn() {
        val camera = InkCamera(1f)
        var strokes by mutableStateOf(emptyList<InkStroke>())
        var swaps = 0
        compose.setContent {
            FucknotesTheme {
                InkSurface(
                    strokes = { strokes }, camera = camera, tool = InkTool.PEN,
                    colorArgb = 0xFF2386DC.toInt(), widthDp = 3f, enabled = true,
                    fingerInputMode = FingerInputMode.DISABLED, zoomLocked = true, notebook = false,
                    paperStyle = PaperStyle.DOTTED, pageSpec = PageSpec.Default,
                    ruleColor = Color.Gray, ruleSpacingDp = 24f, deskColor = Color.Gray,
                    onStrokesFinished = { finished -> strokes = strokes + finished.map { InkStroke(it, InkTool.PEN) } },
                    onErase = {}, onToggleEraser = { swaps++ }, reading = true,
                    modifier = Modifier.fillMaxSize().testTag("reading-page"),
                )
            }
        }
        val page = compose.onNodeWithTag("reading-page")
        page.performTouchInput { swipe(Offset(200f, 400f), Offset(200f, 650f)) }
        compose.runOnIdle {
            assertTrue("A finger must scroll a page being read", camera.panY > 100f)
            assertTrue("A finger must not draw while reading", strokes.isEmpty())
            camera.reset()
        }
        page.performTouchInput {
            doubleClick(Offset(300f, 500f))
            pinch(Offset(100f, 300f), Offset(400f, 300f), Offset(40f, 240f), Offset(460f, 360f))
        }
        compose.runOnIdle {
            assertEquals("Reading must not swap tools on a double tap", 0, swaps)
            assertEquals("The zoom lock still holds the scale while reading", 1f, camera.zoom, 0f)
            assertTrue("A finger must not draw while reading", strokes.isEmpty())
        }
    }

    @Test fun zoomLockPinsScaleButStillScrollsAndWrites() {
        val camera = InkCamera(1f)
        var strokes by mutableStateOf(emptyList<InkStroke>())
        var swaps = 0
        compose.setContent {
            FucknotesTheme {
                InkSurface(
                    strokes = { strokes }, camera = camera, tool = InkTool.PEN,
                    colorArgb = 0xFF2386DC.toInt(), widthDp = 3f, enabled = true,
                    fingerInputMode = FingerInputMode.HAND, zoomLocked = true, notebook = false,
                    paperStyle = PaperStyle.DOTTED, pageSpec = PageSpec.Default,
                    ruleColor = Color.Gray, ruleSpacingDp = 24f, deskColor = Color.Gray,
                    onStrokesFinished = { finished -> strokes = strokes + finished.map { InkStroke(it, InkTool.PEN) } },
                    onErase = {}, onToggleEraser = { swaps++ },
                    modifier = Modifier.fillMaxSize().testTag("locked-page"),
                )
            }
        }
        val locked = compose.onNodeWithTag("locked-page")
        locked.performTouchInput { swipe(Offset(200f, 400f), Offset(600f, 800f)) }
        compose.runOnIdle {
            assertTrue("A locked page must still scroll by finger", camera.panY > 100f)
            camera.reset()
        }
        locked.performTouchInput {
            doubleClick(Offset(300f, 500f))
            pinch(Offset(100f, 300f), Offset(400f, 300f), Offset(40f, 240f), Offset(460f, 360f))
        }
        compose.runOnIdle {
            assertEquals("Locked touch must not zoom", 1f, camera.zoom, 0f)
            assertEquals("Locked touch must not toggle tools", 0, swaps)
            assertTrue("Locked touch must not draw", strokes.isEmpty())
        }

        val start = SystemClock.uptimeMillis()
        for (i in 0..12) {
            val action = when (i) { 0 -> MotionEvent.ACTION_DOWN; 12 -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
            val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
            val coords = MotionEvent.PointerCoords().apply {
                x = 240f + i * 12f; y = 520f; pressure = if (i == 12) 0f else 0.7f
            }
            val event = MotionEvent.obtain(start, start + i * 16L, action, 1, arrayOf(properties),
                arrayOf(coords), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_STYLUS, 0)
            dispatchAtEventTime(event)
        }
        compose.waitUntil(5_000) { strokes.isNotEmpty() }
        compose.runOnIdle { assertEquals("Locked mode must keep stylus writing", 1, strokes.size) }
    }
}
