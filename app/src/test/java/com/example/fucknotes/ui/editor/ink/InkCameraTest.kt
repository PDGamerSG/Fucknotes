package com.example.fucknotes.ui.editor.ink

import androidx.compose.ui.geometry.Offset
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
}
