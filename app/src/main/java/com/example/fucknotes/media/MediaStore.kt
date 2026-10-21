package com.example.fucknotes.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Private, validated media. Provider streams are copied once before decoding. */
object MediaStore {
    fun mediaDir(context: Context): File =
        File(context.filesDir, "media").apply { check(isDirectory || mkdirs()) { "Cannot create media folder" } }

    fun newFile(context: Context, extension: String): File =
        File(mediaDir(context), "${UUID.randomUUID()}.$extension")

    fun displayName(context: Context, source: Uri): String = runCatching {
        context.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: source.takeIf { it.scheme == "file" }?.lastPathSegment?.takeIf { it.isNotBlank() }
        ?: "Imported document"

    suspend fun importImage(context: Context, source: Uri, maxEdge: Int = 3072): File? =
        withContext(Dispatchers.IO) {
            val original = File(context.cacheDir, "image-${UUID.randomUUID()}")
            val target = newFile(context, "png")
            try {
                context.contentResolver.openInputStream(source)?.use { input ->
                    original.outputStream().use { input.copyTo(it) }
                } ?: error("Cannot read image")
                val bitmap = decodeImage(original, maxEdge)
                try {
                    target.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally { bitmap.recycle() }
                target
            } catch (error: Exception) {
                target.delete()
                if (error is kotlinx.coroutines.CancellationException) throw error
                null
            } finally { original.delete() }
        }

    /** Software decoding works in PDF canvases; ImageDecoder also applies EXIF orientation. */
    fun decodeImage(file: File, maxEdge: Int = 3072): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = minOf(1f, maxEdge.toFloat() / maxOf(info.size.width, info.size.height))
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1))
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Unsupported image")
        val orientation = runCatching {
            android.media.ExifInterface(file.absolutePath).getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(android.media.ExifInterface.ORIENTATION_NORMAL)
        val matrix = android.graphics.Matrix()
        when (orientation) {
            2 -> matrix.setScale(-1f, 1f)
            3 -> matrix.setRotate(180f)
            4 -> matrix.setScale(1f, -1f)
            5 -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            6 -> matrix.setRotate(90f)
            7 -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            8 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }
}
