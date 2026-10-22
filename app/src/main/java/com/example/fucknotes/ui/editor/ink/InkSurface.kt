package com.example.fucknotes.ui.editor.ink

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.LaunchedEffect
import androidx.ink.brush.Brush
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
// Aliased: androidx.ink.strokes.Stroke is the ink stroke, this one is a paint style.
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.geometry.MutableParallelogram
import androidx.ink.geometry.MutableSegment
import androidx.ink.geometry.MutableVec
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import com.example.fucknotes.data.ink.InkCodec
import com.example.fucknotes.data.ink.InkStroke
import com.example.fucknotes.data.ink.NotebookLayout
import com.example.fucknotes.data.ink.PageSpec
import com.example.fucknotes.data.model.FingerInputMode
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.PaperStyle
import java.util.Collections
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import androidx.ink.geometry.Box as InkBox

/**
 * The writing surface.
 *
 * Rendering is split the way the Ink API expects:
 *  - **wet ink** — the stroke under the user's pen right now — is drawn by [InProgressStrokes],
 *    which uses a low-latency front buffer and motion prediction;
 *  - **dry ink** — everything already committed — is drawn once per frame by
 *    [CanvasStrokeRenderer] onto an ordinary [Canvas], culled to the visible page area.
 *
 * A gesture layer sits above both. Because ancestors receive [PointerEventPass.Initial] first, it
 * can claim events before the ink layer sees them: two fingers pan and zoom, the eraser and pan
 * tools take over single-pointer input, a resting palm is dropped while a stylus is down, and a
 * double tap swaps the eraser in and out.
 */
