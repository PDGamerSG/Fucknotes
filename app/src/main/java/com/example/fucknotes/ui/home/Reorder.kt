package com.example.fucknotes.ui.home

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One laid-out shelf item, in the scroll container's viewport coordinates. */
data class ShelfItemBox(val key: Long, val left: Float, val top: Float, val width: Float, val height: Float) {
    fun contains(x: Float, y: Float): Boolean =
        x >= left && x < left + width && y >= top && y < top + height
}

/**
 * Hold-to-drag reordering for the shelf.
 *
 * The shelf's order is a decision the user made, so it only changes by a deliberate gesture: a
 * press held still for [HOLD_MILLIS] lifts the note, which then follows the finger and swaps
 * places with whatever it is dragged over. Every other press keeps its meaning — a tap opens, a
 * long press selects — and a press that starts to move within the hold becomes an ordinary scroll.
 *
 * The state is layout-agnostic: the grid and the list each describe their items as [ShelfItemBox]es
 * and lend their scroll, so one gesture serves both.
 */
@Stable
class NoteReorderState(
    private val visibleItems: () -> List<ShelfItemBox>,
    private val viewportHeight: () -> Float,
    private val scrollBy: suspend (Float) -> Unit,
    private val scope: CoroutineScope,
    private val onMove: (noteId: Long, beforeId: Long?, afterId: Long?) -> Unit,
) {
    var draggingId: Long? by mutableStateOf(null)
        private set

    /** Where the finger is, in viewport coordinates. */
    private var pointer: Offset by mutableStateOf(Offset.Zero)

    /** Where inside the note the finger took hold, so the note does not jump under it. */
    private var grab: Offset = Offset.Zero

    /** The order on screen while a drag is in flight and until the store catches up. */
    private var order: List<Long>? by mutableStateOf(null)

    /** The items in the order they should be shown: the dragged order while one is pending. */
    fun <T> arrange(items: List<T>, key: (T) -> Long): List<T> {
        val pending = order ?: return items
        val byKey = items.associateBy(key)
        val arranged = pending.mapNotNull { byKey[it] }
        val fresh = items.filter { key(it) !in pending }
        return arranged + fresh
    }

    /** Called with every new list from the store: once it matches, the local order is dropped. */
    fun settle(ids: List<Long>) {
        if (draggingId == null && order != null && (ids == order || ids.toSet() != order!!.toSet())) order = null
    }

    fun start(id: Long, currentIds: List<Long>, pointerInViewport: Offset, grabOffset: Offset) {
        draggingId = id
        pointer = pointerInViewport
        grab = grabOffset
        order = currentIds
    }

    fun drag(delta: Offset) {
        val id = draggingId ?: return
        pointer += delta
        val boxes = visibleItems()

        // Near an edge the shelf scrolls under the finger, so a note can travel further than one
        // screen. Scrolling moves the boxes, not the pointer, so the hit test below stays right.
        val viewport = viewportHeight()
        when {
            pointer.y < AUTOSCROLL_EDGE -> scope.launch { scrollBy(-AUTOSCROLL_STEP) }
            pointer.y > viewport - AUTOSCROLL_EDGE -> scope.launch { scrollBy(AUTOSCROLL_STEP) }
        }

        val target = boxes.firstOrNull { it.key != id && it.contains(pointer.x, pointer.y) } ?: return
        val list = (order ?: return).toMutableList()
        val from = list.indexOf(id)
        val to = list.indexOf(target.key)
        if (from < 0 || to < 0 || from == to) return
        list.removeAt(from)
        list.add(to, id)
        order = list
    }

    fun end() {
        val id = draggingId ?: return
        val list = order
        draggingId = null
        if (list == null) return
        val index = list.indexOf(id)
        onMove(id, list.getOrNull(index - 1), list.getOrNull(index + 1))
        // If the store never answers (a failed write), the shelf must not stay frozen in the
        // dragged order.
        scope.launch {
            delay(SETTLE_TIMEOUT_MS)
            if (draggingId == null) order = null
        }
    }

    fun cancel() {
        draggingId = null
        order = null
    }

    /** How far the dragged note is drawn from its laid-out slot, so it sits under the finger. */
    fun translationFor(id: Long): Offset {
        if (draggingId != id) return Offset.Zero
        val box = visibleItems().firstOrNull { it.key == id } ?: return Offset.Zero
        return Offset(pointer.x - grab.x - box.left, pointer.y - grab.y - box.top)
    }

    companion object {
        /** How long a still press lasts before the note lifts. */
        const val HOLD_MILLIS = 2_000L
        private const val AUTOSCROLL_EDGE = 120f
        private const val AUTOSCROLL_STEP = 28f
        private const val SETTLE_TIMEOUT_MS = 2_500L
    }
}

