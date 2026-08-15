package com.example.fucknotes.ui.editor.ink

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
// Aliased: androidx.ink.strokes.Stroke is the ink stroke, this one is a paint style.
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.geometry.MutableParallelogram
import androidx.ink.geometry.MutableSegment
import androidx.ink.geometry.MutableVec
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import com.example.fucknotes.data.ink.InkCodec
import com.example.fucknotes.data.ink.InkStroke
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.PaperStyle
import java.util.Collections
import java.util.WeakHashMap
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
 * tools take over single-pointer input, and a resting palm is dropped while a stylus is down.
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
    /**
     * Whether a finger draws.
     *
     * When false the page is stylus-only: finger and palm contacts are consumed before the ink
     * layer sees them, and a single finger pans the page instead. This is the mode that makes a
     * stylus usable — otherwise the hand resting on the glass writes as well as the pen.
     */
    fingerDrawing: Boolean,
    /**
     * Freezes the zoom level.
     *
     * A locked page still scrolls — otherwise a notebook would be unusable — but a pinch cannot
     * change its scale, so resting fingers and a passing palm stop resizing the writing.
     */
    zoomLocked: Boolean,
    /**
     * Notebook mode: pages the shape of this surface, stacked down a column.
     *
     * Canvas mode passes false and gets the endless sheet instead.
     */
    notebook: Boolean,
    paperStyle: PaperStyle,
    onStrokesFinished: (List<Stroke>) -> Unit,
    onErase: (Set<Int>) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The sheet the ink sits on.
     *
     * This is deliberately a paper tone rather than the theme's surface colour: ink is stored with
     * the colour the user picked, so a page that followed a dark theme would render near-black
     * strokes onto a near-black background and look empty.
     */
    pageColor: Color = PaperWhite,
) {
    camera.density = LocalDensity.current.density

    val renderer = remember { CanvasStrokeRenderer.create() }
    val ruleColor = remember(pageColor) { PaperRule }

    // The gesture loop reads these on every event; holding them in updated state keeps the pointer
    // coroutine alive when the user simply picks a different pen.
    val currentTool by rememberUpdatedState(tool)
    val currentStrokes by rememberUpdatedState(strokes)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentWidth by rememberUpdatedState(widthDp)
    val currentFingerDrawing by rememberUpdatedState(fingerDrawing)
    val currentZoomLocked by rememberUpdatedState(zoomLocked)
    val currentOnErase by rememberUpdatedState(onErase)

    val brush = remember(tool, colorArgb, widthDp, camera.scale) {
        if (tool.isBrush) InkCodec.brushFor(tool, colorArgb, widthDp, camera.scale) else null
    }

    // Compose's Matrix is a value class over a FloatArray, so it compares by reference: building a
    // fresh one each recomposition would look like a changed parameter to InProgressStrokes and
    // make it rebuild its low-latency surface. Keep one instance per actual camera position.
    val screenToWorld = remember(camera.scale, camera.panX, camera.panY) { camera.screenToWorld() }

    BoxWithConstraints(
        modifier = modifier
            // Notebook pages float on a desk; the endless canvas *is* the page.
            .background(if (notebook) DeskSurface else pageColor)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var lastErasePoint: Offset? = null

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)

                        // While ink is off the layer is display-only: it must not steal scrolling
                        // or text selection, and the page must not drift out from under the text.
                        if (!currentEnabled) {
                            lastErasePoint = null
                            continue
                        }

                        val down = event.changes.filter { it.pressed }

                        // Two or more pointers always mean "move the page", never "draw".
                        if (down.size >= 2) {
                            camera.transformBy(
                                factor = if (currentZoomLocked) 1f
                                else event.calculateZoom().takeIf { it > 0f } ?: 1f,
                                centroid = event.calculateCentroid(useCurrent = false),
                                panDelta = event.calculatePan(),
                            )
                            event.changes.forEach { it.consume() }
                            lastErasePoint = null
                            continue
                        }

                        // A palm resting on the glass while the stylus writes is not input.
                        val stylusDown = down.any { it.type == PointerType.Stylus }
                        if (stylusDown) {
                            event.changes
                                .filter { it.type != PointerType.Stylus }
                                .forEach { it.consume() }
                        }

                        // Stylus-only: a finger scrolls the page instead of marking it. The
                        // consume is what keeps the touch away from the ink layer, so it has to
                        // happen here, before any tool handling below.
                        if (!currentFingerDrawing && !stylusDown &&
                            event.changes.any { it.type != PointerType.Stylus }
                        ) {
                            down.firstOrNull()?.let { camera.panBy(it.positionChange()) }
                            event.changes
                                .filter { it.type != PointerType.Stylus }
                                .forEach { it.consume() }
                            lastErasePoint = null
                            continue
                        }

                        // Prefer the pen when both are on the glass, so a stray finger cannot
                        // steer an erase or a pan away from where the stylus is.
                        val active = down.firstOrNull { it.type == PointerType.Stylus }
                            ?: down.firstOrNull()

                        when {
                            currentTool == InkTool.PAN && active != null -> {
                                camera.panBy(active.positionChange())
                                event.changes.forEach { it.consume() }
                            }

                            currentTool == InkTool.ERASER && active != null -> {
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
            }
    ) {
        // A page is exactly one screenful, so a notebook has the proportions of the device it is
        // written on and a page turn lands where the eye already is.
        val pageWidthDp = maxWidth.value
        val pageHeightDp = maxHeight.value

        // Written from an effect rather than inline: the camera is snapshot state that this
        // composable reads, and writing it mid-composition would schedule another pass — the very
        // thing that disturbs InProgressStrokes.
        SideEffect {
            camera.configure(
                columnWidthDp = if (notebook) pageWidthDp else 0f,
                viewportWidthPx = constraints.maxWidth.toFloat(),
                viewportHeightPx = constraints.maxHeight.toFloat(),
            )
        }

        // Paper.
        Canvas(Modifier.fillMaxSize()) {
            if (notebook) drawNotebookPages(camera, pageWidthDp, pageHeightDp, pageColor, paperStyle, ruleColor)
            else drawPaper(paperStyle, ruleColor, camera)
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
                        currentStrokes().forEach { inkStroke ->
                            if (isVisible(inkStroke.stroke, pageBounds)) {
                                renderer.draw(native, inkStroke.stroke, matrix)
                            }
                        }
                        native.restore()
                    }
                    return@drawIntoCanvas
                }
                native.save()
                native.concat(matrix)
                currentStrokes().forEach { inkStroke ->
                    if (isVisible(inkStroke.stroke, viewport)) {
                        renderer.draw(native, inkStroke.stroke, matrix)
                    }
                }
                native.restore()
            }
        }

        // Wet ink. A null brush keeps the renderer warm while a non-drawing tool is selected.
        InProgressStrokes(
            defaultBrush = if (enabled) brush else null,
            pointerEventToWorldTransform = screenToWorld,
            onStrokesFinished = onStrokesFinished,
        )
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
    val hits = mutableSetOf<Int>()
    strokes.forEachIndexed { index, inkStroke ->
        if (inkStroke.stroke.shape.computeCoverageIsGreaterThan(sweep, 0f)) hits += index
    }
    return hits
}

