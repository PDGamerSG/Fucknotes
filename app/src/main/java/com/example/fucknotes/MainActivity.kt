package com.example.fucknotes

import android.content.Intent
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.fucknotes.data.repo.SettingsRepository
import com.example.fucknotes.data.model.PenButton
import com.example.fucknotes.input.PenInputBus
import com.example.fucknotes.ui.nav.FucknotesNavGraph
import com.example.fucknotes.ui.theme.FucknotesTheme
import java.util.Locale

/**
 * Single-activity host.
 *
 * It extends [FragmentActivity] because `BiometricPrompt` needs a fragment host to attach to when
 * a locked note is opened.
 */
class MainActivity : FragmentActivity() {
    private val documentOpen by lazy {
        androidx.lifecycle.ViewModelProvider(this)[DocumentOpenViewModel::class.java]
    }


    /** Last Xiaomi button bitmask per input device, used when firmware reports a button on ACTION_MOVE. */
    private val lastPenMotionButtons = mutableMapOf<Int, Int>()

    /** Last actionButton value per device. Some Xiaomi builds repeat it on every hover packet. */
    private val lastPenActionButtons = mutableMapOf<Int, Int>()

    /** Key down state lets us support firmware that sends only ACTION_UP without double-firing. */
    private val pressedPenKeys = mutableSetOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        goFullscreen()

        documentOpen.acceptInitial(intent)

        val settingsRepo = ServiceLocator.settings(this)

