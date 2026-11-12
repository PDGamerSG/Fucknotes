package com.example.fucknotes.ui.editor.ink

import com.example.fucknotes.data.ink.InkCodec
import com.example.fucknotes.data.model.DefaultPenTray
import com.example.fucknotes.data.model.InkColors
import com.example.fucknotes.data.model.InkTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Strokes that reached a note in the colour they were drawn in on a dark page.
 *
 * The three colours below were read out of the owner's notebook on 2026-11-13: 66 strokes among
 * 6,989, each sitting in a run of ordinary strokes from the pen it was written with.
 */
class NightSlipTest {
    private val WHITE = 0xFFFFFFFF.toInt()

    @Test fun aStoredNightColourGoesBackToThePenItWasWrittenWith() {
        assertEquals(0xFF171B23.toInt(), daylightOfNightSlip(0xFFDBDEE6.toInt(), InkTool.PEN))
        assertEquals(0xFF2386DC.toInt(), daylightOfNightSlip(0xFF2A85D5.toInt(), InkTool.PEN))
        assertEquals(0xFFE84158.toInt(), daylightOfNightSlip(0xFFB71E33.toInt(), InkTool.PEN))
    }

    @Test fun everyNightFormOfAPalettePenIsRecognised() {
        InkColors.filter { it != WHITE }.forEach { day ->
            val night = darkReaderArgb(day, InkTool.PEN)
            if (night !in KnownInks) assertEquals(day, daylightOfNightSlip(night, InkTool.PEN))
        }
    }

    /**
     * Black is the night form of the white pen, and it is also simply black: ink that came from
     * somewhere other than the tray. Turning that white would make it vanish from a light page,
     * so black is never taken for a slip. Found by NotebookRenderingTest on the tablet.
     */
    @Test fun blackInkStaysBlack() {
        assertEquals(0xFF000000.toInt(), darkReaderArgb(WHITE, InkTool.PEN))
        assertNull(daylightOfNightSlip(0xFF000000.toInt(), InkTool.PEN))
        assertNull(daylightOfNightSlip(0xFF000000.toInt(), InkTool.MARKER))
        assertNull(daylightOfNightSlip(0x40000000, InkTool.HIGHLIGHTER))
    }

    @Test fun aColourAPenCanHoldIsNeverRewritten() {
        (InkColors + DefaultPenTray.map { it.colorArgb }).forEach { argb ->
            assertNull(daylightOfNightSlip(argb, InkTool.PEN))
            assertNull(daylightOfNightSlip(argb, InkTool.HIGHLIGHTER))
        }
    }

    @Test fun anUnknownColourIsLeftAlone() {
        assertNull(daylightOfNightSlip(0xFF123456.toInt(), InkTool.PEN))
        assertNull(daylightOfNightSlip(0xFFDBDEE7.toInt(), InkTool.PEN))
    }

    @Test fun alphaStaysWithTheStroke() {
        val day = 0xFFFFDF43.toInt()
        val night = darkReaderArgb(0x40FFDF43, InkTool.HIGHLIGHTER)
        if ((night or 0xFF000000.toInt()) !in KnownInks) {
            assertEquals(0x40000000 or (day and 0xFFFFFF), daylightOfNightSlip(night, InkTool.HIGHLIGHTER))
        }
    }

    @Test fun readingANoteRepairsTheStoredColour() {
        fun stroke(colour: Int) =
            """{"inputs":"","tool":"PEN","colorArgb":$colour,"size":1.9765482,"epsilon":0.036}"""
        val raw = """{"version":1,"strokes":[${stroke(0xFF171B23.toInt())},${stroke(0xFFDBDEE6.toInt())},${stroke(0xFF2A85D5.toInt())}]}"""
        assertEquals(
            listOf(0xFF171B23.toInt(), 0xFF171B23.toInt(), 0xFF2386DC.toInt()),
            InkCodec.parse(raw).map { it.colorArgb },
        )
    }
}
