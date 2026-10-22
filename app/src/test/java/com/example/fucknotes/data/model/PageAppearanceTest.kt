package com.example.fucknotes.data.model

import com.example.fucknotes.data.db.Converters
import com.example.fucknotes.ui.editor.EditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A note's own light-or-dark choice wins; only a note that never chose follows the app. */
class PageAppearanceTest {
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

    @Test fun aDraftIsLightUnlessTheSheetSaysOtherwise() {
        assertEquals(PageAppearance.LIGHT, NotebookDraft().appearance)
        assertEquals(PageAppearance.DARK, NotebookDraft(appearance = PageAppearance.of(true)).appearance)
    }

    @Test fun storedNamesRoundTripAndUnknownOnesFollowTheApp() {
        val c = Converters()
        PageAppearance.entries.forEach { assertEquals(it, c.stringToAppearance(c.appearanceToString(it))) }
        assertEquals(PageAppearance.FOLLOW, c.stringToAppearance("sepia"))
    }
}
