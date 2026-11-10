package com.example.fucknotes.ui.editor.ink

import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.saveable.SaverScope
import com.example.fucknotes.data.ink.PageFormat
import com.example.fucknotes.data.ink.PageSpec
import org.junit.Assert.assertEquals
import org.junit.Test

class InkCameraTest {
    @Test fun typedAnnotationKeepsItsDocumentPositionAfterScrolling() {
        val camera = InkCamera(density = 2f)
        val before = camera.screenToWorld(Offset(200f, 600f))

        camera.followContentScroll(400)
        val after = camera.screenToWorld(Offset(200f, 200f))

        assertEquals(before, after)
        camera.followContentScroll(0)
        assertEquals(before, camera.screenToWorld(Offset(200f, 600f)))
    }

    @Test fun resizingTheWindowKeepsTheNotebookPositionAndZoom() {
        val page = PageSpec(PageFormat.A4)
        val first = InkCamera(density = 2f)
        first.configure(
            columnWidthDp = page.widthDp,
            viewportWidthPx = 1600f,
            viewportHeightPx = 900f,
            columnHeightDp = page.heightDp * 8,
        )
        first.goToPage(5, page)
        first.transformBy(1.5f, Offset(800f, 450f), Offset(-200f, -100f))
        val centre = first.screenToWorld(Offset(800f, 450f))
        val zoom = first.zoom

        first.configure(
            columnWidthDp = page.widthDp,
            viewportWidthPx = 900f,
            viewportHeightPx = 1400f,
            columnHeightDp = page.heightDp * 8,
        )
        val resizedCentre = first.screenToWorld(Offset(450f, 700f))
        assertEquals(centre.x, resizedCentre.x, 0.001f)
        assertEquals(centre.y, resizedCentre.y, 0.001f)
        assertEquals(zoom, first.zoom, 0.001f)
        assertEquals(5, first.pageAtCentre(page))
    }

    @Test fun recreationRestoresSixthPdfPageZoomAndPanInBothOrientations() {
        val page = PageSpec(customWidth = 600f, customHeight = 800f)
        var camera = InkCamera(2f)
        camera.configure(600f, 1600f, 900f, 8000f)
        camera.goToPage(5, page)
        camera.transformBy(1.5f, Offset(800f, 450f), Offset(-200f, -100f))
        val centre = camera.screenToWorld(Offset(800f, 450f))
        val zoom = camera.zoom

        camera = recreate(camera, density = 2.5f)
        assertEquals(5, camera.pageAtCentre(page))
        camera.configure(600f, 900f, 1400f, 8000f)
        assertCentre(camera, Offset(450f, 700f), centre)
        assertEquals(zoom, camera.zoom, 0.001f)

        camera = recreate(camera, density = 2f)
        camera.configure(600f, 1600f, 900f, 8000f)
        assertCentre(camera, Offset(800f, 450f), centre)
        assertEquals(5, camera.pageAtCentre(page))
        assertEquals(zoom, camera.zoom, 0.001f)
    }

    @Test fun canvasPanAndZoomSurviveRecreation() {
        val camera = InkCamera(2f)
        camera.configure(0f, 1200f, 800f)
        camera.transformBy(2f, Offset(600f, 400f), Offset(200f, -300f))
        val centre = camera.screenToWorld(Offset(600f, 400f))

        val restored = recreate(camera, density = 3f)
        restored.configure(0f, 800f, 1200f)

        assertCentre(restored, Offset(400f, 600f), centre)
        assertEquals(2f, restored.zoom, 0.001f)
    }

    @Test fun restoredPositionSurvivesAnotherSaveBeforeLayout() {
        val camera = InkCamera(2f)
        camera.configure(600f, 1600f, 900f, 8000f)
        camera.goToPage(5, PageSpec(customWidth = 600f, customHeight = 800f))
        val centre = camera.screenToWorld(Offset(800f, 450f))

        val restored = recreate(recreate(camera, density = 2f), density = 2f)
        restored.configure(600f, 0f, 0f, 8000f)
        restored.configure(600f, 900f, 1400f, 8000f)

        assertCentre(restored, Offset(450f, 700f), centre)
    }

    @Test fun growingNotebookDoesNotMoveTheViewport() {
        val camera = InkCamera(2f)
        camera.configure(600f, 1600f, 900f, 6400f)
        camera.goToPage(5, PageSpec(customWidth = 600f, customHeight = 800f))
        val centre = camera.screenToWorld(Offset(800f, 450f))

        camera.configure(600f, 1600f, 900f, 7200f)

        assertCentre(camera, Offset(800f, 450f), centre)
    }

    @Test fun firstNonzeroLayoutFitsNotebookWidth() {
        val camera = InkCamera(2f)
        camera.configure(600f, 0f, 0f, 8000f)
        camera.configure(600f, 900f, 1400f, 8000f)

        assertEquals(0.75f, camera.zoom, 0.001f)
        assertEquals(0f, camera.panY, 0.001f)
    }

    private fun recreate(camera: InkCamera, density: Float): InkCamera {
        val saver = InkCamera.saver(density)
        val saved = with(saver) { SaverScope { true }.save(camera) }
        return requireNotNull(saver.restore(requireNotNull(saved)))
    }

    private fun assertCentre(camera: InkCamera, screenCentre: Offset, expected: Offset) {
        val actual = camera.screenToWorld(screenCentre)
        assertEquals(expected.x, actual.x, 0.001f)
        assertEquals(expected.y, actual.y, 0.001f)
    }
}
