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
import androidx.compose.runtime.getValue
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

private val imageCache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
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
    val bounds = camera.visibleWorldBounds(camera.viewportWidthPx, camera.viewportHeightPx)
    val visible = attachments.filter { image ->
        image.pageIndex != null && image.pageWidth > 0 && image.pageHeight > 0 &&
            bounds.overlaps(image.pageX, image.pageY + NotebookLayout.topOf(image.pageIndex, page),
                image.pageX + image.pageWidth, image.pageY + NotebookLayout.topOf(image.pageIndex, page) + image.pageHeight)
    }
    val bitmaps = rememberPageImages(visible)
    Canvas(Modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            native.concat(camera.worldToScreen())
            visible.forEach { image ->
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
