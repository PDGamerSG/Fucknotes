package com.example.fucknotes.data.model

import com.example.fucknotes.data.db.Converters
import com.example.fucknotes.ui.editor.EditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A note's own light-or-dark choice wins; only a note that never chose follows the app. */
class PageAppearanceTest {
    @Test fun aNoteStoredAsPitchBlackIsSimplyDark() {
        // Dark mode is black now; the separate name only survives in old rows and exported files.
        assertEquals(PageAppearance.DARK, Converters().stringToAppearance("PITCH_BLACK"))
        assertEquals(PageAppearance.DARK, PageAppearance.fromStored("PITCH_BLACK"))
        assertEquals(listOf("FOLLOW", "LIGHT", "DARK"), PageAppearance.entries.map { it.name })
    }

    @Test fun darkPaperIsBlack() {
        assertEquals(0xFF000000.toInt(), com.example.fucknotes.ui.editor.ink.NightPalette.paper.value.shr(32).toInt())
    }

    @Test fun onlyLightPaperIsOfferedWhenANotebookIsMade() {
        PaperColors.forEach { argb ->
            val lightness = com.example.fucknotes.ui.editor.ink.rgbToHsl(argb).third
            assertTrue("paper ${Integer.toHexString(argb)} must be light", lightness > 0.8f)
        }
    }

    @Test fun ownChoiceWinsOverTheAppSetting() {
        assertTrue(PageAppearance.DARK.isDark(appDarkReader = false))
        assertFalse(PageAppearance.LIGHT.isDark(appDarkReader = true))
        assertTrue(PageAppearance.FOLLOW.isDark(appDarkReader = true))
        assertFalse(PageAppearance.FOLLOW.isDark(appDarkReader = false))
        assertEquals(PageAppearance.DARK, PageAppearance.of(true))
        assertEquals(PageAppearance.LIGHT, PageAppearance.of(false))
    }

    @Test fun editorStateResolvesThroughTheNoteFirst() {
        assertFalse(EditorUiState(appearance = PageAppearance.LIGHT, appDarkReader = true).darkReader)
        assertTrue(EditorUiState(appearance = PageAppearance.DARK, appDarkReader = false).darkReader)
        assertTrue(EditorUiState(appearance = PageAppearance.FOLLOW, appDarkReader = true).darkReader)
        assertFalse(EditorUiState().darkReader)
    }

    @Test fun aNewNotebookIsLight() {
        assertEquals(PageAppearance.LIGHT, NotebookDraft().appearance)
        assertFalse(NotebookDraft().appearance.isDark(appDarkReader = true))
    }

    @Test fun storedNamesRoundTripAndUnknownOnesFollowTheApp() {
        val c = Converters()
        PageAppearance.entries.forEach { assertEquals(it, c.stringToAppearance(c.appearanceToString(it))) }
        assertEquals(PageAppearance.FOLLOW, c.stringToAppearance("sepia"))
    }
}
