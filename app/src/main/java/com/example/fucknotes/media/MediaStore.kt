package com.example.fucknotes.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Owns the app-private folder that holds imported images and voice recordings. */
object MediaStore {

    fun mediaDir(context: Context): File =
        File(context.filesDir, "media").apply { if (!exists()) mkdirs() }

    fun newFile(context: Context, extension: String): File =
        File(mediaDir(context), "${UUID.randomUUID()}.$extension")

    /**
     * Copies a picked image into private storage, downscaling anything larger than [maxEdge] so a
     * note full of photos does not blow up the backup zip.
     */
    suspend fun importImage(context: Context, source: Uri, maxEdge: Int = 2048): File? =
        withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sample = 1
            while (bounds.outWidth / sample > maxEdge || bounds.outHeight / sample > maxEdge) sample *= 2

            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: return@withContext null

            val target = newFile(context, "jpg")
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            target
        }
}