// ---- paper -----------------------------------------------------------------

/**
 * Ruled / grid / dotted paper for the endless canvas, drawn in world space so it pans and zooms
 * with the ink instead of sliding underneath it.
 */
private fun DrawScope.drawPaper(style: PaperStyle, color: Color, camera: InkCamera) {
    val step = PAPER_SPACING_DP * camera.scale
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
 * Notebook mode: screen-shaped sheets stacked down a column with a gap between them.
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
) {
    val scale = camera.scale
    val step = PAPER_SPACING_DP * scale
    val widthPx = pageWidthDp * scale
    val heightPx = pageHeightDp * scale

    forEachVisiblePage(camera, pageHeightDp, size.height) { topWorld ->
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
    val stride = pageHeightDp + PAGE_GAP_DP
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

    if (style == PaperStyle.LINED || style == PaperStyle.GRID) {
        var y = firstY
        while (y < bottom) {
            drawLine(color, Offset(left, y), Offset(right, y), strokeWidth = 1f)
            y += step
        }
    }
    if (style == PaperStyle.GRID) {
        var x = firstX
        while (x < right) {
            drawLine(color, Offset(x, top), Offset(x, bottom), strokeWidth = 1f)
            x += step
        }
    }
    if (style == PaperStyle.DOTTED) {
        var y = firstY
        while (y < bottom) {
            var x = firstX
            while (x < right) {
                drawCircle(color, radius = 1.6f, center = Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

private const val PAPER_SPACING_DP = 24f

/** Gap between notebook pages, in world units. */
private const val PAGE_GAP_DP = 18f

/** The surface a notebook's pages lie on. */
private val DeskSurface = Color(0xFF2A2C31)

/** Page outline, one hairline of separation from the desk. */
private val PageEdge = Color(0x22000000)

/** Warm off-white, so a night-time page is not a slab of pure white. */
val PaperWhite = Color(0xFFFCFCFA)

/** Rules and dots: visible on paper, never competing with the ink. */
private val PaperRule = Color(0xFFD3D6DE)
