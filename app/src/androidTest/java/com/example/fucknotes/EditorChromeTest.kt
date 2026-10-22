package com.example.fucknotes

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fucknotes.data.ink.InkStroke
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.ui.editor.EditorChrome
import com.example.fucknotes.ui.editor.EditorUiState
import com.example.fucknotes.ui.editor.InkEditor
import com.example.fucknotes.ui.editor.ink.InkCamera
import com.example.fucknotes.ui.theme.FucknotesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The lock in the pen bar steps the notebook chrome through its three states, and each state shows
 * exactly the controls it promises. Runs on an isolated surface; no stored note is touched.
 */
@RunWith(AndroidJUnit4::class)
class EditorChromeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun lockCyclesControlsPenBarAndReadingWithPageCounterAlwaysPresent() {
        val camera = InkCamera(1f)
        var chrome by mutableStateOf(EditorChrome.FULL)
        var pages by mutableStateOf(3)
        val state = EditorUiState(loaded = true, noteId = 1, type = NoteType.NOTEBOOK, inkMode = true)
        compose.setContent {
            FucknotesTheme {
                InkEditor(
                    state = state, strokes = { emptyList<InkStroke>() }, camera = camera,
                    canUndo = false, canRedo = false, pageCount = { pages },
                    onStrokesFinished = {}, onErase = {}, onToggleEraser = {}, onUndo = {}, onRedo = {},
                    onSelectPen = {}, onEditPen = { _, _, _ -> }, onSelectTool = {}, onFingerInputMode = {},
                    onToggleZoomLock = {}, chrome = chrome, onCycleChrome = { chrome = chrome.next() },
                    onAppendPage = { pages += 1; pages - 1 }, onBack = {}, onPages = {},
                    attachments = emptyList(), playingPath = null, onPlayAttachment = {},
                    onRemoveAttachment = {}, onMore = {},
                )
            }
        }

        // FULL: note bar, pen bar, zoom readout and page counter.
        compose.onNodeWithContentDescription("Back to notes").assertIsDisplayed()
        compose.onNodeWithContentDescription("Eraser").assertIsDisplayed()
        compose.onNodeWithContentDescription("Lock the zoom").assertIsDisplayed()
        compose.onNodeWithContentDescription("Page 1 of 3").assertIsDisplayed()

        // One tap: only the pen bar remains, plus the counter and the zoom readout.
        compose.onNodeWithContentDescription(EditorChrome.FULL.lockLabel).performClick()
        compose.runOnIdle { assertEquals(EditorChrome.COMPACT, chrome) }
        compose.onNodeWithContentDescription("Back to notes").assertDoesNotExist()
        compose.onNodeWithContentDescription("Eraser").assertIsDisplayed()
        compose.onNodeWithContentDescription("Page 1 of 3").assertIsDisplayed()

        // Second tap: reading mode keeps the page counter and a single unlock button.
        compose.onNodeWithContentDescription(EditorChrome.COMPACT.lockLabel).performClick()
        compose.runOnIdle { assertEquals(EditorChrome.READING, chrome) }
        compose.onNodeWithContentDescription("Eraser").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to notes").assertDoesNotExist()
        compose.onNodeWithContentDescription("Lock the zoom").assertDoesNotExist()
        compose.onNodeWithContentDescription("Page 1 of 3").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next page").assertIsDisplayed()

        // Third tap: everything is back.
        compose.onNodeWithContentDescription(EditorChrome.READING.lockLabel).performClick()
        compose.runOnIdle { assertEquals(EditorChrome.FULL, chrome) }
        compose.onNodeWithContentDescription("Back to notes").assertIsDisplayed()
        compose.onNodeWithContentDescription("Eraser").assertIsDisplayed()

        // Past the last page the arrow adds a sheet rather than refusing.
        compose.onNodeWithContentDescription("Next page").performClick()
        compose.onNodeWithContentDescription("Next page").performClick()
        compose.onNodeWithContentDescription("Add a page").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(4, pages) }
        compose.onNodeWithContentDescription("Page 4 of 4").assertIsDisplayed()
    }
}
