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
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import com.example.fucknotes.ui.editor.ink.RED_HIGHLIGHTER_LABEL
import com.example.fucknotes.ui.editor.ink.ToolbarLayout
import com.example.fucknotes.ui.editor.ink.ToolbarTool
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
                pickShape(label)
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
            pickShape("Rectangle")
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
            pickShape("Line")
            draw(camera, Offset(60f, 260f), Offset(180f, 360f))
            compose.runOnIdle { assertEquals(InkShape.LINE, vm.strokes.single().shape) }
        }
    }

    @Test fun penCanDrawAShapeAfterPinchingWithoutLiftingTheRestingHand() {
        withEditor { vm, camera ->
            pickShape("Rectangle")
            val origin = compose.onNodeWithTag("editor").fetchSemanticsNode().boundsInWindow.topLeft
            val start = SystemClock.uptimeMillis()
            val fingers = (1..2).map { pointer ->
                MotionEvent.PointerProperties().apply { id = pointer; toolType = MotionEvent.TOOL_TYPE_FINGER }
            }
            val pen = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_STYLUS }
            fun send(action: Int, point: Offset? = null, fingerCount: Int = 2) {
                val points = listOf(Offset(240f, 400f), Offset(320f, 400f)).take(fingerCount) + listOfNotNull(point)
                val properties = fingers.take(fingerCount) + listOfNotNull(pen.takeIf { point != null })
                val coordinates = points.map { world ->
                    MotionEvent.PointerCoords().apply {
                        x = origin.x + world.x * camera.scale + camera.panX
                        y = origin.y + world.y * camera.scale + camera.panY
                        pressure = 0.7f
                    }
                }
                val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, properties.size,
                    properties.toTypedArray(), coordinates.toTypedArray(), 0, 0, 1f, 1f, 0, 0,
                    InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN, 0)
                try { compose.runOnUiThread { compose.activity.dispatchTouchEvent(event) } }
                finally { event.recycle() }
            }
            // Two contacts cancel finger drawing. A new pen stroke must own a fresh gesture,
            // even when those contacts are now the palm and remain on the screen.
            send(MotionEvent.ACTION_DOWN, fingerCount = 1)
            send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT))
            send(MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), Offset(60f, 260f))
            send(MotionEvent.ACTION_MOVE, Offset(180f, 360f))
            send(MotionEvent.ACTION_POINTER_UP or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), Offset(180f, 360f))
            try {
                compose.runOnIdle { assertEquals("The pen must work while the palm stays down after a pinch", 1, vm.strokes.size) }
            } finally {
                send(MotionEvent.ACTION_CANCEL)
            }
        }
    }

    @Test fun cancellingAShapeDiscardsItsPreviewWithoutAddingInk() {
        withEditor { vm, camera ->
            pickShape("Rectangle")
            compose.onNodeWithTag("editor").performTouchInput {
                down(Offset(60f * camera.scale + camera.panX, 260f * camera.scale + camera.panY))
                moveTo(Offset(180f * camera.scale + camera.panX, 360f * camera.scale + camera.panY))
                cancel()
            }
            compose.runOnIdle { assertTrue("A cancelled shape must not be saved", vm.strokes.isEmpty()) }
            draw(camera, Offset(60f, 260f), Offset(180f, 360f))
            compose.runOnIdle { assertEquals("The next shape must still work", 1, vm.strokes.size) }
        }
    }

    @Test fun choosingPaperColorInDarkModeShowsAndSavesTheChosenColor() {
        withEditor { vm, _ ->
            compose.runOnIdle { vm.setDarkPages(true); vm.setColor(0xFFFBF3DE.toInt()) }
            fun paperPixel(): Color {
                val pixels = compose.onNodeWithTag("editor").captureToImage().toPixelMap()
                return pixels[pixels.width / 2, pixels.height / 2]
            }
            assertEquals("Choosing paper must make that color visible immediately", Color(0xFFFBF3DE), paperPixel())
            runBlocking { vm.saveNow() }
            val app = InstrumentationRegistry.getInstrumentation().targetContext
            val saved = runBlocking { ServiceLocator.notes(app).getNote(vm.state.value.noteId) }!!
            assertEquals(PageAppearance.LIGHT, saved.appearance)
            assertEquals(0xFFFBF3DE.toInt(), saved.colorArgb)
            compose.runOnIdle { vm.setDarkPages(true); vm.setColor(0) }
            assertTrue("Default paper must also leave dark mode", paperPixel().luminance() > 0.9f)
        }
    }

    @Test fun bothOverflowColorPickersRepaintTheNotebookImmediately() {
        withEditor(type = NoteType.NOTEBOOK, fullScreen = true) { vm, _ ->
            compose.runOnUiThread { vm.setDarkPages(true) }
            compose.onNodeWithContentDescription("More").performClick()
            compose.onNodeWithText("Note colour").performClick()
            compose.onNodeWithContentDescription("Colour #FFF1B8").performClick()
            fun paperPixel(): Color {
                val pixels = compose.onNodeWithTag("editor").captureToImage().toPixelMap()
                return pixels[pixels.width / 2, pixels.height / 2]
            }
            assertEquals(Color(0xFFFFF1B8), paperPixel())

            compose.runOnUiThread { vm.setDarkPages(true) }
            compose.onNodeWithContentDescription("More").performClick()
            compose.onNodeWithText("Paper").performClick()
            compose.onNodeWithText("Paper colour").assertIsDisplayed()
            compose.onNodeWithContentDescription("Colour #FBF3DE").performClick()
            compose.runOnIdle { assertFalse(vm.state.value.darkReader) }
            compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitForIdle()
            assertEquals(Color(0xFFFBF3DE), paperPixel())
            runBlocking { vm.saveNow() }
            val app = InstrumentationRegistry.getInstrumentation().targetContext
            val saved = runBlocking { ServiceLocator.notes(app).getNote(vm.state.value.noteId) }!!
            assertEquals(0xFFFBF3DE.toInt(), saved.colorArgb)
            assertEquals(PageAppearance.LIGHT, saved.appearance)
        }
    }

    @Test fun shapeCanBeDrawnSelectedMovedUndoneAndReopened() {
        withEditor { vm, camera ->
            pickShape("Rectangle")
            draw(camera, Offset(60f, 260f), Offset(180f, 360f))
            compose.runOnIdle {
                assertEquals(1, vm.strokes.size)
                assertEquals(InkShape.RECTANGLE, vm.strokes.single().shape)
                assertTrue(vm.canUndoInk)
            }
            tapTool("Select: circle strokes to move them", "Select")
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
            tapTool(RED_HIGHLIGHTER_LABEL, "Red highlighter")
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

    @Test fun earlierHighlightsStayVisibleThroughoutTheNextStroke() {
        withEditor { vm, camera ->
            tapTool(RED_HIGHLIGHTER_LABEL, "Red highlighter")
            compose.runOnIdle { assertEquals(InkTool.LASER, vm.state.value.tool) }
            compose.mainClock.autoAdvance = false
            var writing = false
            try {
                draw(camera, Offset(60f, 260f), Offset(180f, 260f))
                compose.runOnIdle { assertTrue("The first temporary stroke must not be stored: ${vm.strokes.map { it.tool }}", vm.strokes.isEmpty()) }
                compose.onNodeWithTag("editor").performTouchInput {
                    down(Offset(60f * camera.scale + camera.panX, 340f * camera.scale + camera.panY))
                    moveTo(Offset(180f * camera.scale + camera.panX, 340f * camera.scale + camera.panY))
                }
                writing = true
                val until = SystemClock.uptimeMillis() + 3000L
                compose.waitUntil(5000) {
                    compose.mainClock.advanceTimeBy(50)
                    SystemClock.uptimeMillis() >= until
                }
                val pixels = compose.onNodeWithTag("editor").captureToImage().toPixelMap()
                val x = (120f * camera.scale + camera.panX).toInt()
                val y = (260f * camera.scale + camera.panY).toInt()
                assertTrue("A previous stroke must remain while the pen is still writing", (-5..5).any { dy ->
                    val color = pixels[x, y + dy]
                    color.red > 0.65f && color.red > color.green * 1.5f
                })
                compose.onNodeWithTag("editor").performTouchInput { up() }
                writing = false
                compose.runOnIdle {
                    assertTrue("Holding the temporary highlighter must not store ink: ${vm.strokes.map { it.tool }}", vm.strokes.isEmpty())
                    assertFalse(vm.canUndoInk)
                }
            } finally {
                if (writing) compose.onNodeWithTag("editor").performTouchInput { cancel() }
                compose.mainClock.autoAdvance = true
            }
        }
    }

    @Test fun compactToolbarKeepsUndoAndSelectAvailable() {
        withEditor { vm, camera ->
            pickShape("Line")
            draw(camera, Offset(60f, 260f), Offset(180f, 360f))
            compose.onNodeWithContentDescription(EditorChrome.FULL.lockLabel).performClick()
            compose.onNodeWithContentDescription("Select: circle strokes to move them").assertIsDisplayed()
            compose.onNodeWithContentDescription("Undo").assertIsDisplayed().performClick()
            compose.runOnIdle { assertTrue(vm.strokes.isEmpty()) }
        }
    }

    @Test fun darkModeRendersAsBlackAndPersists() {
        withEditor { vm, _ ->
            compose.runOnIdle { vm.setDarkPages(true) }
            val pixels = compose.onNodeWithTag("editor").captureToImage().toPixelMap()
            assertEquals(Color.Black, pixels[pixels.width / 2, pixels.height / 2])
            runBlocking { vm.saveNow() }
            val app = InstrumentationRegistry.getInstrumentation().targetContext
            val saved = runBlocking { ServiceLocator.notes(app).getNote(vm.state.value.noteId) }!!
            assertEquals(PageAppearance.DARK, saved.appearance)
        }
    }

    @Test fun exportComesOutInTheNotesOwnMode() {
        withEditor { vm, _ ->
            /** The bottom-right corner of the exported PDF's first page: paper, never ink or title. */
            fun exportedPaper(): Int {
                val ready = java.util.concurrent.LinkedBlockingQueue<java.io.File>()
                compose.runOnUiThread { vm.export(com.example.fucknotes.export.ExportFormat.PDF) { file, _ -> ready.put(file) } }
                val file = ready.poll(20, java.util.concurrent.TimeUnit.SECONDS) ?: error("The export did not finish")
                try {
                    android.graphics.pdf.PdfRenderer(
                        android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                    ).use { pdf ->
                        pdf.openPage(0).use { page ->
                            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                            try {
                                page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                return bitmap.getPixel(page.width - 12, page.height - 12)
                            } finally { bitmap.recycle() }
                        }
                    }
                } finally { file.parentFile?.deleteRecursively() }
            }
            compose.runOnIdle { vm.setDarkPages(true) }
            assertEquals("A dark note exports black pages", android.graphics.Color.BLACK, exportedPaper())
            compose.runOnIdle { vm.setDarkPages(false) }
            assertTrue("A light note exports light pages", Color(exportedPaper()).luminance() > 0.8f)
        }
    }

    /** Hand-drawn boxes use one closed outline, and reopen as the same drawing. */
    @Test fun aHandDrawnBoxIsOneStrokeAndReopensAsDrawn() {
        withEditor { vm, camera ->
            compose.runOnUiThread { vm.setSketchyShapes(true) }
            pickShape("Rectangle")
            draw(camera, Offset(60f, 260f), Offset(180f, 360f))
            compose.runOnIdle {
                assertEquals(InkShape.RECTANGLE, vm.strokes.single().shape)
                val points = com.example.fucknotes.data.ink.ShapeRecognizer.points(vm.strokes.single().stroke)
                val length = points.zipWithNext().sumOf { (a, b) -> (b - a).getDistance().toDouble() }
                assertTrue("A 120 × 100 box must have one perimeter, was $length", length in 390.0..460.0)
                assertEquals("The box must close without another lap", points.first(), points.last())
            }
            runBlocking { vm.saveNow() }
            val app = InstrumentationRegistry.getInstrumentation().targetContext
            val saved = runBlocking { ServiceLocator.notes(app).getNote(vm.state.value.noteId) }!!
            val reopened = InkCodec.decode(saved.drawingJson).single()
            assertEquals(InkShape.RECTANGLE, reopened.shape)
            assertEquals(vm.strokes.single().stroke.inputs.size, reopened.stroke.inputs.size)
            // The hand-drawn box has soft corners: ink along the edges, none in the very corner.
            val renderer = CanvasStrokeRenderer.create()
            val bitmap = Bitmap.createBitmap(240, 420, Bitmap.Config.ARGB_8888)
            try {
                renderer.draw(Canvas(bitmap), reopened.stroke, Matrix())
                fun inked(x: Int, y: Int, reach: Int) = (-reach..reach).any { dx -> (-reach..reach).any { dy ->
                    android.graphics.Color.alpha(bitmap.getPixel(x + dx, y + dy)) > 128
                } }
                listOf(120 to 260, 180 to 310, 120 to 360, 60 to 310).forEach { (x, y) ->
                    assertTrue("no ink on the edge at $x,$y", inked(x, y, 4))
                }
                assertFalse("a hand-drawn corner is rounded", inked(180, 260, 1))
            } finally { bitmap.recycle() }
        }
    }

    @Test fun handDrawnShapesRenderOneOutlineAtThinAndThickWidths() {
        withEditor { vm, camera ->
            compose.runOnUiThread { vm.setSketchyShapes(true) }
            compose.waitUntil(5000) { vm.state.value.sketchyShapes }
            val fixtures = listOf(
                "Line" to Offset(180f, 260f), "Arrow" to Offset(180f, 260f),
                "Rectangle" to Offset(180f, 360f), "Circle" to Offset(180f, 380f),
            )
            val renderer = CanvasStrokeRenderer.create()
            for ((label, end) in fixtures) {
                pickShape(label)
                draw(camera, Offset(60f, 260f), end)
                runBlocking { vm.saveNow() }
                val app = InstrumentationRegistry.getInstrumentation().targetContext
                val saved = runBlocking { ServiceLocator.notes(app).getNote(vm.state.value.noteId) }!!
                val reopened = InkCodec.decode(saved.drawingJson).last().stroke
                for ((stage, stroke) in listOf("drawn" to vm.strokes.last().stroke, "reopened" to reopened)) {
                    for (width in listOf(0.5f, 2.5f)) {
                        val bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
                        try {
                            // Magnify the top/shaft intersection so separated fine outlines
                            // cannot hide in the same pixel or merge under a wide nib.
                            val transform = Matrix().apply { setScale(8f, 8f); postTranslate(-840f, -1960f) }
                            val canvas = Canvas(bitmap).apply { concat(transform) }
                            renderer.draw(canvas, androidx.ink.strokes.Stroke(stroke.brush.copy(size = width), stroke.inputs), transform)
                            var runs = 0
                            var previous = false
                            for (y in 8 until 232) {
                                val inked = android.graphics.Color.alpha(bitmap.getPixel(120, y)) >= 32
                                if (inked && !previous) runs++
                                previous = inked
                            }
                            assertEquals("$label $stage at width $width must cross once, with no doubled outline", 1, runs)
                        } finally { bitmap.recycle() }
                    }
                }
            }
        }
    }

    @Test fun aToolPutAwayLeavesTheBarAndComesBack() {
        withEditor { vm, _ ->
            compose.onNodeWithContentDescription("Eraser").assertIsDisplayed()
            compose.runOnUiThread { vm.setTool(InkTool.ERASER); vm.setToolbarItemHidden(ToolbarTool.ERASER.key, true) }
            // The bar follows the stored set, which lands a moment after the call.
            compose.waitUntil(5000) { ToolbarTool.ERASER.key in vm.state.value.hiddenToolbarItems }
            compose.onNodeWithContentDescription("Eraser").assertDoesNotExist()
            // The eraser was in hand; a pen that is still on the bar takes its place.
            compose.runOnIdle { assertTrue(vm.state.value.tool.isBrush) }

            val first = ToolbarLayout.penSlots(vm.state.value.pens).first()
            compose.runOnUiThread { vm.selectPen(first); vm.setToolbarItemHidden(ToolbarLayout.penKey(first), true) }
            compose.waitUntil(5000) { ToolbarLayout.penKey(first) in vm.state.value.hiddenToolbarItems }
            compose.runOnIdle { assertTrue("the pen in hand moved to one still shown", vm.state.value.selectedPen != first) }

            // With the eraser in hand, putting away the pen it will hand back to still clears
            // that pen off the bar; the eraser stays in hand. (Seen on the emulator: the pen
            // moved to the end of the row instead of leaving.)
            val second = ToolbarLayout.penSlots(vm.state.value.pens).first { it != first }
            compose.runOnUiThread { vm.selectPen(second) }
            tapTool(RED_HIGHLIGHTER_LABEL, "Red highlighter")
            compose.runOnUiThread { vm.setToolbarItemHidden(ToolbarLayout.penKey(second), true) }
            compose.waitUntil(5000) { ToolbarLayout.penKey(second) in vm.state.value.hiddenToolbarItems }
            compose.runOnIdle {
                assertEquals(InkTool.LASER, vm.state.value.tool)
                assertTrue(vm.state.value.selectedPen != second)
            }
            compose.onNodeWithContentDescription(com.example.fucknotes.ui.editor.ink.penLabel(vm.state.value.pens[second])).assertDoesNotExist()

            compose.runOnUiThread { vm.showAllToolbarItems() }
            compose.waitUntil(5000) { vm.state.value.hiddenToolbarItems.isEmpty() }
            compose.onNodeWithContentDescription("Eraser").assertIsDisplayed()
        }
    }

    /** Upright or lying down, the note bar and the pen bar sit side by side on one row. */
    @Test fun theNoteBarAndThePenBarShareOneRow() {
        withEditor { _, _ ->
            val back = compose.onNodeWithContentDescription("Back to notes").fetchSemanticsNode().boundsInRoot
            val lock = compose.onNodeWithContentDescription(EditorChrome.FULL.lockLabel).fetchSemanticsNode().boundsInRoot
            assertTrue("the pen bar is below the note bar: $back / $lock", kotlin.math.abs(back.center.y - lock.center.y) < back.height / 2)
            assertTrue("the pen bar is to the right of the note bar", lock.left > back.right)
            // Whatever does not fit is behind one button, never dropped.
            val onBar = compose.onAllNodesWithContentDescription("Shapes").fetchSemanticsNodes().isNotEmpty()
            if (!onBar) compose.onNodeWithContentDescription("More tools").assertIsDisplayed()
        }
    }

    /** Taps a pen-bar tool wherever the bar has put it: on the bar, or behind "More tools". */
    private fun tapTool(onBar: String, inMenu: String) {
        if (compose.onAllNodesWithContentDescription(onBar).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithContentDescription(onBar).performClick()
        } else {
            compose.onNodeWithContentDescription("More tools").performClick()
            compose.onNodeWithText(inMenu).performClick()
        }
    }

    private fun pickShape(label: String) {
        tapTool("Shapes", "Shapes")
        compose.onNodeWithText(label).performClick()
    }

    private fun withEditor(type: NoteType = NoteType.DRAWING, fullScreen: Boolean = false, test: (EditorViewModel, InkCamera) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val repo = ServiceLocator.notes(app)
        val settings = ServiceLocator.settings(app)
        // The owner's own bar and shape style are app-wide settings; the tests need every tool
        // out and exact geometry, and put the owner's choices back afterwards.
        val previous = runBlocking { settings.settings.first() }
        // Written before the editor exists, so its first reading of the settings already has them.
        runBlocking { settings.setHiddenToolbarItems(emptySet()); settings.setSketchyShapes(false) }
        val id = runBlocking { repo.createNote(type, null, "Drawing tools test") }
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
                    if (fullScreen) Box(Modifier.testTag("editor")) {
                        EditorScreen(viewModel = vm, onBack = {}, onOpenNote = {})
                    } else InkEditor(
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
                        onSketchyShapes = vm::setSketchyShapes,
                    )
                }
            }
            test(vm, camera)
        } finally {
            runBlocking { vm.saveNow() }
            compose.runOnUiThread { store.clear() }
            runBlocking {
                repo.deleteForever(listOf(id))
                if (fullScreen) com.example.fucknotes.ui.editor.tabs.OpenTabs.forget(id)
                settings.setFingerInputMode(previous.fingerInputMode)
                settings.setHiddenToolbarItems(previous.hiddenToolbarItems)
                settings.setSketchyShapes(previous.sketchyShapes)
            }
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
