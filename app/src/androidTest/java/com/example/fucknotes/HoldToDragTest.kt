package com.example.fucknotes

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fucknotes.ui.home.NoteReorderState
import com.example.fucknotes.ui.home.ShelfItemBox
import com.example.fucknotes.ui.home.holdToDrag
import com.example.fucknotes.ui.home.reorderItem
import com.example.fucknotes.ui.theme.FucknotesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shelf's hold-to-drag with a real, fidgety finger: a two-second hold with jitter lifts the
 * note and drops it where the finger goes; a swipe during the hold scrolls instead; a shorter
 * press selects on release. Driven through the activity so the gesture runs on the real clock.
 */
@RunWith(AndroidJUnit4::class)
class HoldToDragTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val moves = mutableListOf<Triple<Long, Long?, Long?>>()
    private val longClicks = mutableListOf<Long>()
    private val clicks = mutableListOf<Long>()

    private fun content() {
        compose.setContent {
            FucknotesTheme {
                val ids = remember { (1L..6L).toList() }
                val gridState = rememberLazyGridState()
                val scope = rememberCoroutineScope()
                val reorder = remember {
                    NoteReorderState(
                        visibleItems = {
                            gridState.layoutInfo.visibleItemsInfo.map {
                                ShelfItemBox(it.key as Long, it.offset.x.toFloat(), it.offset.y.toFloat(),
                                    it.size.width.toFloat(), it.size.height.toFloat())
                            }
                        },
                        viewportHeight = { gridState.layoutInfo.viewportSize.height.toFloat() },
                        scrollBy = { gridState.scrollBy(it) },
                        scope = scope,
                        onMove = { id, before, after -> moves += Triple(id, before, after) },
                    )
                }
                val ordered = reorder.arrange(ids) { it }
                LazyVerticalGrid(state = gridState, columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize().testTag("grid")) {
                    items(ordered, key = { it }) { id ->
                        Box(
                            Modifier
                                .then(if (reorder.draggingId == id) Modifier else Modifier.animateItem())
                                .reorderItem(reorder, id)
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(Color(0xFF4472C4))
                                .holdToDrag(
                                    state = reorder, id = id, currentIds = { ordered },
                                    boxOf = {
                                        gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.let {
                                            ShelfItemBox(id, it.offset.x.toFloat(), it.offset.y.toFloat(),
                                                it.size.width.toFloat(), it.size.height.toFloat())
                                        }
                                    },
                                    onClick = { clicks += id },
                                    onLongClick = { longClicks += id },
                                )
                                .testTag("tile-$id"),
                        )
                    }
                }
            }
        }
    }

    private fun centre(tag: String): Pair<Float, Float> {
        val b = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        return (b.left + b.right) / 2f to (b.top + b.bottom) / 2f
    }

    private fun send(action: Int, x: Float, y: Float, downTime: Long) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(downTime, now, action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
        try {
            compose.runOnUiThread { assertTrue(compose.activity.dispatchTouchEvent(event)) }
        } finally { event.recycle() }
    }

    /** The test rule runs pointer-input timeouts on its own clock, so time is advanced by hand. */
    private fun pass(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        SystemClock.sleep(ms.coerceAtMost(40))
    }

    @Test fun jitteryTwoSecondHoldLiftsAndDropsTheNote() {
        content()
        val (x1, y1) = centre("tile-1")
        val (x3, y3) = centre("tile-3")
        val downTime = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, x1, y1, downTime)
        // A real finger wanders well past the touch slop over two seconds.
        var x = x1; var y = y1
        repeat(23) { i ->
            pass(100)
            x += if (i % 2 == 0) 9f else -6f
            y += if (i % 3 == 0) 7f else -4f
            send(MotionEvent.ACTION_MOVE, x, y, downTime)
        }
        // Glide to the third tile and let go.
        repeat(10) { i ->
            pass(16)
            send(MotionEvent.ACTION_MOVE, x + (x3 - x) * (i + 1) / 10f, y + (y3 - y) * (i + 1) / 10f, downTime)
        }
        send(MotionEvent.ACTION_UP, x3, y3, downTime)
        compose.waitUntil(3_000) { moves.isNotEmpty() }
        assertEquals("note 1 lands after 3, before 4", Triple(1L, 3L, 4L), moves.single())
        assertTrue("a drag is not a selection", longClicks.isEmpty())
        assertTrue("a drag is not a tap", clicks.isEmpty())
    }

    @Test fun stillTwoSecondHoldLiftsWithoutAnyMovement() {
        content()
        val (x1, y1) = centre("tile-1")
        val (x3, y3) = centre("tile-3")
        val downTime = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, x1, y1, downTime)
        pass(2_300)
        repeat(10) { i ->
            pass(16)
            send(MotionEvent.ACTION_MOVE, x1 + (x3 - x1) * (i + 1) / 10f, y1 + (y3 - y1) * (i + 1) / 10f, downTime)
        }
        send(MotionEvent.ACTION_UP, x3, y3, downTime)
        compose.waitUntil(3_000) { moves.isNotEmpty() }
        assertEquals(Triple(1L, 3L, 4L), moves.single())
        assertTrue(longClicks.isEmpty() && clicks.isEmpty())
    }

    @Test fun swipeBeforeTheTickScrollsInstead() {
        content()
        val (x1, y1) = centre("tile-1")
        val downTime = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, x1, y1, downTime)
        pass(150)
        repeat(12) { i -> pass(16); send(MotionEvent.ACTION_MOVE, x1, y1 - 40f * (i + 1), downTime) }
        pass(2_200)
        send(MotionEvent.ACTION_UP, x1, y1 - 480f, downTime)
        pass(300)
        compose.runOnIdle {
            assertTrue("a swipe must not move the note", moves.isEmpty())
            assertTrue("a swipe must not select", longClicks.isEmpty())
        }
    }

    @Test fun slowVerticalDriftDuringTheHoldStillLifts() {
        content()
        val (x1, y1) = centre("tile-1")
        val (x3, y3) = centre("tile-3")
        val downTime = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, x1, y1, downTime)
        // A finger rolling downwards a few pixels at a time: past the slop long before the hold
        // ends, which used to hand the gesture to the shelf's scroll.
        var y = y1
        repeat(23) { pass(100); y += 3f; send(MotionEvent.ACTION_MOVE, x1, y, downTime) }
        repeat(10) { i ->
            pass(16)
            send(MotionEvent.ACTION_MOVE, x1 + (x3 - x1) * (i + 1) / 10f, y + (y3 - y) * (i + 1) / 10f, downTime)
        }
        send(MotionEvent.ACTION_UP, x3, y3, downTime)
        compose.waitUntil(3_000) { moves.isNotEmpty() }
        assertEquals(Triple(1L, 3L, 4L), moves.single())
        assertTrue(longClicks.isEmpty())
    }

    @Test fun moveAfterTheTickLiftsWithoutWaitingOutTheHold() {
        content()
        val (x1, y1) = centre("tile-1")
        val (x3, y3) = centre("tile-3")
        val downTime = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, x1, y1, downTime)
        pass(700)
        repeat(10) { i ->
            pass(16)
            send(MotionEvent.ACTION_MOVE, x1 + (x3 - x1) * (i + 1) / 10f, y1 + (y3 - y1) * (i + 1) / 10f, downTime)
        }
        send(MotionEvent.ACTION_UP, x3, y3, downTime)
        compose.waitUntil(3_000) { moves.isNotEmpty() }
        assertEquals(Triple(1L, 3L, 4L), moves.single())
        assertTrue(longClicks.isEmpty() && clicks.isEmpty())
    }

    @Test fun tapOpens() {
        content()
        val (x2, y2) = centre("tile-2")
        val downTime = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, x2, y2, downTime)
        SystemClock.sleep(80)
        send(MotionEvent.ACTION_UP, x2, y2, downTime)
        compose.waitUntil(2_000) { clicks.isNotEmpty() }
        assertEquals(listOf(2L), clicks)
        assertTrue(moves.isEmpty() && longClicks.isEmpty())
    }

    @Test fun longPressSelectsOnReleaseThenTapOpens() {
        content()
        val (x2, y2) = centre("tile-2")
        var downTime = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, x2, y2, downTime)
        pass(900)
        send(MotionEvent.ACTION_UP, x2, y2, downTime)
        compose.waitUntil(2_000) { longClicks.isNotEmpty() }
        assertEquals(listOf(2L), longClicks)

        pass(300)
        downTime = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, x2, y2, downTime)
        SystemClock.sleep(80)
        send(MotionEvent.ACTION_UP, x2, y2, downTime)
        compose.waitUntil(2_000) { clicks.isNotEmpty() }
        assertEquals(listOf(2L), clicks)
        assertTrue(moves.isEmpty())
    }
}
