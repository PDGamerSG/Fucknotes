package com.example.fucknotes.input

import org.junit.Assert.assertEquals
import org.junit.Test

class PenMotionButtonsTest {
    private val primary = 32
    private val secondary = 64

    @Test fun onePressAcrossDelayedStateTouchAndRepeatedHover() {
        val tracker = PenMotionButtons()
        assertEquals(primary, tracker.update(1, 0, primary, false, false))
        assertEquals(0, tracker.update(1, primary, 0, false, false))
        // Lifting the tip while a side switch is still down keeps the same mask.
        repeat(10) { assertEquals(0, tracker.update(1, primary, primary, false, false)) }
        assertEquals(0, tracker.update(1, primary, primary, true, false))
        assertEquals(primary, tracker.update(1, 0, primary, false, false))
    }

    @Test fun releaseNeverBecomesPressAndKeepsOtherHeldButtons() {
        val tracker = PenMotionButtons()
        assertEquals(0, tracker.update(1, primary, primary, true, false))
        assertEquals(primary, tracker.update(1, primary, 0, false, false))
        assertEquals(secondary, tracker.update(1, primary or secondary, secondary, false, false))
        assertEquals(0, tracker.update(1, primary or secondary, secondary, true, false))
        assertEquals(0, tracker.update(1, primary, 0, false, false))
    }

    @Test fun devicesAreIndependentAndFocusResetAllowsNextPress() {
        val tracker = PenMotionButtons()
        assertEquals(primary, tracker.update(1, primary, 0, false, false))
        assertEquals(primary, tracker.update(2, primary, 0, false, false))
        assertEquals(0, tracker.update(1, primary, 0, false, true))
        assertEquals(primary, tracker.update(1, primary, 0, false, false))
        tracker.clear()
        assertEquals(primary, tracker.update(2, primary, 0, false, false))
    }
}
