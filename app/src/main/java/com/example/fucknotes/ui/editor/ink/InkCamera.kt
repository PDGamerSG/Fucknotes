package com.example.fucknotes.ui.editor.ink

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix

/**
 * The "camera" over the infinite page.
 *
 * World coordinates are density-independent (1 world unit == 1 dp), so a note drawn on a phone and
 * reopened on a tablet keeps its physical size. The camera maps world to screen pixels:
 *
 * ```
 * screenPx = world * (density * zoom) + pan
 * ```
 *
 * Ink needs the reverse of that for [androidx.ink.authoring.compose.InProgressStrokes], and the
 * forward form for drawing finished strokes, so both are exposed.
 */
@Stable
class InkCamera(density: Float) {

    var density: Float = density
        internal set

    var zoom: Float by mutableFloatStateOf(1f)
        private set

    var panX: Float by mutableFloatStateOf(0f)
        private set

    var panY: Float by mutableFloatStateOf(0f)
        private set

    /**
     * Width of the page column the camera is confined to, in world units.
     *
     * Zero is canvas mode — an endless sheet with nothing to bump into. Anything larger is notebook
     * mode, where the column is centred when it fits on screen and cannot be dragged off the edge
     * when it does not, so the page always stays under the pen.
     */
    var columnWidthDp: Float = 0f
        private set

    private var viewportWidthPx = 0f
    private var viewportHeightPx = 0f

    /** Combined world-unit to screen-pixel factor. */
    val scale: Float get() = density * zoom

    /** Told by the surface on every layout; re-clamps in case the window or the mode changed. */
    fun configure(columnWidthDp: Float, viewportWidthPx: Float, viewportHeightPx: Float) {
        val changed = this.columnWidthDp != columnWidthDp ||
            this.viewportWidthPx != viewportWidthPx ||
            this.viewportHeightPx != viewportHeightPx
        if (!changed) return
        this.columnWidthDp = columnWidthDp
        this.viewportWidthPx = viewportWidthPx
        this.viewportHeightPx = viewportHeightPx
        clampToColumn()
    }

    /** Applies a pinch: [factor] around [centroid], plus a drag of [panDelta]. All in pixels. */
    fun transformBy(factor: Float, centroid: Offset, panDelta: Offset) {
        val target = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        // Recompute the effective factor after clamping so the centroid stays put at the limits.
        val applied = target / zoom
        panX = centroid.x - (centroid.x - panX) * applied + panDelta.x
        panY = centroid.y - (centroid.y - panY) * applied + panDelta.y
        zoom = target
        clampToColumn()
    }

    /** Single-finger / two-finger drag with no scaling. */
    fun panBy(delta: Offset) {
        panX += delta.x
        panY += delta.y
        clampToColumn()
    }

    fun reset() {
        zoom = 1f
        panX = 0f
        panY = 0f
        clampToColumn()
    }

    /**
     * Keeps a notebook page in view.
     *
     * Sideways the column is pinned: centred while it is narrower than the window, otherwise held
     * so neither margin can be dragged past the edge. Downwards nothing stops the user — a notebook
     * grows a page at a time — but the first page cannot be pushed off the top.
     */
    private fun clampToColumn() {
        if (columnWidthDp <= 0f || viewportWidthPx <= 0f) return
        val columnPx = columnWidthDp * scale
        panX = if (columnPx <= viewportWidthPx) {
            (viewportWidthPx - columnPx) / 2f
        } else {
            panX.coerceIn(viewportWidthPx - columnPx, 0f)
        }
        panY = panY.coerceAtMost(0f)
    }

    /** Screen pixels to world units — what Ink calls `pointerEventToWorldTransform`. */
    fun screenToWorld(): Matrix = Matrix().apply {
        // Compose post-multiplies, so this reads as scale(1/s) ∘ translate(-pan).
        val inverse = 1f / scale
        scale(inverse, inverse)
        translate(-panX, -panY)
    }

    /** World units to screen pixels, for rendering finished strokes. */
    fun worldToScreen(): android.graphics.Matrix = android.graphics.Matrix().apply {
        setScale(scale, scale)
        postTranslate(panX, panY)
    }

    fun screenToWorld(point: Offset): Offset =
        Offset((point.x - panX) / scale, (point.y - panY) / scale)

    /** The world-space rectangle currently on screen, used to cull strokes before drawing. */
    fun visibleWorldBounds(widthPx: Float, heightPx: Float): WorldRect {
        val topLeft = screenToWorld(Offset.Zero)
        val bottomRight = screenToWorld(Offset(widthPx, heightPx))
        return WorldRect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)
    }

    companion object {
        const val MIN_ZOOM = 0.25f
        const val MAX_ZOOM = 8f
    }
}

data class WorldRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun overlaps(l: Float, t: Float, r: Float, b: Float): Boolean =
        l <= right && r >= left && t <= bottom && b >= top
}