        setContent {
            val settings by settingsRepo.settings
                .collectAsStateWithLifecycle(initialValue = SettingsRepository.Settings())

            val document by documentOpen.state.collectAsStateWithLifecycle()

            LaunchedEffect(settings.keepScreenOn) {
                if (settings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            FucknotesTheme(
                themeMode = settings.themeMode,
                accent = settings.accent,
                dynamicColor = settings.dynamicColor,
                fontScalePercent = settings.fontScalePercent,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // The surface itself stays full-bleed so its colour reaches every corner,
                    // including behind a notch; the UI inside keeps clear of the cutout. With the
                    // status bar hidden there is no bar left to hide the camera behind, and the
                    // window is laid out through it, so without this a top-bar icon would sit
                    // under the lens.
                    Box(Modifier.windowInsetsPadding(WindowInsets.displayCutout)) {
                        FucknotesNavGraph(
                            navController = rememberNavController(),
                            openNoteId = document.noteId,
                            onOpenNoteHandled = {
                                documentOpen.consumed()
                                setIntent(Intent(this@MainActivity, MainActivity::class.java))
                            },
                        )
                        if (document.progress != null || document.error != null) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { if (document.progress == null) documentOpen.consumed() },
                                title = { androidx.compose.material3.Text(if (document.error != null) "PDF import" else "Opening PDF") },
                                text = { androidx.compose.material3.Text(document.error ?: document.progress.orEmpty()) },
                                confirmButton = {
                                    if (document.error != null) androidx.compose.material3.TextButton(onClick = documentOpen::consumed) {
                                        androidx.compose.material3.Text("Close")
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        documentOpen.accept(intent)
    }

    /** Xiaomi Pen buttons arrive as Android stylus key events on some firmware versions. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val button = penButtonFor(event)
        val device = event.device ?: InputDevice.getDevice(event.deviceId)
        val isStandardStylusButton = event.keyCode == KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY ||
            event.keyCode == KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY ||
            event.keyCode == KeyEvent.KEYCODE_STYLUS_BUTTON_TERTIARY ||
            event.keyCode == KeyEvent.KEYCODE_STYLUS_BUTTON_TAIL
        val isStylusSource = device?.sources?.and(InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS
        PenInputBus.recordKeyEvent(
            event,
            button.takeIf { isXiaomiPen(device) || isStandardStylusButton || isStylusSource },
        )
        if (button != null && (isXiaomiPen(device) || isStandardStylusButton || isStylusSource)) {
            val identity = penKeyIdentity(event, button)
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    // A paired keyboard endpoint can repeat while the pen button is held. Only
                    // the first packet represents a press.
                    if (event.repeatCount == 0 && pressedPenKeys.add(identity)) {
                        PenInputBus.emit(button)
                    }
                }

                KeyEvent.ACTION_MULTIPLE -> {
                    // A few Focus Pen endpoints send ACTION_MULTIPLE instead of ACTION_DOWN.
                    if (pressedPenKeys.add(identity)) PenInputBus.emit(button)
                }

                KeyEvent.ACTION_UP -> {
                    // Some Xiaomi builds expose an up-only HID packet. Treat it as a press in
                    // that case, while a normal down/up sequence remains one press. If the
                    // firmware changes the key representation on release, clear the active key
                    // for this device without firing the newly reported button a second time.
                    if (!pressedPenKeys.remove(identity)) {
                        val devicePrefix = event.deviceId.toLong() shl 32
                        val mismatched = pressedPenKeys.firstOrNull { (it and DEVICE_ID_MASK) == devicePrefix }
                        if (mismatched != null) pressedPenKeys.remove(mismatched)
                        else PenInputBus.emit(button)
                    }
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Other Xiaomi firmware exposes the same buttons as stylus motion button events. */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        PenInputBus.recordMotionEvent(event)
        if (handlePenMotion(event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    /** A few Xiaomi ROMs route the button state through the normal stylus touch stream. */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        PenInputBus.recordMotionEvent(event)
        if (handlePenMotion(event)) return true
        return super.dispatchTouchEvent(event)
    }

    private fun handlePenMotion(event: MotionEvent): Boolean {
        val device = event.device ?: InputDevice.getDevice(event.deviceId)
        val isStylusSource = event.isFromSource(InputDevice.SOURCE_STYLUS) ||
            device?.sources?.and(InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS
        val isPenMouseSource = event.isFromSource(InputDevice.SOURCE_MOUSE) && isXiaomiPen(device)
        if (!isXiaomiPen(device) && !isStylusSource && !isPenMouseSource) return false

        val deviceId = device?.id ?: -1
        val state = event.buttonState
        val previous = lastPenMotionButtons[deviceId] ?: 0
        // Mouse-style Xiaomi packets identify the changed button in actionButton. On some MIUI
        // builds buttonState is still the previous value for ACTION_BUTTON_PRESS, so relying on
        // the state delta alone drops every pen press.
        // Android normally reports ACTION_BUTTON_PRESS with actionButton, but the Focus Pen's
        // cursor mapper has shipped in builds that report ACTION_MOVE/HOVER_MOVE with the same
        // actionButton field. Use only its rising edge; otherwise use the rising edge in
        // buttonState. This covers both the stylus and mouse input mappers without repeating a
        // shortcut during a long drag.
        val actionButton = event.actionButton
        val isRelease = event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE
        val previousActionButton = lastPenActionButtons[deviceId] ?: 0
        // On the Focus Pen, actionButton can remain set on every HOVER_MOVE while a switch is
        // held. Treat only its rising edge as a press; otherwise a long drag would toggle the
        // eraser repeatedly. Normal Android BUTTON_PRESS packets still take this same path.
        val actionButtonPress = if (
            !isRelease &&
            actionButton != 0 &&
            actionButton != previousActionButton &&
            (previous and actionButton) == 0
        ) {
            actionButton
        } else {
            0
        }
        val newlyPressed = actionButtonPress.takeIf { it != 0 } ?: (state and previous.inv())
        if (newlyPressed != 0) motionButtonFromState(newlyPressed)?.let(PenInputBus::emit)

        if (event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE ||
            event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            lastPenMotionButtons.remove(deviceId)
            lastPenActionButtons.remove(deviceId)
        } else {
            // A MIUI BUTTON_PRESS packet can carry the changed button only in actionButton and
            // leave buttonState at zero. Remember both fields so the following MOVE packet does
            // not produce a second shortcut for the same physical press.
            lastPenMotionButtons[deviceId] = state or actionButton
            lastPenActionButtons[deviceId] = actionButton
        }

        // Button press/release packets have no useful pointer data and must not reach the ink
        // surface. ACTION_MOVE/ACTION_DOWN still pass through so the pen can continue writing.
        return event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS ||
            event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE ||
            (isPenMouseSource && event.actionMasked == MotionEvent.ACTION_DOWN && state != 0)
    }

    private fun penButtonFor(event: KeyEvent): PenButton? = when (event.keyCode) {
        KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY -> PenButton.PRIMARY
        KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY -> PenButton.SECONDARY
        KeyEvent.KEYCODE_STYLUS_BUTTON_TERTIARY -> PenButton.TERTIARY
        KeyEvent.KEYCODE_STYLUS_BUTTON_TAIL -> PenButton.TERTIARY

        // Xiaomi Focus/Smart Pen HID descriptors commonly expose the controls as consecutive
        // function keys. Supporting all three ranges covers Android keyboard-layout variants.
        KeyEvent.KEYCODE_F13, KeyEvent.KEYCODE_F16, KeyEvent.KEYCODE_F19, KeyEvent.KEYCODE_F22 -> PenButton.PRIMARY
        KeyEvent.KEYCODE_F14, KeyEvent.KEYCODE_F17, KeyEvent.KEYCODE_F20, KeyEvent.KEYCODE_F23 -> PenButton.SECONDARY
        KeyEvent.KEYCODE_F15, KeyEvent.KEYCODE_F18, KeyEvent.KEYCODE_F21, KeyEvent.KEYCODE_F24 -> PenButton.TERTIARY

        // Some firmware uses gamepad-style HID usages for its side switches.
        KeyEvent.KEYCODE_BUTTON_A -> PenButton.PRIMARY
        KeyEvent.KEYCODE_BUTTON_B -> PenButton.SECONDARY
        KeyEvent.KEYCODE_BUTTON_C -> PenButton.TERTIARY
        KeyEvent.KEYCODE_BUTTON_1, KeyEvent.KEYCODE_BUTTON_4, KeyEvent.KEYCODE_BUTTON_7 -> PenButton.PRIMARY
        KeyEvent.KEYCODE_BUTTON_2, KeyEvent.KEYCODE_BUTTON_5, KeyEvent.KEYCODE_BUTTON_8 -> PenButton.SECONDARY
        KeyEvent.KEYCODE_BUTTON_3, KeyEvent.KEYCODE_BUTTON_6, KeyEvent.KEYCODE_BUTTON_9 -> PenButton.TERTIARY
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1 -> PenButton.PRIMARY
        KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2 -> PenButton.SECONDARY
        KEYCODE_BUTTON_R3 -> PenButton.TERTIARY

        // A keyboard-profiled pen can fall back to navigation keys. These are handled only when
        // the source device is identified as a pen, so a normal keyboard's Back key is untouched.
        KeyEvent.KEYCODE_BACK -> PenButton.PRIMARY
        KeyEvent.KEYCODE_FORWARD -> PenButton.SECONDARY
        KeyEvent.KEYCODE_MENU -> PenButton.TERTIARY

        // The connected 2410CRP4CI tablet currently translates the Focus Pen's primary
        // HID switch to PAGE_DOWN (keyCode 93, Linux scan code 109). Other firmware uses
        // PAGE_UP for the second switch, so keep both navigation translations pen-scoped.
        KeyEvent.KEYCODE_PAGE_DOWN -> PenButton.PRIMARY
        KeyEvent.KEYCODE_PAGE_UP -> PenButton.SECONDARY

        // The standalone Focus Pen HID advertises KEY_KPPLUS (Linux code 78), which Android
        // translates to NUMPAD_ADD on some tablet builds.
        KeyEvent.KEYCODE_NUMPAD_ADD -> PenButton.PRIMARY
        KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> PenButton.SECONDARY
        KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> PenButton.TERTIARY

        else -> when {
            event.keyCode in KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16 ->
                when ((event.keyCode - KeyEvent.KEYCODE_BUTTON_1) % 3) {
                    0 -> PenButton.PRIMARY
                    1 -> PenButton.SECONDARY
                    else -> PenButton.TERTIARY
                }
            else -> when (event.scanCode) {
                XIAOMI_PRIMARY_SCAN_CODE, XIAOMI_PRIMARY_PAGE_SCAN_CODE,
                SCAN_F13, SCAN_F16, SCAN_F19 -> PenButton.PRIMARY
                XIAOMI_SECONDARY_SCAN_CODE, XIAOMI_SECONDARY_HID_SCAN_CODE,
                XIAOMI_SECONDARY_LEGACY_SCAN_CODE,
                SCAN_F14, SCAN_F17, SCAN_F20 -> PenButton.SECONDARY
                XIAOMI_TERTIARY_SCAN_CODE, SCAN_F15, SCAN_F18, SCAN_F21 -> PenButton.TERTIARY
                // A few Xiaomi ROMs report KEYCODE_UNKNOWN with no usable scan code. A pen has
                // one physical control on those builds, so route it to Primary instead of losing
                // the press entirely.
                else -> if (event.keyCode == KeyEvent.KEYCODE_UNKNOWN &&
                    isXiaomiPen(event.device ?: InputDevice.getDevice(event.deviceId))
                ) {
                    PenButton.PRIMARY
                } else {
                    null
                }
            }
        }
    }

    private fun penKeyIdentity(event: KeyEvent, button: PenButton): Long {
        // The Focus Pen can translate one physical switch to different Android keycodes (and, on
        // some firmware, different scan representations) between down and up. The mapped button
        // is the stable physical identity; the raw key/scan values remain visible in diagnostics.
        return (event.deviceId.toLong() shl 32) xor (button.ordinal + 1).toLong()
    }

    private fun isXiaomiPen(device: InputDevice?): Boolean {
        if (device == null) return false
        val name = device.name.orEmpty().lowercase(Locale.ROOT)
        // Focus Pen currently identifies as vendor 0x0022/product 0x4d80. Keep the name checks
        // for older Android releases where vendorId/productId were not populated on Bluetooth HID.
        val isFocusPenHid = device.vendorId == XIAOMI_VENDOR_ID && device.productId == XIAOMI_PRODUCT_ID
        return isFocusPenHid || name.contains("xiaomi") ||
            name.contains("mi pen") ||
            name.contains("smart pen") ||
            name.contains("focus pen") ||
            name.contains("stylus")
    }

    override fun onPause() {
        // A key-up packet can be lost when a permission dialog or another app takes focus. Reset
        // these edge detectors so the next press after returning to Fucknotes is never ignored.
        pressedPenKeys.clear()
        lastPenMotionButtons.clear()
        lastPenActionButtons.clear()
        super.onPause()
    }

    private fun motionButtonFromState(state: Int): PenButton? = when {
        state and MotionEvent.BUTTON_STYLUS_PRIMARY != 0 -> PenButton.PRIMARY
        state and MotionEvent.BUTTON_STYLUS_SECONDARY != 0 -> PenButton.SECONDARY
        // MotionEvent exposes two stylus masks on current SDKs. A few Xiaomi firmware builds use
        // the next bit for a third button, so keep the mapping available.
        state and STYLUS_TERTIARY_BUTTON_MASK != 0 -> PenButton.TERTIARY
        state and MotionEvent.BUTTON_PRIMARY != 0 -> PenButton.PRIMARY
        state and MotionEvent.BUTTON_SECONDARY != 0 -> PenButton.SECONDARY
        state and MotionEvent.BUTTON_TERTIARY != 0 -> PenButton.TERTIARY
        state and MotionEvent.BUTTON_BACK != 0 -> PenButton.SECONDARY
        state and MotionEvent.BUTTON_FORWARD != 0 -> PenButton.TERTIARY
        else -> null
    }

    /**
     * The bars come back whenever the window loses focus — a permission dialog, the biometric
     * sheet, the recents switcher, the notification shade. Re-hiding on every focus gain is what
     * makes the fullscreen stick instead of lasting until the first interruption.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullscreen()
    }

    /**
     * Hides the status and navigation bars for good.
     *
     * A page of handwriting wants the whole panel, and the navigation bar sits exactly where the
     * hand rests. `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` keeps them reachable: a swipe from the
     * edge floats them over the page, and they retreat on their own — so nothing is lost, and a
     * stray swipe cannot permanently resize the writing surface.
     */
    private fun goFullscreen() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun Intent.noteIdExtra(): Long? =
        getLongExtra(EXTRA_OPEN_NOTE_ID, -1L).takeIf { it > 0 }

    companion object {
        const val EXTRA_OPEN_NOTE_ID = "open_note_id"
        private const val STYLUS_TERTIARY_BUTTON_MASK = 128
        private const val XIAOMI_PRIMARY_SCAN_CODE = 78
        // Android commonly exposes Linux KEY_PAGEUP as 104; some builds expose the raw HID
        // usage 0x4b (75), while older mappings used the adjacent 0x4a (74).
        private const val XIAOMI_SECONDARY_SCAN_CODE = 104
        private const val XIAOMI_SECONDARY_HID_SCAN_CODE = 75
        private const val XIAOMI_SECONDARY_LEGACY_SCAN_CODE = 74
        private const val XIAOMI_TERTIARY_SCAN_CODE = 55
        private const val XIAOMI_PRIMARY_PAGE_SCAN_CODE = 109
        private const val SCAN_F13 = 183
        private const val SCAN_F14 = 184
        private const val SCAN_F15 = 185
        private const val SCAN_F16 = 186
        private const val SCAN_F17 = 187
        private const val SCAN_F18 = 188
        private const val SCAN_F19 = 189
        private const val SCAN_F20 = 190
        private const val SCAN_F21 = 191
        // Android exposes R1/R2 constants but omits R3 even though the key code is defined.
        private const val KEYCODE_BUTTON_R3 = 105
        private const val XIAOMI_VENDOR_ID = 0x0022
        private const val XIAOMI_PRODUCT_ID = 0x4d80
        private const val DEVICE_ID_MASK = -0x100000000L
    }
}
