package com.example.fucknotes.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Page images as they travel inside an editable PDF.
 *
 * Imported PDF pages are kept as large PNGs for crisp display; sent along inside every export
 * they would double a document's size for nothing a reader can see. Each is re-encoded as a JPEG
 * with a bounded long edge; a picture that is already small travels as is.
 */
object ImageParts {
    const val MAX_EDGE = 2048
    const val JPEG_QUALITY = 82

    /** The bytes to embed and the extension they should get back on restore. */
    class Encoded(val bytes: ByteArray, val extension: String)

    fun encode(file: File): Encoded? {
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        if (longEdge <= 0) return null
        val isJpeg = bounds.outMimeType == "image/jpeg"
        if (isJpeg && longEdge <= MAX_EDGE) return Encoded(file.readBytes(), "jpg")

        var sample = 1
        while (longEdge / (sample * 2) >= MAX_EDGE) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return null
        try {
            val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(bitmap.width, bitmap.height))
            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(
                bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true,
            ) else bitmap
            try {
                // A page scan has no transparency; a photo with alpha keeps PNG so it is not flattened on black.
                val hasAlpha = scaled.hasAlpha() && !isOpaque(scaled)
                val out = ByteArrayOutputStream()
                return if (hasAlpha) {
                    scaled.compress(Bitmap.CompressFormat.PNG, 100, out); Encoded(out.toByteArray(), "png")
                } else {
                    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out); Encoded(out.toByteArray(), "jpg")
                }
            } finally { if (scaled !== bitmap) scaled.recycle() }
        } finally { bitmap.recycle() }
    }

    /** Samples a grid of pixels; a scan rendered onto white is opaque everywhere. */
    private fun isOpaque(bitmap: Bitmap): Boolean {
        val stepX = maxOf(1, bitmap.width / 16)
        val stepY = maxOf(1, bitmap.height / 16)
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                if (bitmap.getPixel(x, y) ushr 24 != 0xFF) return false
                x += stepX
            }
            y += stepY
        }
        return true
    }
}
