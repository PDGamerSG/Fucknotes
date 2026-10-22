package com.example.fucknotes.input

import com.example.fucknotes.data.model.PenButton

/**
 * One physical press of a pen button, however many packets it arrives in.
 *
 * The Xiaomi Focus Pen reports a switch through more than one endpoint — a key down from the
 * HID endpoint and the matching key up from the paired keyboard endpoint, say, or a key and a
 * stylus-button motion packet for the same press. Counted naively that is two toggles, and an
 * eraser that was switched on is switched straight back off: the pen "does nothing". This
 * class decides, from the sequence alone, which packets are the start of a press. Pure Kotlin
 * so the rules are checked on the JVM.
 */
class PenPresses(private val staleAfterMs: Long = STALE_AFTER_MS) {
    enum class Source { KEY, MOTION }

    private class Held(var sinceMs: Long, val identities: MutableSet<Long>, var source: Source)

    private class Released(val atMs: Long, val identities: Set<Long>)

    private val held = mutableMapOf<PenButton, Held>()
    private val released = mutableMapOf<PenButton, Released>()
    private var lastEmitted: PenButton? = null
    private var lastEmittedAtMs = Long.MIN_VALUE

    /** A key went down ([repeat] packets never start a press). True when this starts a press. */
    fun keyDown(button: PenButton, identity: Long, nowMs: Long, repeat: Boolean = false): Boolean {
        if (repeat) return false
        return press(button, identity, nowMs, Source.KEY)
    }

    /**
     * A key came up. The release of a press that is held — under any identity, from any
     * endpoint — ends it quietly. A release with nothing held is up-only firmware, and counts
     * as the press itself.
     */
    fun keyUp(button: PenButton, identity: Long, nowMs: Long): Boolean {
        val current = held[button]
        if (current != null) {
            held.remove(button)
            released[button] = Released(nowMs, current.identities + identity)
            return false
        }
        // The other endpoint's up for a press already released: still the same press.
        val recent = released[button]
        if (recent != null && (identity in recent.identities || nowMs - recent.atMs < RELEASE_GRACE_MS) &&
            nowMs - recent.atMs < staleAfterMs
        ) return false
        return emit(button, nowMs)
    }

    /** A complete key packet with no later up. A press unless the button is already held. */
    fun keyMultiple(button: PenButton, nowMs: Long): Boolean {
        if (isHeld(button, nowMs)) return false
        return emit(button, nowMs)
    }

    /** A stylus button reported newly pressed in the motion stream. */
    fun motionPress(button: PenButton, identity: Long, nowMs: Long): Boolean =
        press(button, identity, nowMs, Source.MOTION)

    fun motionRelease(button: PenButton) {
        if (held[button]?.source == Source.MOTION) held.remove(button)
    }

    fun clear() {
        held.clear()
        released.clear()
    }

    private fun press(button: PenButton, identity: Long, nowMs: Long, source: Source): Boolean {
        val current = held[button]
        if (current != null && nowMs - current.sinceMs <= staleAfterMs) {
            // The same press seen again through another endpoint or the other stream.
            current.identities += identity
            return false
        }
        // Nothing held, or a press whose release was lost long ago: a new press.
        held[button] = Held(nowMs, mutableSetOf(identity), source)
        return emit(button, nowMs)
    }

    private fun isHeld(button: PenButton, nowMs: Long): Boolean {
        val current = held[button] ?: return false
        if (nowMs - current.sinceMs > staleAfterMs) { held.remove(button); return false }
        return true
    }

    /** The last guard: the same button twice within a blink is one press whatever the packets said. */
    private fun emit(button: PenButton, nowMs: Long): Boolean {
        if (button == lastEmitted && nowMs - lastEmittedAtMs < DUPLICATE_WINDOW_MS) return false
        lastEmitted = button
        lastEmittedAtMs = nowMs
        return true
    }

    companion object {
        /** A press without a release for this long is taken to have lost its release. */
        const val STALE_AFTER_MS = 1_500L
        const val DUPLICATE_WINDOW_MS = 120L

        /** A second up this soon after a release is the same release from the other endpoint. */
        const val RELEASE_GRACE_MS = 300L
    }
}