@Composable
fun InkSurface(
    /**
     * Read as a lambda, not a value.
     *
     * Taking the list directly would make finishing a stroke recompose this composable — and so
     * re-supply [InProgressStrokes] with fresh parameters at the exact moment it is handing the
     * stroke over, which its documentation warns produces a visible flicker. Deferring the read
     * into the draw phase means a new stroke invalidates drawing only.
     */
    strokes: () -> List<InkStroke>,
    camera: InkCamera,
    tool: InkTool,
    colorArgb: Int,
    widthDp: Float,
    enabled: Boolean,
    /** Finger drawing, page navigation or complete touch rejection; stylus input stays active. */
    fingerInputMode: FingerInputMode,
    /**
     * Freezes the zoom level.
     *
     * A locked page still scrolls — otherwise a notebook would be unusable — but a pinch cannot
     * change its scale, so resting fingers and a passing palm stop resizing the writing. It also
     * disables the double-tap tool swap, the other thing a resting hand can trigger by accident.
     */
    zoomLocked: Boolean,
    /**
     * Notebook mode: sheets of [pageSpec] stacked down a column, fitted to the window's width.
     *
     * Canvas mode passes false and gets the endless sheet instead.
     */
    notebook: Boolean,
    paperStyle: PaperStyle,
    /** Sheet size and direction. Ignored in canvas mode, which has no sheet. */
    pageSpec: PageSpec,
    /** Colour of the rules, grid or dots. */
    ruleColor: Color,
    /** Distance between rules, in world units. */
    ruleSpacingDp: Float,
    /** The surface a notebook's pages float on. Canvas mode paints [pageColor] instead. */
    deskColor: Color,
    onStrokesFinished: (List<Stroke>) -> Unit,
    onErase: (Set<Int>) -> Unit,
    /**
     * Double-tap on the page: swap between the eraser and the pen in hand without reaching for the
     * toolbar. See the gesture loop for which taps qualify — a tap that could have been a stroke
     * never does.
     */
    onToggleEraser: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The sheet the ink sits on.
     *
     * This is deliberately a paper tone rather than the theme's surface colour: ink is stored with
     * the colour the user picked, so a page that followed a dark theme would render near-black
     * strokes onto a near-black background and look empty.
     */
    pageColor: Color = PaperWhite,
    /** Inverts paper, imported pages and ink colours for low-glare reading. */
    darkReader: Boolean = false,
    attachments: List<com.example.fucknotes.data.db.AttachmentEntity> = emptyList(),
    pageCount: () -> Int = { 1 },
    /** When annotating typed content, scrolling moves the text and ink as one document. */
    contentScroll: ScrollState? = null,
    /** The circle-and-move selection; null on a surface that has no Select tool. */
    lasso: LassoState? = null,
    /** Circled strokes were dragged: indices and the world-unit shift to apply. */
    onMoveStrokes: (Set<Int>, Float, Float) -> Unit = { _, _, _ -> },
    /**
     * Reading mode: fingers always move the page.
     *
     * The finger-mode preference and the zoom lock exist to keep a resting hand from marking or
     * shifting a page that is being written on. A page that is being read has to scroll, so here
     * one finger pans whatever the preference says, the zoom lock still refuses a pinch's scale
     * but not its movement, and a finger never draws, erases or swaps tools. The stylus writes.
     */
    reading: Boolean = false,
) {
    camera.density = LocalDensity.current.density

    val renderer = remember { CanvasStrokeRenderer.create() }
    val nightPaper = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val createdPageCount = pageCount().coerceAtLeast(1)

    // The gesture loop reads these on every event; holding them in updated state keeps the pointer
    // coroutine alive when the user simply picks a different pen.
    val currentTool by rememberUpdatedState(tool)
    val currentStrokes by rememberUpdatedState(strokes)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentWidth by rememberUpdatedState(widthDp)
    val currentColor by rememberUpdatedState(colorArgb)
    val currentDarkReader by rememberUpdatedState(darkReader)
    val currentFingerInputMode by rememberUpdatedState(if (reading) FingerInputMode.HAND else fingerInputMode)
    val currentReading by rememberUpdatedState(reading)
    val currentZoomLocked by rememberUpdatedState(zoomLocked)
    val currentPageCount by rememberUpdatedState(pageCount)
    val currentPageSpec by rememberUpdatedState(pageSpec)
    val currentNotebook by rememberUpdatedState(notebook)
    val currentOnErase by rememberUpdatedState(onErase)
    val currentOnToggleEraser by rememberUpdatedState(onToggleEraser)
    val currentContentScroll by rememberUpdatedState(contentScroll)
    val currentLasso by rememberUpdatedState(lasso)
    val currentOnMoveStrokes by rememberUpdatedState(onMoveStrokes)
    // Leaving the Select tool drops the selection; a pen stroke on a circled word is just a stroke.
    LaunchedEffect(tool) { if (tool != InkTool.LASSO) lasso?.clear() }
    val contentScrollOffset = contentScroll?.value
    val haptics = LocalHapticFeedback.current

    val panContent: (Offset) -> Unit = remember(camera) {
        { delta ->
            val scroll = currentContentScroll
            if (scroll == null) camera.panBy(delta)
            else {
                scroll.dispatchRawDelta(-delta.y)
                camera.followContentScroll(scroll.value)
            }
        }
    }

    // The brush is always built in the pen's real colour and only then turned to night, so the
    // night brush knows which daylight brush to hand a finished stroke back in.
    val brush = remember(tool, colorArgb, widthDp, camera.scale, darkReader) {
        if (tool.isBrush) InkCodec.brushFor(tool, colorArgb, widthDp, camera.scale)
            .let { if (darkReader) it.forNight(tool) else it } else null
    }

    /**
     * The brush a stroke is actually drawn with, asked for the moment the stroke begins.
     *
     * This has to be one lambda that lives as long as the component and reads the pen when it is
     * called. Ink builds its stroke workflow from this parameter and keeps the one it was built
     * with, so supplying the brush by value instead — a fresh object every time the user picks a
     * pen — left the page drawing with whichever pen happened to be in hand when the note opened:
     * a new colour, a new nib and even a new brush family were all accepted by the tray and then
     * ignored by the ink. Reading the pen here through the updated-state handles keeps this lambda
     * identical for the component's whole life while still returning the current pen.
     */
    val nextBrush = remember {
        {
            val brushTool = currentTool.takeIf { it.isBrush } ?: InkTool.PEN
            val day = InkCodec.brushFor(
                tool = brushTool,
                colorArgb = currentColor,
                widthDp = currentWidth,
                worldToScreenScale = camera.scale,
            )
            if (currentDarkReader) day.forNight(brushTool) else day
        }
    }

    // Finished strokes leave in daylight colour whatever they were drawn in. One stable lambda,
    // for the same reason as nextBrush: a fresh one would rebuild the wet-ink surface.
    val currentOnStrokesFinished by rememberUpdatedState(onStrokesFinished)
    val finishInDaylight = remember {
        { finished: List<Stroke> -> currentOnStrokesFinished(finished.map { it.daylight() }) }
    }

    // Compose's Matrix is a value class over a FloatArray, so it compares by reference: building a
    // fresh one each recomposition would look like a changed parameter to InProgressStrokes and
    // make it rebuild its low-latency surface. Keep one instance per actual camera position.
    val screenToWorld = remember(camera.scale, camera.panX, camera.panY) { camera.screenToWorld() }

    // The paper tone the surface actually paints: night slate while dark mode is on. Notebook
    // sheets go through the same rule in drawNotebookPages; the canvas has no sheets, so its
    // background is the page and must follow the rule here or stay white at night.
    val renderedPageColor = if (darkReader && pageColor != Color.Transparent) NightPalette.paper else pageColor
    BoxWithConstraints(
        modifier = modifier
            // Notebook pages float on a desk; the endless canvas *is* the page.
            .background(if (notebook) deskColor else renderedPageColor)
            .then(if (enabled) Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    var lastErasePoint: Offset? = null
                    var outsidePageGesture = false
                    // The Select tool: which pointer is drawing the loop or dragging the selection,
                    // and where it was last, in world units.
                    var lassoPointer: androidx.compose.ui.input.pointer.PointerId? = null
                    var lassoMoving = false
                    var lassoLast = Offset.Zero
                    // The tool a gesture began with. A pen button pressed mid-stroke changes the
                    // tool for the *next* stroke: switching the current one would cancel the wet
                    // stroke under the pen (eraser now consumes the stream) or hand a half-erase
                    // to the ink layer as moves without a down. Either way the pen seems dead.
                    var gestureTool: InkTool? = null

                    // Double-tap tool swap, measured over a whole gesture: from the first pointer
                    // down to the last one up.
                    var gestureActive = false
                    var gestureStartMs = 0L
                    var gesturePointers = 0
                    var gestureTravel = 0f
                    var gestureSpread = 0f
                    var gestureHadStylus = false
                    // Keep stylus ownership across pointer events.  Some Xiaomi firmware sends a
                    // palm contact in a separate packet from the pen move, so the stylus is not
                    // present in every event even though it is still touching the glass.
                    var stylusContactActive = false
                    // Touch pointers that were on the glass while the stylus wrote. They are the
                    // palm, and they stay the palm until they lift — so a hand that keeps resting
                    // after the pen comes up cannot drift the page or leave a mark.
                    val palmIds = mutableSetOf<androidx.compose.ui.input.pointer.PointerId>()
                    var lastTapMs = 0L
                    var lastTapPointers = 0

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val nowMs = event.changes.firstOrNull()?.uptimeMillis ?: 0L

                        // While ink is off the layer is display-only: it must not steal scrolling
                        // or text selection, and the page must not drift out from under the text.
                        if (!currentEnabled) {
                            lastErasePoint = null
                            gestureActive = false
                            stylusContactActive = false
                            palmIds.clear()
                            continue
                        }

                        val down = event.changes.filter { it.pressed }
                        event.changes.filter { !it.pressed }.forEach { palmIds -= it.id }
                        if (down.isEmpty()) { outsidePageGesture = false; gestureTool = null }
                        else if (gestureTool == null) gestureTool = currentTool
                        val activeTool = gestureTool ?: currentTool
                        if (currentNotebook && activeTool.isBrush) {
                            val writer = down.firstOrNull { it.type == PointerType.Stylus }
                                ?: down.singleOrNull()?.takeIf { currentFingerInputMode == FingerInputMode.DRAW }
                            if (writer != null && !writer.previousPressed) {
                                val world = camera.screenToWorld(writer.position)
                                val spec = currentPageSpec
                                outsidePageGesture = world.x !in 0f..spec.widthDp || world.y < 0f ||
                                    world.y >= currentPageCount().coerceAtLeast(1) * NotebookLayout.strideOf(spec)
                            }
                            if (outsidePageGesture) {
                                event.changes.forEach { it.consume() }
                                continue
                            }
                        }
                        val stylusInEvent = event.changes.any { it.type == PointerType.Stylus }
                        if (stylusInEvent) {
                            // A stylus-up event is included as an unpressed stylus change.  Touch
                            // packets omit it, so they leave the state untouched until that up.
                            stylusContactActive = down.any { it.type == PointerType.Stylus }
                        }
                        val stylusDown = stylusContactActive || down.any { it.type == PointerType.Stylus }
                        if (stylusDown) down.filter { it.type != PointerType.Stylus }.forEach { palmIds += it.id }

                        // "Pen only": fingers never draw and never swap tools, but they still move
                        // the page — a notebook that cannot be scrolled by hand is not usable.
                        val touchDisabled = currentFingerInputMode == FingerInputMode.DISABLED

                        // A palm resting on the glass while the stylus writes is not input. Do
                        // this before the multi-pointer gesture check: otherwise the stylus plus
                        // one palm contact looks like a pinch and can move or zoom the page.
                        if (stylusDown) {
                            event.changes.filter { it.type != PointerType.Stylus }.forEach { it.consume() }
                            // Ignore finger-only packets, including releases, before tap, pan,
                            // pinch and eraser handling. A stylus in this packet still works.
                            if (!stylusInEvent) {
                                gestureActive = false
                                lastTapMs = 0L
                                lastErasePoint = null
                                continue
                            }
                        }

                        // The fingers that may navigate: touch pointers that are not the palm.
                        val fingers = down.filter { it.type != PointerType.Stylus && it.id !in palmIds }
                        val palmOnly = !stylusDown && fingers.isEmpty() &&
                            down.any { it.type != PointerType.Stylus }
                        if (palmOnly) {
                            event.changes.filter { it.type != PointerType.Stylus }.forEach { it.consume() }
                            gestureActive = false
                            lastTapMs = 0L
                            lastErasePoint = null
                            continue
                        }

                        if (down.isNotEmpty()) {
                            if (!gestureActive) {
                                gestureActive = true
                                gestureStartMs = nowMs
                                gesturePointers = 0
                                gestureTravel = 0f
                                gestureSpread = 0f
                                gestureHadStylus = false
                            }
                            // Highest count wins: fingers rarely land or lift on the same frame,
                            // so a two-finger tap passes through one-pointer frames at both ends.
                            gesturePointers = maxOf(gesturePointers, down.size)
                            gestureTravel += event.calculatePan().getDistance()
                            if (down.size >= 2) {
                                gestureSpread += abs((event.calculateZoom().takeIf { it > 0f } ?: 1f) - 1f)
                            }
                            if (down.any { it.type == PointerType.Stylus }) gestureHadStylus = true
                        } else if (gestureActive) {
                            gestureActive = false

                            // Only gestures this layer already claims for itself can be taps: two
                            // fingers, which always move the page, or one finger while the page is
                            // stylus-only. Anything a stylus touched is writing, never a swap — so
                            // a tap can never cost the user the dot they meant to draw. A page
                            // being read, a pen-only page and a locked page never swap tools.
                            val claimed = !touchDisabled && !gestureHadStylus && !currentReading &&
                                !currentZoomLocked &&
                                (gesturePointers == 2 || (gesturePointers == 1 &&
                                    currentFingerInputMode == FingerInputMode.HAND))
                            val tap = claimed &&
                                nowMs - gestureStartMs <= viewConfiguration.longPressTimeoutMillis &&
                                gestureTravel <= viewConfiguration.touchSlop &&
                                gestureSpread <= PINCH_SLOP

                            val doubled = tap &&
                                gesturePointers == lastTapPointers &&
                                nowMs - lastTapMs <= viewConfiguration.doubleTapTimeoutMillis
                            if (doubled) {
                                currentOnToggleEraser()
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                // Consumed: a third tap starts a new pair rather than toggling again.
                                lastTapMs = 0L
                            } else {
                                lastTapMs = if (tap) nowMs else 0L
                                lastTapPointers = gesturePointers
                            }
                        }

                        // Two or more fingers mean "move the page", never "draw". The zoom lock
                        // pins the scale but still lets the pinch carry the page.
                        if (fingers.size >= 2 && !stylusDown) {
                            if (currentContentScroll != null) panContent(event.calculatePan())
                            else camera.transformBy(
                                factor = if (currentZoomLocked) 1f
                                else event.calculateZoom().takeIf { it > 0f } ?: 1f,
                                centroid = event.calculateCentroid(useCurrent = false),
                                panDelta = event.calculatePan(),
                            )
                            event.changes.forEach { it.consume() }
                            lastErasePoint = null
                            continue
                        }

                        // One finger scrolls the page unless the user asked to write with it. The
                        // consume is what keeps the touch away from the ink layer, so it has to
                        // happen here, before any tool handling below.
                        if (currentFingerInputMode != FingerInputMode.DRAW && !stylusDown &&
                            fingers.isNotEmpty()
                        ) {
                            // InProgressStrokes may observe the same touch stream during the
                            // initial pass on some Compose/Ink versions. Use the raw movement so
                            // a stylus-only page still responds to a finger drag instead of
                            // appearing frozen after the touch has been consumed downstream.
                            panContent(fingers.first().positionChangeIgnoreConsumed())
                            event.changes
                                .filter { it.type != PointerType.Stylus }
                                .forEach { it.consume() }
                            lastErasePoint = null
                            continue
                        }

                        // Prefer the pen when both are on the glass, so a stray finger cannot
                        // steer an erase or a pan away from where the stylus is.
                        val active = down.firstOrNull { it.type == PointerType.Stylus }
                            ?: fingers.firstOrNull()?.takeUnless { stylusDown }

                        // The lasso pointer lifted: close the loop, or land the move.
                        val lassoState = currentLasso
                        if (lassoPointer != null && down.none { it.id == lassoPointer } && lassoState != null) {
                            if (lassoMoving) {
                                val drag = lassoState.drag
                                lassoState.drag = Offset.Zero
                                if (drag != Offset.Zero) currentOnMoveStrokes(lassoState.selection, drag.x, drag.y)
                            } else {
                                lassoState.selection = Lasso.select(lassoState.loop, currentStrokes())
                                lassoState.loop = emptyList()
                                if (lassoState.active) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            lassoPointer = null
                            lassoMoving = false
                        }

                        when {
                            activeTool == InkTool.LASSO && active != null && lassoState != null -> {
                                val world = camera.screenToWorld(active.position)
                                if (lassoPointer != active.id) {
                                    // A new touch: inside the selection it drags, anywhere else it
                                    // starts a fresh loop (and lets the old selection go).
                                    lassoPointer = active.id
                                    val grab = Lasso.selectionBounds(currentStrokes(), lassoState.selection)
                                        ?.let { it.copy(left = it.left - Lasso.GRAB_MARGIN, top = it.top - Lasso.GRAB_MARGIN, right = it.right + Lasso.GRAB_MARGIN, bottom = it.bottom + Lasso.GRAB_MARGIN) }
                                    lassoMoving = lassoState.active && grab != null && grab.contains(world)
                                    if (!lassoMoving) {
                                        lassoState.selection = emptySet()
                                        lassoState.loop = listOf(world)
                                    }
                                    lassoState.drag = Offset.Zero
                                } else if (lassoMoving) {
                                    lassoState.drag += world - lassoLast
                                } else {
                                    val last = lassoState.loop.lastOrNull()
                                    if (last == null) lassoState.loop = listOf(world)
                                    else if ((world - last).getDistance() >= 1.5f) lassoState.loop = lassoState.loop + world
                                }
                                lassoLast = world
                                event.changes.forEach { it.consume() }
                            }

                            activeTool == InkTool.PAN && active != null -> {
                                panContent(active.positionChangeIgnoreConsumed())
                                event.changes.forEach { it.consume() }
                            }

                            activeTool == InkTool.ERASER && active != null -> {
                                val world = camera.screenToWorld(active.position)
                                val hits = eraseHits(
                                    strokes = currentStrokes(),
                                    from = lastErasePoint,
                                    to = world,
                                    radiusWorld = (currentWidth * 1.5f).coerceAtLeast(6f),
                                )
                                if (hits.isNotEmpty()) currentOnErase(hits)
                                lastErasePoint = world
                                event.changes.forEach { it.consume() }
                            }

                            else -> lastErasePoint = null
                        }
                    }
                }
            } else Modifier)
    ) {
        // A page is measured in points, so it exports as a PDF page one-to-one; the camera scales
        // it to whatever window it is being written in.
        val pageWidthDp = pageSpec.widthDp
        val pageHeightDp = pageSpec.heightDp

        // Written from an effect rather than inline: the camera is snapshot state that this
        // composable reads, and writing it mid-composition would schedule another pass — the very
        // thing that disturbs InProgressStrokes.
        SideEffect {
            camera.configure(
                columnWidthDp = if (notebook) pageWidthDp else 0f,
                viewportWidthPx = constraints.maxWidth.toFloat(),
                viewportHeightPx = constraints.maxHeight.toFloat(),
                columnHeightDp = if (notebook) createdPageCount * NotebookLayout.strideOf(pageSpec) else 0f,
            )
            contentScrollOffset?.let(camera::followContentScroll)
        }

        // Paper.
        val renderedRuleColor = if (darkReader) NightPalette.rule else ruleColor

        Canvas(Modifier.fillMaxSize()) {
            if (notebook) {
                drawNotebookPages(
                    camera = camera,
                    pageWidthDp = pageWidthDp,
                    pageHeightDp = pageHeightDp,
                    pageColor = renderedPageColor,
                    style = paperStyle,
                    ruleColor = renderedRuleColor,
                    ruleSpacingDp = ruleSpacingDp,
                    pageCount = pageCount().coerceAtLeast(1),
                )
            } else {
                drawPaper(paperStyle, renderedRuleColor, ruleSpacingDp, camera)
            }
        }

        PageImages(attachments, camera, pageSpec, notebook, darkReader)

        // Cool only the page and imported backgrounds. Wet ink uses a separate front buffer;
        // tinting the dry layer would change the ink's color as soon as the pen is lifted.
        if (nightPaper && !darkReader && pageColor != Color.Transparent) {
            Canvas(Modifier.fillMaxSize()) { drawRect(Color(0x38213249)) }
        }

        // Dry ink.
        Canvas(Modifier.fillMaxSize()) {
            val matrix = camera.worldToScreen()
            val viewport = camera.visibleWorldBounds(size.width, size.height)
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                // CanvasStrokeRenderer does not move geometry: strokeToScreenTransform only tells
                // it how large the stroke will end up so it can pick a level of detail. The canvas
                // itself has to carry the camera, or dry ink lands at raw world coordinates while
                // wet ink stays under the pen.
                if (notebook) {
                    // Clipped page by page, so ink never appears to run off the sheet onto the desk.
                    forEachVisiblePage(camera, pageHeightDp, size.height) { top ->
                        if (top >= pageCount().coerceAtLeast(1) * NotebookLayout.strideOf(pageSpec)) return@forEachVisiblePage
                        val bottom = top + pageHeightDp
                        native.save()
                        native.clipRect(
                            camera.panX,
                            top * camera.scale + camera.panY,
                            pageWidthDp * camera.scale + camera.panX,
                            bottom * camera.scale + camera.panY,
                        )
                        native.concat(matrix)
                        val pageBounds = WorldRect(0f, top, pageWidthDp, bottom)
                        val selected = lasso?.selection.orEmpty()
                        val drag = lasso?.drag ?: Offset.Zero
                        currentStrokes().forEachIndexed { index, inkStroke ->
                            val moving = drag != Offset.Zero && index in selected
                            if (moving || isVisible(inkStroke.stroke, pageBounds)) {
                                if (moving) { native.save(); native.translate(drag.x, drag.y) }
                                renderer.draw(
                                    native,
                                    if (darkReader) inkStroke.forDarkReader() else inkStroke.stroke,
                                    matrix,
                                )
                                if (moving) native.restore()
                            }
                        }
                        native.restore()
                    }
                    return@drawIntoCanvas
                }
                native.save()
                native.concat(matrix)
                val selected = lasso?.selection.orEmpty()
                val drag = lasso?.drag ?: Offset.Zero
                currentStrokes().forEachIndexed { index, inkStroke ->
                    val moving = drag != Offset.Zero && index in selected
                    if (moving || isVisible(inkStroke.stroke, viewport)) {
                        if (moving) { native.save(); native.translate(drag.x, drag.y) }
                        renderer.draw(
                            native,
                            if (darkReader) inkStroke.forDarkReader() else inkStroke.stroke,
                            matrix,
                        )
                        if (moving) native.restore()
                    }
                }
                native.restore()
            }
        }

        // Wet ink.
        //
        // Composed for as long as the page takes ink, whichever tool is in hand. It used to leave
        // the composition while the eraser or the hand was selected, and come back when a pen was
        // picked again — and coming back means building the low-latency front-buffer surface anew,
        // which on this tablet takes long enough that the first strokes after the switch drew
        // nowhere until they were committed to the dry layer. The eraser and pan branches above
        // consume their touches in the Initial pass, so the wet layer never sees them; the brush
        // handed over while no pen is selected is only a placeholder, since every stroke asks
        // [nextBrush] for the pen in hand the moment it starts.
        if (enabled) {
            val placeholder = remember { mutableStateOf<Brush?>(null) }
            if (brush != null) SideEffect { placeholder.value = brush }
            val wetBrush = brush ?: placeholder.value
                ?: remember { InkCodec.brushFor(InkTool.PEN, colorArgb, widthDp, camera.scale) }
            InProgressStrokes(
                defaultBrush = wetBrush,
                nextBrush = nextBrush,
                pointerEventToWorldTransform = screenToWorld,
                onStrokesFinished = finishInDaylight,
            )
        }

        // The Select tool's marks: the loop while it is drawn, the box around what it took.
        if (lasso != null && tool == InkTool.LASSO) {
            LassoOverlay(lasso = lasso, camera = camera, strokes = currentStrokes)
        }

    }
}

