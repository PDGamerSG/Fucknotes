package com.example.fucknotes.ui.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isOutOfBounds
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
            pointer.y < AUTOSCROLL_EDGE -> scope.launch { scrollBy(-AUTOSCROLL_STEP); settleOver(id) }
            pointer.y > viewport - AUTOSCROLL_EDGE -> scope.launch { scrollBy(AUTOSCROLL_STEP); settleOver(id) }
        }
        settleOver(id, boxes)
    }

    /** Moves [id] to the slot under the finger, if the finger is over another note. */
    private fun settleOver(id: Long, boxes: List<ShelfItemBox> = visibleItems()) {
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
        /** How long a press must last to select on release, past which a still hold lifts. */
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
 * The rules follow the platform's own long-press drag: a press is only cancelled when something
 * above takes the event — the shelf's scroll, once the finger has clearly moved — or when the
 * finger leaves the tile. Jitter never cancels it: a finger held still for two seconds drifts
 * more than a touch slop, and a hold that failed for that reason would feel broken.
 *
 * Timing: a release before the long-press timeout is a tap; a release after it is a long press
 * (selection) — applied on release, so nothing under the finger changes while it is still held;
 * a still hold past [NoteReorderState.HOLD_MILLIS] lifts the note, which then follows the finger
 * until it is dropped. Each phase is one timeout around [followPress], so the gesture keeps the
 * same clock as the rest of the pointer system rather than reading the wall clock itself.
 */
@Composable
fun Modifier.holdToDrag(
    state: NoteReorderState,
    id: Long,
    currentIds: () -> List<Long>,
    boxOf: () -> ShelfItemBox?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val click by rememberUpdatedState(onClick)
    val longClick by rememberUpdatedState(onLongClick)
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
                var last = down.position
                val longPressMs = viewConfiguration.longPressTimeoutMillis

                // Phase one: up to the long-press timeout. A release here is a tap.
                when (withTimeoutOrNull(longPressMs) { followPress(down.id) { last = it } }) {
                    PressEnd.RELEASED -> { click(); return@awaitEachGesture }
                    PressEnd.CANCELLED -> return@awaitEachGesture
                    null -> Unit
                }
                // A light tick says the press registered; the note is selected only if the
                // finger comes up now, so the tile stays put under a hold.
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                // Phase two: up to the hold. A release here is a long press.
                val remaining = (NoteReorderState.HOLD_MILLIS - longPressMs).coerceAtLeast(1L)
                when (withTimeoutOrNull(remaining) { followPress(down.id) { last = it } }) {
                    PressEnd.RELEASED -> { longClick(); return@awaitEachGesture }
                    PressEnd.CANCELLED -> return@awaitEachGesture
                    null -> Unit
                }

                // Held still for the whole hold: lift the note and follow the finger.
                val origin = box() ?: return@awaitEachGesture
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                state.start(
                    id = id,
                    currentIds = ids(),
                    pointerInViewport = Offset(origin.left + last.x, origin.top + last.y),
                    grabOffset = last,
                )
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

private enum class PressEnd { RELEASED, CANCELLED }

/**
 * Follows one pressed pointer until it is released or taken away, reporting each position.
 *
 * Returns how the press ended; the caller wraps it in a timeout, which is how "still held" is
 * detected. A press is taken away when an ancestor consumes it — the shelf's scroll sees the
 * event after this tile does, so that is checked on the final pass — or when it leaves the tile.
 */
private suspend fun AwaitPointerEventScope.followPress(
    pointerId: PointerId,
    onPosition: (Offset) -> Unit,
): PressEnd {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointerId } ?: return PressEnd.CANCELLED
        if (!change.pressed) return PressEnd.RELEASED
        if (change.isConsumed || change.isOutOfBounds(size, extendedTouchPadding)) return PressEnd.CANCELLED
        onPosition(change.position)
        val afterParents = awaitPointerEvent(PointerEventPass.Final)
        if (afterParents.changes.any { it.isConsumed }) return PressEnd.CANCELLED
    }
}