/**
 * Draws a shelf item in its own layer and, while it is being dragged, under the finger.
 *
 * The layer is deliberate even when nothing is dragged: a scrolling shelf then moves rendered
 * tiles instead of repainting every page thumbnail each frame.
 */
fun Modifier.reorderItem(state: NoteReorderState, id: Long): Modifier = this
    .zIndex(if (state.draggingId == id) 1f else 0f)
    .graphicsLayer {
        val shift = state.translationFor(id)
        translationX = shift.x
        translationY = shift.y
        val lifted = state.draggingId == id
        scaleX = if (lifted) 1.04f else 1f
        scaleY = if (lifted) 1.04f else 1f
        shadowElevation = if (lifted) 24f else 0f
    }

/**
 * Tap, long press and hold-to-drag on one shelf item.
 *
 * A standard long press fires at the system timeout and selects, as before. Holding on past
 * [NoteReorderState.HOLD_MILLIS] without moving lifts the note instead; [onDragStart] lets the
 * caller take back the selection that the long press just made. Any movement before the hold
 * completes hands the gesture to the shelf's scroll.
 */
@Composable
fun Modifier.holdToDrag(
    state: NoteReorderState,
    id: Long,
    currentIds: () -> List<Long>,
    boxOf: () -> ShelfItemBox?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDragStart: () -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
    val dragStart by rememberUpdatedState(onDragStart)
    val ids by rememberUpdatedState(currentIds)
    val box by rememberUpdatedState(boxOf)
    return this
        .semantics {
            role = Role.Button
            onClick { click(); true }
            onLongClick { longClick(); true }
        }
        .pointerInput(state, id) {
            awaitEachGesture {
                val down = awaitFirstDown()
                val slop = viewConfiguration.touchSlop
                val longPressMs = viewConfiguration.longPressTimeoutMillis
                var longPressed = false
                var travel = Offset.Zero
                var last = down.position
                var lifted = false

                while (true) {
                    val elapsed = SystemClock.uptimeMillis() - down.uptimeMillis
                    val deadline = (if (longPressed) NoteReorderState.HOLD_MILLIS else longPressMs) - elapsed
                    val event = if (deadline <= 0) null else withTimeoutOrNull(deadline) { awaitPointerEvent() }
                    if (event == null) {
                        if (!longPressed) {
                            longPressed = true
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            longClick()
                            continue
                        }
                        val origin = box() ?: return@awaitEachGesture
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        dragStart()
                        state.start(
                            id = id,
                            currentIds = ids(),
                            pointerInViewport = Offset(origin.left + last.x, origin.top + last.y),
                            grabOffset = last,
                        )
                        lifted = true
                        break
                    }
                    val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                    if (change.isConsumed) return@awaitEachGesture
                    if (!change.pressed) {
                        if (!longPressed) click()
                        return@awaitEachGesture
                    }
                    travel += change.positionChange()
                    last = change.position
                    if (travel.getDistance() > slop) return@awaitEachGesture
                }

                if (!lifted) return@awaitEachGesture
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        state.drag(change.positionChange())
                        change.consume()
                    }
                    state.end()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    state.cancel()
                    throw cancelled
                }
            }
        }
}