// ---- culling ---------------------------------------------------------------

/** Bounding boxes are a native round trip, so keep them for as long as the stroke lives. */
private val boundsCache: MutableMap<Stroke, InkBox?> = Collections.synchronizedMap(WeakHashMap())

private fun isVisible(stroke: Stroke, viewport: WorldRect): Boolean {
    val box = boundsCache.getOrPut(stroke) { stroke.shape.computeBoundingBox() } ?: return true
    return viewport.overlaps(box.xMin, box.yMin, box.xMax, box.yMax)
}

// ---- eraser ----------------------------------------------------------------

/**
 * Indices of strokes touched by the eraser as it travels from [from] to [to].
 *
 * The swept area is a parallelogram around that segment, which is the shape the Ink geometry API
 * takes for coverage tests. Any non-zero coverage counts as a hit, so a stroke disappears whole —
 * the behaviour the reference app uses, and far cheaper than splitting meshes mid-gesture.
 */
private fun eraseHits(
    strokes: List<InkStroke>,
    from: Offset?,
    to: Offset,
    radiusWorld: Float,
): Set<Int> {
    val start = from ?: to
    val segment = MutableSegment(MutableVec(start.x, start.y), MutableVec(to.x, to.y))
    val sweep = MutableParallelogram().populateFromSegmentAndPadding(segment, radiusWorld)
    // The sweep's own bounds, so strokes nowhere near the eraser are skipped on a cached box
    // instead of each paying for a native coverage test.
    val pad = radiusWorld
    val left = minOf(start.x, to.x) - pad
    val top = minOf(start.y, to.y) - pad
    val right = maxOf(start.x, to.x) + pad
    val bottom = maxOf(start.y, to.y) + pad
    val hits = mutableSetOf<Int>()
    strokes.forEachIndexed { index, inkStroke ->
        val box = boundsCache.getOrPut(inkStroke.stroke) { inkStroke.stroke.shape.computeBoundingBox() }
        if (box != null && (box.xMax < left || box.xMin > right || box.yMax < top || box.yMin > bottom)) return@forEachIndexed
        if (inkStroke.stroke.shape.computeCoverageIsGreaterThan(sweep, 0f)) hits += index
    }
    return hits
}

