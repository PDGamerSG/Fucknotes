package com.example.fucknotes

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fucknotes.data.ink.InkCodec
import com.example.fucknotes.data.ink.InkShape
import com.example.fucknotes.data.model.*
import com.example.fucknotes.ui.editor.*
import com.example.fucknotes.ui.editor.ink.InkCamera
import com.example.fucknotes.ui.home.NewNotebookDialog
import com.example.fucknotes.ui.theme.FucknotesTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real toolbar -> pointer input -> ViewModel -> stored vectors, on an isolated scratch note. */
@RunWith(AndroidJUnit4::class)
class EditorDrawingToolsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun renderedShapesKeepTheirCornersAndArrowWingsAfterReopening() {
        withEditor { vm, camera ->
            val fixtures = listOf(
                Triple("Rectangle", Offset(180f, 360f), listOf(Offset(60f, 260f), Offset(180f, 260f), Offset(180f, 360f), Offset(60f, 360f))),
                Triple("Arrow", Offset(180f, 260f), listOf(Offset(60f, 260f), Offset(180f, 260f), Offset(153.6f, 245.48f), Offset(153.6f, 274.52f))),
                Triple("Circle", Offset(180f, 380f), listOf(Offset(180f, 320f), Offset(120f, 380f), Offset(60f, 320f), Offset(120f, 260f))),
                Triple("Line", Offset(180f, 360f), listOf(Offset(60f, 260f), Offset(120f, 310f), Offset(180f, 360f))),
            )
            val renderer = CanvasStrokeRenderer.create()
            for ((label, end, landmarks) in fixtures) {
                compose.onNodeWithContentDescription("Shapes").performClick()
                compose.onNodeWithText(label).performClick()
                draw(camera, Offset(60f, 260f), end)
                runBlocking { vm.saveNow() }
                val app = InstrumentationRegistry.getInstrumentation().targetContext
                val saved = runBlocking { ServiceLocator.notes(app).getNote(vm.state.value.noteId) }!!
                val reopened = InkCodec.decode(saved.drawingJson).last().stroke
                for ((stage, stroke) in listOf("drawn" to vm.strokes.last().stroke, "reopened" to reopened)) {
                    val bitmap = Bitmap.createBitmap(240, 420, Bitmap.Config.ARGB_8888)
                    try {
                        renderer.draw(Canvas(bitmap), stroke, Matrix())
                        landmarks.forEach { point ->
                            val hasInk = (-2..2).any { dx -> (-2..2).any { dy ->
                                android.graphics.Color.alpha(bitmap.getPixel(point.x.toInt() + dx, point.y.toInt() + dy)) > 128
                            } }
                            assertTrue("$label $stage must reach $point without handwriting smoothing", hasInk)
                        }
                    } finally { bitmap.recycle() }
                }
            }
        }
    }

    @Test fun eachShapeCommitsWhenThePenLiftsWhileThePalmStaysDown() {
        withEditor { vm, camera ->
            compose.onNodeWithContentDescription("Shapes").performClick()
            compose.onNodeWithText("Rectangle").performClick()
            val origin = compose.onNodeWithTag("editor").fetchSemanticsNode().boundsInWindow.topLeft
            val start = SystemClock.uptimeMillis()
            val palm = MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER }
            val pen = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
            fun send(action: Int, point: Offset? = null) {
                fun coords(world: Offset) = MotionEvent.PointerCoords().apply {
                    x = origin.x + world.x * camera.scale + camera.panX
                    y = origin.y + world.y * camera.scale + camera.panY
                    pressure = 0.7f
                }
                val properties = if (point == null) arrayOf(palm) else arrayOf(palm, pen)
                val coordinates = if (point == null) arrayOf(coords(Offset(250f, 500f)))
                    else arrayOf(coords(Offset(250f, 500f)), coords(point))
                val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, properties.size,
                    properties, coordinates, 0, 0, 1f, 1f, 0, 0,
                    InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN, 0)
                try { compose.runOnUiThread { compose.activity.dispatchTouchEvent(event) } }
                finally { event.recycle() }
            }
            send(MotionEvent.ACTION_DOWN)
            repeat(2) { index ->
                val from = Offset(60f, 260f + index * 140f)
                val to = from + Offset(100f, 70f)
                send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), from)
                send(MotionEvent.ACTION_MOVE, to)
                send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), to)
                compose.runOnIdle { assertEquals("Pen lift must commit before the palm lifts", index + 1, vm.strokes.size) }
            }
            send(MotionEvent.ACTION_UP)
        }
    }

    @Test fun pinchBeforeChoosingShapeDoesNotSwallowItsFirstStroke() {
        withEditor { vm, camera ->
            compose.onNodeWithTag("editor").performTouchInput {
                pinch(Offset(100f, 650f), Offset(300f, 650f), Offset(80f, 650f), Offset(320f, 650f))
            }
            compose.onNodeWithContentDescription("Shapes").performClick()
            compose.onNodeWithText("Line").performClick()
            draw(camera, Offset(60f, 260f), Offset(180f, 360f))
            compose.runOnIdle { assertEquals(InkShape.LINE, vm.strokes.single().shape) }
        }
    }

    @Test fun shapeCanBeDrawnSelectedMovedUndoneAndReopened() {
        withEditor { vm, camera ->
            compose.onNodeWithContentDescription("Shapes").performClick()
            compose.onNodeWithText("Rectangle").performClick()
            draw(camera, Offset(60f, 260f), Offset(180f, 360f))
            compose.runOnIdle {
                assertEquals(1, vm.strokes.size)
                assertEquals(InkShape.RECTANGLE, vm.strokes.single().shape)
                assertTrue(vm.canUndoInk)
            }
            compose.onNodeWithContentDescription("Select: circle strokes to move them").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(InkTool.LASSO, vm.state.value.tool) }
            val loop = listOf(Offset(45f, 245f), Offset(195f, 245f), Offset(195f, 375f), Offset(45f, 375f), Offset(45f, 245f))
            compose.onNodeWithTag("editor").performTouchInput {
                val points = loop.map { Offset(it.x * camera.scale + camera.panX, it.y * camera.scale + camera.panY) }
                down(points.first()); points.drop(1).forEach { moveTo(it) }; up()
            }
            compose.onNodeWithContentDescription("Duplicate the circled strokes").assertIsDisplayed()
            draw(camera, Offset(120f, 310f), Offset(145f, 340f))
            compose.onNodeWithContentDescription("Undo").performClick()
            runBlocking { vm.saveNow() }
            val app = InstrumentationRegistry.getInstrumentation().targetContext
            val saved = runBlocking { ServiceLocator.notes(app).getNote(vm.state.value.noteId) }!!
            val restored = InkCodec.decode(saved.drawingJson).single()
            assertEquals(InkShape.RECTANGLE, restored.shape)
            assertEquals(vm.strokes.single().stroke.inputs.size, restored.stroke.inputs.size)
            compose.onNodeWithContentDescription("Undo").performClick()
            compose.runOnIdle { assertTrue(vm.strokes.isEmpty()) }
            compose.onNodeWithContentDescription("Redo").performClick()
            compose.runOnIdle { assertEquals(InkShape.RECTANGLE, vm.strokes.single().shape) }
        }
    }

    @Test fun temporaryRedHighlightIsVisibleThenDisappearsWithoutSavingOrUndo() {
        withEditor { vm, camera ->
            compose.onNodeWithContentDescription("Temporary red highlighter (fades after 2.5 seconds)").performClick()
            compose.runOnIdle { assertEquals(InkTool.LASER, vm.state.value.tool) }
            compose.mainClock.autoAdvance = false
            draw(camera, Offset(60f, 260f), Offset(180f, 360f))
            compose.mainClock.advanceTimeBy(64)
            fun hasRed(): Boolean {
                val pixels = compose.onNodeWithTag("editor").captureToImage().toPixelMap()
                val x = (120f * camera.scale + camera.panX).toInt()
                val y = (310f * camera.scale + camera.panY).toInt()
                return (-4..4).any { dx -> (-4..4).any { dy ->
                    val color = pixels[x + dx, y + dy]
                    color.red > 0.65f && color.red > color.green * 1.5f
                } }
            }
            assertTrue("The temporary highlight must render immediately (scale=${camera.scale}, pan=${camera.panX},${camera.panY})", hasRed())
            compose.waitUntil(timeoutMillis = 5000) { compose.mainClock.advanceTimeBy(50); !hasRed() }
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { assertTrue(vm.strokes.isEmpty()); assertFalse(vm.canUndoInk) }
            runBlocking { vm.saveNow() }
            val app = InstrumentationRegistry.getInstrumentation().targetContext
            val saved = runBlocking { ServiceLocator.notes(app).getNote(vm.state.value.noteId) }!!
            assertEquals("", saved.drawingJson)
        }
    }

    @Test fun compactToolbarKeepsUndoAndSelectAvailable() {
        withEditor { vm, camera ->
            compose.onNodeWithContentDescription("Shapes").performClick()
            compose.onNodeWithText("Line").performClick()
            draw(camera, Offset(60f, 260f), Offset(180f, 360f))
            compose.onNodeWithContentDescription(EditorChrome.FULL.lockLabel).performClick()
            compose.onNodeWithContentDescription("Select: circle strokes to move them").assertIsDisplayed()
            compose.onNodeWithContentDescription("Undo").assertIsDisplayed().performClick()
            compose.runOnIdle { assertTrue(vm.strokes.isEmpty()) }
        }
    }

    @Test fun pitchBlackRendersAsBlackAndPersists() {
        withEditor { vm, _ ->
            compose.runOnIdle { vm.setPageAppearance(PageAppearance.PITCH_BLACK) }
            val pixels = compose.onNodeWithTag("editor").captureToImage().toPixelMap()
            assertEquals(Color.Black, pixels[pixels.width / 2, pixels.height / 2])
            runBlocking { vm.saveNow() }
            val app = InstrumentationRegistry.getInstrumentation().targetContext
            val saved = runBlocking { ServiceLocator.notes(app).getNote(vm.state.value.noteId) }!!
            assertEquals(PageAppearance.PITCH_BLACK, saved.appearance)
        }
    }

    @Test fun newNotebookOffersPitchBlack() {
        var draft: NotebookDraft? = null
        compose.setContent {
            FucknotesTheme { NewNotebookDialog("Black page", {}, { draft = it }) }
        }
        compose.onNodeWithContentDescription("Pitch black mode").performClick()
        compose.onNodeWithText("Create").performClick()
        assertEquals(PageAppearance.PITCH_BLACK, draft?.appearance)
    }

    private fun withEditor(test: (EditorViewModel, InkCamera) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val repo = ServiceLocator.notes(app)
        val settings = ServiceLocator.settings(app)
        val previousFingerMode = runBlocking { settings.settings.first().fingerInputMode }
        val id = runBlocking { repo.createNote(NoteType.DRAWING, null, "Drawing tools test") }
        val store = ViewModelStore()
        lateinit var vm: EditorViewModel
        val camera = InkCamera(1f)
        compose.runOnUiThread { vm = EditorViewModel(app, id); store.put("editor", vm) }
        try {
            compose.waitUntil(10000) { vm.state.value.loaded }
            compose.runOnUiThread { vm.setFingerInputMode(FingerInputMode.DRAW) }
            compose.setContent {
                val state by vm.state.collectAsState()
                var chrome by remember { mutableStateOf(EditorChrome.FULL) }
                val strokes = remember { { vm.strokes } }
                FucknotesTheme(darkReader = state.darkReader) {
                    InkEditor(
                        state, strokes, camera, vm.canUndoInk, vm.canRedoInk, { vm.pageCount },
                        vm::commitStrokes, vm::eraseStrokes, vm::toggleEraser, vm::undo, vm::redo,
                        onMoveStrokes = vm::moveStrokes, onDuplicateStrokes = vm::duplicateStrokes,
                        onSelectPen = vm::selectPen, onEditPen = vm::editPen, onSelectTool = vm::setTool,
                        onFingerInputMode = vm::setFingerInputMode, onToggleZoomLock = vm::toggleZoomLock,
                        chrome = chrome, onCycleChrome = { chrome = chrome.next() }, onAppendPage = vm::appendPage,
                        onBack = {}, onPages = {}, attachments = emptyList(), playingPath = null,
                        onPlayAttachment = {}, onRemoveAttachment = {}, onMore = {},
                        modifier = Modifier.testTag("editor"),
                        onSelectShape = vm::selectShape, onShapeFinished = vm::commitShape,
                    )
                }
            }
            test(vm, camera)
        } finally {
            runBlocking { vm.saveNow() }
            compose.runOnUiThread { store.clear() }
            runBlocking { repo.deleteForever(listOf(id)); settings.setFingerInputMode(previousFingerMode) }
        }
    }

    private fun draw(camera: InkCamera, from: Offset, to: Offset) {
        compose.onNodeWithTag("editor").performTouchInput {
            fun screen(point: Offset) = Offset(point.x * camera.scale + camera.panX, point.y * camera.scale + camera.panY)
            down(screen(from))
            moveTo(screen(from + (to - from) * 0.5f))
            moveTo(screen(to))
            up()
        }
        compose.waitForIdle()
    }
}
