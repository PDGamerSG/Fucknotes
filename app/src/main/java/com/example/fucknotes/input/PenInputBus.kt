package com.example.fucknotes.input

import android.view.KeyEvent
import android.view.MotionEvent
import com.example.fucknotes.data.model.PenButton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-local bridge from Android stylus button events to the active editor. */
object PenInputBus {
    private val _buttons = MutableSharedFlow<PenButton>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val buttons: SharedFlow<PenButton> = _buttons

    /** Last raw hardware event, exposed temporarily so an attached pen can be identified. */
    private val _lastDebugEvent = MutableStateFlow<String?>(null)
    val lastDebugEvent: StateFlow<String?> = _lastDebugEvent.asStateFlow()

    // The Focus Pen can report one press through both its standalone HID endpoint and the paired
    // keyboard endpoint.  Collapse that duplicate packet so an undo/redo or eraser toggle happens
    // exactly once.
    private var lastButton: PenButton? = null
    private var lastAtMs: Long = 0L

    fun emit(button: PenButton) {
        val now = android.os.SystemClock.uptimeMillis()
        if (button == lastButton && now - lastAtMs < 100L) return
        lastButton = button
        lastAtMs = now
        _buttons.tryEmit(button)
    }

    fun recordKeyEvent(event: KeyEvent, mappedButton: PenButton? = null) {
        val device = event.device
        val description = buildString {
            append("key")
            append(" device=").append(device?.name ?: "<none>")
            append(" id=").append(event.deviceId)
            if (device != null) {
                append(" vendor=0x").append(device.vendorId.toString(16))
                append(" product=0x").append(device.productId.toString(16))
            }
            append(" source=0x").append(event.source.toString(16))
            append(" action=").append(event.action)
            append(" keyCode=").append(event.keyCode)
            append(" key=").append(KeyEvent.keyCodeToString(event.keyCode))
            append(" scanCode=").append(event.scanCode)
            append(" repeat=").append(event.repeatCount)
            mappedButton?.let { append(" mapped=").append(it.name) }
        }
        _lastDebugEvent.value = description
    }

    fun recordMotionEvent(event: MotionEvent) {
        if (event.buttonState == 0 &&
            event.actionButton == 0 &&
            event.actionMasked != MotionEvent.ACTION_BUTTON_PRESS &&
            event.actionMasked != MotionEvent.ACTION_BUTTON_RELEASE
        ) return
        val device = event.device
        val description = buildString {
            append("motion")
            append(" device=").append(device?.name ?: "<none>")
            append(" id=").append(device?.id ?: -1)
            if (device != null) {
                append(" vendor=0x").append(device.vendorId.toString(16))
                append(" product=0x").append(device.productId.toString(16))
            }
            append(" source=0x").append(event.source.toString(16))
            append(" action=").append(event.actionMasked)
            append(" buttons=0x").append(event.buttonState.toString(16))
            append(" actionButton=0x").append(event.actionButton.toString(16))
        }
        _lastDebugEvent.value = description
    }
}
