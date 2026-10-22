package com.example.fucknotes.ui.editor.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import com.example.fucknotes.data.db.AttachmentEntity
import com.example.fucknotes.data.ink.NotebookLayout
import com.example.fucknotes.data.ink.PageSpec
import com.example.fucknotes.media.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Decoded page backgrounds, by path and size.
 *
 * A quarter of the heap rather than a fixed 32 MB: an imported PDF page at 2048 px is ~12 MB, so
 * the old cap held two pages and every scroll past a third re-decoded the first. The budget scales
 * with the device, and the cache is shared by the editor, the pages sheet and the shelf.
 */
private val imageCache = object : LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 4).coerceIn(48L * 1024 * 1024, 512L * 1024 * 1024).toInt(),
) {
    override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
}

/** Only visible pages are decoded; a long PDF never allocates a bitmap for every page. */
@Composable
internal fun rememberPageImages(attachments: List<AttachmentEntity>, maxEdge: Int = 2048): Map<Long, Bitmap> {
    val images by produceState<Map<Long, Bitmap>>(emptyMap(), attachments, maxEdge) {
        value = withContext(Dispatchers.IO) {
            attachments.associate { image ->
                val key = "${image.path}:$maxEdge"
                val bitmap = imageCache.get(key) ?: runCatching {
                    MediaStore.decodeImage(File(image.path), maxEdge).also { imageCache.put(key, it) }
                }.getOrNull()
                image.id to bitmap
            }.mapNotNull { (id, bitmap) -> bitmap?.let { id to it } }.toMap()
        }
    }
    return images
}

@Composable
internal fun PageImages(
    attachments: List<AttachmentEntity>,
    camera: InkCamera,
    page: PageSpec,
    notebook: Boolean,
    darkReader: Boolean = false,
) {
    // Which page images are near the viewport is derived state: the camera moves every frame
    // while the page scrolls, but this only changes — and only recomposes — when an image enters
    // or leaves the band. The band is a viewport tall on each side, so the next page is already
    // decoded before it comes into view.
    val visibleIds by remember(attachments, page) {
        derivedStateOf {
            val viewportHeightWorld = camera.viewportHeightPx / camera.scale.coerceAtLeast(0.01f)
            val bounds = camera.visibleWorldBounds(camera.viewportWidthPx, camera.viewportHeightPx)
            val near = WorldRect(bounds.left, bounds.top - viewportHeightWorld, bounds.right, bounds.bottom + viewportHeightWorld)
            attachments.filter { image ->
                image.pageIndex != null && image.pageWidth > 0 && image.pageHeight > 0 &&
                    near.overlaps(
                        image.pageX, image.pageY + NotebookLayout.topOf(image.pageIndex, page),
                        image.pageX + image.pageWidth,
                        image.pageY + NotebookLayout.topOf(image.pageIndex, page) + image.pageHeight,
                    )
            }.map { it.id }
        }
    }
    val near = remember(attachments, visibleIds) { attachments.filter { it.id in visibleIds } }
    val bitmaps = rememberPageImages(near)
    Canvas(Modifier.fillMaxSize()) {
        val bounds = camera.visibleWorldBounds(size.width, size.height)
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.concat(camera.worldToScreen())
            near.forEach { image ->
                val top = NotebookLayout.topOf(image.pageIndex ?: 0, page) + image.pageY
                if (!bounds.overlaps(image.pageX, top, image.pageX + image.pageWidth, top + image.pageHeight)) return@forEach
                bitmaps[image.id]?.let { bitmap -> drawPageImage(native, image, bitmap, page, notebook, darkReader) }
            }
            native.restore()
        }
    }
}

internal fun drawPageImage(
    canvas: Canvas,
    image: AttachmentEntity,
    bitmap: Bitmap,
    page: PageSpec,
    notebook: Boolean = true,
    darkReader: Boolean = false,
) {
    val top = if (notebook) NotebookLayout.topOf(image.pageIndex ?: 0, page) else 0f
    canvas.save()
    if (notebook) canvas.clipRect(0f, top, page.widthDp, top + page.heightDp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    if (darkReader) {
        paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )))
    }
    canvas.drawBitmap(
        bitmap,
        null,
        RectF(image.pageX, top + image.pageY,
            image.pageX + image.pageWidth, top + image.pageY + image.pageHeight),
        paint,
    )
    canvas.restore()
}