// ---- paper -----------------------------------------------------------------

/**
 * Ruled / grid / dotted paper for the endless canvas, drawn in world space so it pans and zooms
 * with the ink instead of sliding underneath it.
 */
private fun DrawScope.drawPaper(
    style: PaperStyle,
    color: Color,
    ruleSpacingDp: Float,
    camera: InkCamera,
) {
    val step = ruleSpacingDp * camera.scale
    if (style == PaperStyle.PLAIN || step < 6f) return // Too dense to read once zoomed far out.
    drawPaperPattern(
        style = style,
        color = color,
        step = step,
        startX = camera.panX.mod(step),
        startY = camera.panY.mod(step),
        left = 0f,
        top = 0f,
        right = size.width,
        bottom = size.height,
    )
}

/**
 * Notebook mode: A4 sheets stacked down a column, edge to edge.
 *
 * Nothing separates one sheet from the next but the hairline both of them draw, so scrolling reads
 * as one continuous surface and a line of writing never lands in a dead band between pages.
 *
 * Only the pages the viewport touches are drawn, so the column can run on for as long as the user
 * keeps writing without the cost growing.
 */
private fun DrawScope.drawNotebookPages(
    camera: InkCamera,
    pageWidthDp: Float,
    pageHeightDp: Float,
    pageColor: Color,
    style: PaperStyle,
    ruleColor: Color,
    ruleSpacingDp: Float,
    pageCount: Int,
) {
    val scale = camera.scale
    val step = ruleSpacingDp * scale
    val widthPx = pageWidthDp * scale
    val heightPx = pageHeightDp * scale

    forEachVisiblePage(camera, pageHeightDp, size.height) { topWorld ->
        if (topWorld >= pageCount * (pageHeightDp + NotebookLayout.PAGE_GAP_DP)) return@forEachVisiblePage
        val left = camera.panX
        val top = topWorld * scale + camera.panY
        drawRect(pageColor, topLeft = Offset(left, top), size = Size(widthPx, heightPx))
        if (style != PaperStyle.PLAIN && step >= 6f) {
            drawPaperPattern(
                style = style,
                color = ruleColor,
                step = step,
                // Rules start below the top edge so the first line is not welded to it.
                startX = left + step,
                startY = top + step,
                left = left,
                top = top,
                right = left + widthPx,
                bottom = top + heightPx,
            )
        }
        // A hairline edge, so a page reads as a sheet rather than as a lit rectangle.
        drawRect(
            color = PageEdge,
            topLeft = Offset(left, top),
            size = Size(widthPx, heightPx),
            style = DrawStroke(width = 1f),
        )
    }
}

