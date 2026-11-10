package com.example.fucknotes.ui.editor.ink

import org.junit.Assert.assertEquals
import org.junit.Test

class InkOverlayStateTest {
    @Test fun inactivePageControlsKeepAVisibleTouchTarget() {
        assertEquals(1f, controlAlpha(true), 0f)
        assertEquals(FADED_CONTROL_ALPHA, controlAlpha(false), 0f)
    }
}
