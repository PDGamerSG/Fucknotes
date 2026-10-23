package com.example.fucknotes

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.ink.authoring.InProgressShapesView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fucknotes.data.ink.InkStroke
import com.example.fucknotes.data.ink.PageSpec
import com.example.fucknotes.data.model.FingerInputMode
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.ui.editor.ink.InkCamera
import com.example.fucknotes.ui.editor.ink.InkSurface
import com.example.fucknotes.ui.theme.FucknotesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Switching to the eraser and back must not rebuild the wet-ink surface: rebuilding it is what
 * made the first strokes after the switch invisible until they were committed.
 */
@OptIn(androidx.ink.authoring.ExperimentalCustomShapeWorkflowApi::class)
@RunWith(AndroidJUnit4::class)
class WetLayerSurvivesToolSwitchTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    // Ink 1.0.0's compose wrapper hosts the generic shapes view, not InProgressStrokesView.
    private fun wetView(): InProgressShapesView<*, *, *>? {
        fun find(view: View): InProgressShapesView<*, *, *>? {
            if (view is InProgressShapesView<*, *, *>) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        return find(compose.activity.window.decorView)
    }

    @Test fun theSameWetViewServesPenEraserAndPenAgain() {
        var tool by mutableStateOf(InkTool.PEN)
        val camera = InkCamera(1f)
        compose.setContent {
            FucknotesTheme {
                InkSurface(
                    strokes = { emptyList<InkStroke>() }, camera = camera, tool = tool,
                    colorArgb = 0xFF2F6BFF.toInt(), widthDp = 3f, enabled = true,
                    fingerInputMode = FingerInputMode.HAND, zoomLocked = false, notebook = false,
                    paperStyle = PaperStyle.PLAIN, pageSpec = PageSpec.Default,
                    ruleColor = Color.Gray, ruleSpacingDp = 24f, deskColor = Color.Gray,
                    onStrokesFinished = {}, onErase = {}, onToggleEraser = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        compose.waitForIdle()
        val first = wetView()
        assertNotNull("the wet layer exists with a pen in hand", first)

        tool = InkTool.ERASER
        compose.waitForIdle()
        assertSame("the eraser keeps the wet layer alive", first, wetView())

        tool = InkTool.PAN
        compose.waitForIdle()
        assertSame("the hand keeps it too", first, wetView())

        tool = InkTool.PEN
        compose.waitForIdle()
        assertSame("a pen picked again writes on the same surface", first, wetView())
        assertEquals(View.VISIBLE, wetView()!!.visibility)
    }
}