/**
 * Calls [block] with the world-space top of every page the viewport can see.
 *
 * Pages run downwards from world zero; there is no last page, so the range is derived from where
 * the camera is rather than from any stored count.
 */
private inline fun forEachVisiblePage(
    camera: InkCamera,
    pageHeightDp: Float,
    viewportHeightPx: Float,
    block: (topWorld: Float) -> Unit,
) {
    val stride = pageHeightDp + NotebookLayout.PAGE_GAP_DP
    if (stride <= 0f) return
    val topWorld = camera.screenToWorld(Offset.Zero).y
    val bottomWorld = camera.screenToWorld(Offset(0f, viewportHeightPx)).y
    val first = floor(topWorld / stride).toInt().coerceAtLeast(0)
    val last = floor(bottomWorld / stride).toInt().coerceAtLeast(0)
    for (index in first..last) block(index * stride)
}

/** Lines, grid or dots stepping from ([startX], [startY]) and confined to the given rectangle. */
private fun DrawScope.drawPaperPattern(
    style: PaperStyle,
    color: Color,
    step: Float,
    startX: Float,
    startY: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
) {
    // Skip straight to the first visible row / column: a page scrolled far down the column would
    // otherwise spin through thousands of off-screen steps.
    val firstX = startX + ceil(((left - startX) / step).coerceAtLeast(0f)) * step
    val firstY = startY + ceil(((top - startY) / step).coerceAtLeast(0f)) * step

    // One draw call per pattern. A page at 195 % zoom holds ~400 dots; issuing each as its own
    // circle was the single largest GPU cost of a pan.
    if (style == PaperStyle.LINED || style == PaperStyle.GRID) {
        val points = ArrayList<Offset>()
        var y = firstY
        while (y < bottom) { points += Offset(left, y); points += Offset(right, y); y += step }
        if (style == PaperStyle.GRID) {
            var x = firstX
            while (x < right) { points += Offset(x, top); points += Offset(x, bottom); x += step }
        }
        drawPoints(points, PointMode.Lines, color, strokeWidth = 1.5f)
    }
    if (style == PaperStyle.DOTTED) {
        val points = ArrayList<Offset>()
        var y = firstY
        while (y < bottom) {
            var x = firstX
            while (x < right) { points += Offset(x, y); x += step }
            y += step
        }
        drawPoints(points, PointMode.Points, color, strokeWidth = 5.6f, cap = StrokeCap.Round)
    }
}

