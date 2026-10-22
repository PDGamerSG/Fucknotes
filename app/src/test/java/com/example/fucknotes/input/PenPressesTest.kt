package com.example.fucknotes.input

import com.example.fucknotes.data.model.PenButton
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every packet pattern the Focus Pen has been seen to produce must come out as one press. */
class PenPressesTest {
    private val a = 1L shl 32   // HID endpoint
    private val b = 2L shl 32   // paired keyboard endpoint

    @Test fun aPlainDownUpIsOnePress() {
        val presses = PenPresses()
        assertTrue(presses.keyDown(PenButton.PRIMARY, a, 1000))
        assertFalse(presses.keyUp(PenButton.PRIMARY, a, 1250))
        assertTrue(presses.keyDown(PenButton.PRIMARY, a, 2000))
        assertFalse(presses.keyUp(PenButton.PRIMARY, a, 2100))
    }

    @Test fun aDownFromOneEndpointAndAnUpFromAnotherIsStillOnePress() {
        val presses = PenPresses()
        assertTrue(presses.keyDown(PenButton.PRIMARY, a, 1000))
        assertFalse("the other endpoint's up is the same press", presses.keyUp(PenButton.PRIMARY, b, 1400))
        assertTrue(presses.keyDown(PenButton.PRIMARY, b, 3000))
        assertFalse(presses.keyUp(PenButton.PRIMARY, a, 3200))
    }

    @Test fun bothEndpointsReportingTheDownIsOnePress() {
        val presses = PenPresses()
        assertTrue(presses.keyDown(PenButton.PRIMARY, a, 1000))
        assertFalse(presses.keyDown(PenButton.PRIMARY, b, 1004))
        assertFalse(presses.keyUp(PenButton.PRIMARY, a, 1300))
        assertFalse("a second up for the same press stays quiet", presses.keyUp(PenButton.PRIMARY, b, 1301))
    }

    @Test fun repeatsWhileHeldNeverCount() {
        val presses = PenPresses()
        assertTrue(presses.keyDown(PenButton.PRIMARY, a, 1000))
        assertFalse(presses.keyDown(PenButton.PRIMARY, a, 1500, repeat = true))
        assertFalse(presses.keyDown(PenButton.PRIMARY, a, 2000, repeat = true))
        assertFalse(presses.keyUp(PenButton.PRIMARY, a, 2100))
    }

    @Test fun upOnlyFirmwareCountsEachUp() {
        val presses = PenPresses()
        assertTrue(presses.keyUp(PenButton.PRIMARY, a, 1000))
        assertTrue(presses.keyUp(PenButton.PRIMARY, a, 1600))
        assertFalse("but not within a blink", presses.keyUp(PenButton.PRIMARY, a, 1650))
    }

    @Test fun aMotionButtonPacketForAHeldKeyIsTheSamePress() {
        val presses = PenPresses()
        assertTrue(presses.keyDown(PenButton.PRIMARY, a, 1000))
        assertFalse(presses.motionPress(PenButton.PRIMARY, b, 1200))
        assertFalse(presses.keyUp(PenButton.PRIMARY, a, 1400))
        presses.motionRelease(PenButton.PRIMARY)
        // And the other way round.
        assertTrue(presses.motionPress(PenButton.PRIMARY, b, 3000))
        assertFalse(presses.keyDown(PenButton.PRIMARY, a, 3050))
    }

    @Test fun aLostReleaseDoesNotKillTheButton() {
        val presses = PenPresses()
        assertTrue(presses.keyDown(PenButton.PRIMARY, a, 1000))
        // No up ever arrives; well after the stale window a new down is a new press.
        assertTrue(presses.keyDown(PenButton.PRIMARY, a, 1000 + PenPresses.STALE_AFTER_MS + 1))
    }

    @Test fun differentButtonsAreIndependent() {
        val presses = PenPresses()
        assertTrue(presses.keyDown(PenButton.PRIMARY, a, 1000))
        assertTrue(presses.keyDown(PenButton.SECONDARY, a, 1010))
        assertFalse(presses.keyUp(PenButton.SECONDARY, a, 1200))
        assertFalse(presses.keyUp(PenButton.PRIMARY, a, 1300))
    }
}
