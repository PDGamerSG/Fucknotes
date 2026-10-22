package com.example.fucknotes.ui.editor.ink

import com.example.fucknotes.data.model.InkTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkReaderTest {
    private fun lightness(argb: Int) = rgbToHsl(argb).third
    private fun hue(argb: Int) = rgbToHsl(argb).first

    @Test fun blackInkBecomesSoftOffWhiteAndWhiteBecomesDark() {
        val night = darkReaderArgb(0xFF000000.toInt())
        assertTrue("black ink must read on dark paper", lightness(night) > 0.8f)
        assertTrue("but not glare", lightness(night) <= NightPalette.MAX_LIGHTNESS + 0.01f)
        assertTrue(lightness(darkReaderArgb(0xFFFFFFFF.toInt())) < 0.15f)
    }

    @Test fun alphaIsPreserved() {
        assertEquals(0x80, darkReaderArgb(0x80BBCCDD.toInt()) ushr 24)
        assertEquals(0x40, darkReaderArgb(0x40FFDF43, InkTool.HIGHLIGHTER) ushr 24)
    }

    @Test fun hueSurvivesSoBlueInkStaysBlue() {
        val blue = 0xFF2386DC.toInt()
        val night = darkReaderArgb(blue)
        assertTrue(hueDistance(hue(blue), hue(night)) < 0.03f)
        val red = 0xFFE84158.toInt()
        assertTrue(hueDistance(hue(red), hue(darkReaderArgb(red))) < 0.03f)
    }

    @Test fun highlightsStayLightInsteadOfTurningToSmears() {
        val yellow = 0xFFFFDF43.toInt()
        assertTrue(lightness(darkReaderArgb(yellow, InkTool.HIGHLIGHTER)) >= NightPalette.MIN_HIGHLIGHT_LIGHTNESS - 0.01f)
        assertTrue(hueDistance(hue(yellow), hue(darkReaderArgb(yellow, InkTool.HIGHLIGHTER))) < 0.03f)
        // The same yellow as pen ink is inverted like any other colour.
        assertTrue(lightness(darkReaderArgb(yellow, InkTool.PEN)) < 0.5f)
    }

    @Test fun imageMatrixTurnsPaperDarkTextLightAndKeepsHue() {
        val paper = NightPalette.applyImageMatrix(0xFFFFFFFF.toInt())
        assertTrue("white paper lands on the slate", lightness(paper) < 0.15f)
        val text = NightPalette.applyImageMatrix(0xFF000000.toInt())
        assertTrue("black text reads light but not pure white", lightness(text) in 0.85f..0.98f)
        val blue = NightPalette.applyImageMatrix(0xFF0000FF.toInt())
        assertTrue("blue stays blue", (blue and 0xFF) > ((blue ushr 16) and 0xFF) && (blue and 0xFF) > ((blue ushr 8) and 0xFF))
        assertEquals(0x80, NightPalette.applyImageMatrix(0x80123456.toInt()) ushr 24)
    }

    @Test fun hslRoundTripsThroughTheConverters() {
        listOf(0xFF2386DC.toInt(), 0xFFE84158.toInt(), 0xFF299B62.toInt(), 0xFF7B818A.toInt(), 0xFFFFFFFF.toInt()).forEach { argb ->
            val (h, s, l) = rgbToHsl(argb)
            val back = hslToRgb(h, s, l) or 0xFF000000.toInt()
            listOf(16, 8, 0).forEach { shift ->
                assertTrue(kotlin.math.abs(((argb ushr shift) and 0xFF) - ((back ushr shift) and 0xFF)) <= 1)
            }
        }
    }
}
