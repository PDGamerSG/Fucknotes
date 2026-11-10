package com.example.fucknotes

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fucknotes.data.ink.PageSpec
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.ui.editor.EditorChrome
import com.example.fucknotes.ui.editor.EditorUiState
import com.example.fucknotes.ui.editor.InkEditor
import com.example.fucknotes.ui.editor.ink.InkCamera
import com.example.fucknotes.ui.editor.ink.rememberInkCamera
import com.example.fucknotes.ui.theme.FucknotesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Recreates a real editor surface with new window bounds, without touching stored notes. */
@RunWith(AndroidJUnit4::class)
class EditorViewportRestorationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun sixthPdfPageAndZoomSurviveSavedStateRestoreInBothOrientations() {
        val restoration = StateRestorationTester(compose)
        var portrait = false
        lateinit var camera: InkCamera
        val page = PageSpec(customWidth = 600f, customHeight = 800f)
        val state = EditorUiState(
            loaded = true, noteId = 101, type = NoteType.NOTEBOOK, page = page, inkMode = true,
        )
        restoration.setContent {
            val restoredCamera = rememberInkCamera(state.noteId)
            SideEffect { camera = restoredCamera }
            FucknotesTheme {
                Box(Modifier.requiredSize(if (portrait) 220.dp else 350.dp, if (portrait) 350.dp else 220.dp)) {
                    InkEditor(
                        state = state, strokes = { emptyList() }, camera = restoredCamera,
                        canUndo = false, canRedo = false, pageCount = { 10 },
                        onStrokesFinished = {}, onErase = {}, onToggleEraser = {},
                        onUndo = {}, onRedo = {}, onSelectPen = {}, onEditPen = { _, _, _ -> },
                        onSelectTool = {}, onFingerInputMode = {}, onToggleZoomLock = {},
                        chrome = EditorChrome.READING, onCycleChrome = {}, onAppendPage = { 9 },
                        onBack = {}, onPages = {}, attachments = emptyList(), playingPath = null,
                        onPlayAttachment = {}, onRemoveAttachment = {}, onMore = {},
                    )
                }
            }
        }

        var centre = Offset.Zero
        var zoom = 0f
        compose.runOnIdle {
            camera.goToPage(5, page)
            camera.transformBy(1.5f, viewportCentre(camera), Offset(-50f, -40f))
            centre = camera.screenToWorld(viewportCentre(camera))
            zoom = camera.zoom
        }
        compose.onNodeWithContentDescription("Page 6 of 10").assertExists()

        portrait = true
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Page 6 of 10").assertExists()
        compose.runOnIdle { assertViewport(camera, centre, zoom) }

        portrait = false
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Page 6 of 10").assertExists()
        compose.runOnIdle { assertViewport(camera, centre, zoom) }
    }

    private fun viewportCentre(camera: InkCamera) =
        Offset(camera.viewportWidthPx / 2f, camera.viewportHeightPx / 2f)

    private fun assertViewport(camera: InkCamera, centre: Offset, zoom: Float) {
        val actual = camera.screenToWorld(viewportCentre(camera))
        assertEquals(centre.x, actual.x, 0.001f)
        assertEquals(centre.y, actual.y, 0.001f)
        assertEquals(zoom, camera.zoom, 0.001f)
    }
}