/** How much two fingers may spread before a quick two-finger tap counts as a pinch instead. */
private const val PINCH_SLOP = 0.12f

/** Page outline, one hairline of separation from the desk. */
private val PageEdge = Color(0x22000000)

/** Warm off-white, so a night-time page is not a slab of pure white. */
val PaperWhite = Color(0xFFF1F5FA)

/** Rules and dots: visible on paper, never competing with the ink. */
val DefaultRuleColor = Color(0xFF8795AA)


/**
 * The lasso as the user sees it: a dashed loop following the pointer, then a dashed box with a
 * faint fill around the circled strokes, carried along while they are dragged. Drawn in screen
 * space from world coordinates, so it stays on the strokes through pans and zooms.
 */
@Composable
private fun LassoOverlay(lasso: LassoState, camera: InkCamera, strokes: () -> List<InkStroke>) {
    val accent = MaterialTheme.colorScheme.primary
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) }
    Canvas(Modifier.fillMaxSize()) {
        val scale = camera.scale
        val panX = camera.panX
        val panY = camera.panY
        fun toScreen(p: Offset) = Offset(p.x * scale + panX, p.y * scale + panY)

        val loop = lasso.loop
        if (loop.size >= 2) {
            val path = Path()
            path.moveTo(toScreen(loop.first()).x, toScreen(loop.first()).y)
            loop.drop(1).forEach { val s = toScreen(it); path.lineTo(s.x, s.y) }
            drawPath(path, accent.copy(alpha = 0.10f))
            drawPath(path, accent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, pathEffect = dash))
        }

        if (lasso.active) {
            val bounds = Lasso.selectionBounds(strokes(), lasso.selection) ?: return@Canvas
            val shifted = bounds.translate(lasso.drag)
            val topLeft = toScreen(Offset(shifted.left, shifted.top)) - Offset(10f, 10f)
            val size = Size(shifted.width * scale + 20f, shifted.height * scale + 20f)
            val corner = CornerRadius(12f, 12f)
            drawRoundRect(accent.copy(alpha = 0.08f), topLeft, size, corner)
            drawRoundRect(accent, topLeft, size, corner, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f, pathEffect = dash))
        }
    }
}
