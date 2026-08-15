package com.example.fucknotes.ui.editor.ink

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
    val currentOnErase by rememberUpdatedState(onErase)

    val brush = remember(tool, colorArgb, widthDp, camera.scale) {
        if (tool.isBrush) InkCodec.brushFor(tool, colorArgb, widthDp, camera.scale) else null
    }

    // Compose's Matrix is a value class over a FloatArray, so it compares by reference: building a
    // fresh one each recomposition would look like a changed parameter to InProgressStrokes and
    // make it rebuild its low-latency surface. Keep one instance per actual camera position.
    val screenToWorld = remember(camera.scale, camera.panX, camera.panY) { camera.screenToWorld() }

    Box(
        modifier = modifier
            .background(pageColor)
            .drawBehind { drawPaper(paperStyle, ruleColor, camera) }
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
                                factor = event.calculateZoom().takeIf { it > 0f } ?: 1f,
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
 * Ruled / grid / dotted paper, drawn in world space so it pans and zooms with the ink instead of
 * sliding underneath it.
 */
private fun DrawScope.drawPaper(style: PaperStyle, color: Color, camera: InkCamera) {
    if (style == PaperStyle.PLAIN) return
    val step = PAPER_SPACING_DP * camera.scale
    if (step < 6f) return // Too dense to read once zoomed far out.

    val originX = camera.panX.mod(step)
    val originY = camera.panY.mod(step)

    when (style) {
        PaperStyle.PLAIN -> Unit

        PaperStyle.LINED -> {
            var y = originY
            while (y < size.height) {
                drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }

        PaperStyle.GRID -> {
            var y = originY
            while (y < size.height) {
                drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
            var x = originX
            while (x < size.width) {
                drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
        }

        PaperStyle.DOTTED -> {
            var y = originY
            while (y < size.height) {
                var x = originX
                while (x < size.width) {
                    drawCircle(color, radius = 1.6f, center = Offset(x, y))
                    x += step
                }
                y += step
            }
        }
    }
}

private const val PAPER_SPACING_DP = 24f

/** Warm off-white, so a night-time page is not a slab of pure white. */
val PaperWhite = Color(0xFFFCFCFA)

/** Rules and dots: visible on paper, never competing with the ink. */
private val PaperRule = Color(0xFFD3D6DE)
