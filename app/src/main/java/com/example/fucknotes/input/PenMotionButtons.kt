package com.example.fucknotes.input

/** Tracks physical switches independently of pen-tip contact and repeated hover packets. */
internal class PenMotionButtons {
    private val heldByDevice = mutableMapOf<Int, Int>()

    fun update(deviceId: Int, buttons: Int, actionButton: Int, released: Boolean, cancelled: Boolean): Int {
        if (cancelled) {
            heldByDevice.remove(deviceId)
            return 0
        }
        val previous = heldByDevice[deviceId] ?: 0
        // Some firmware reports a press only in actionButton, with a delayed buttonState.
        // A release packet may still carry the old state; never interpret it as a press.
        val current = if (released) buttons and actionButton.inv() else buttons or actionButton
        if (current == 0) heldByDevice.remove(deviceId) else heldByDevice[deviceId] = current
        return if (released) 0 else current and previous.inv()
    }

    fun clear() = heldByDevice.clear()
}
