package com.example.fucknotes.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.fucknotes.data.db.AttachmentEntity
import com.example.fucknotes.data.ink.PageSpec
import com.example.fucknotes.data.model.AttachmentType
import com.example.fucknotes.data.model.MAX_NOTEBOOK_PAGES
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.data.repo.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Imports each PDF sheet as a durable page background, with ordinary editable ink above it. */
class PdfImporter(private val context: Context, private val repo: NotesRepository) {
    suspend fun import(source: Uri, folderId: Long?, onProgress: (Int, Int) -> Unit = { _, _ -> }): Long =
        withContext(Dispatchers.IO) {
            val sourceFile = File(context.cacheDir, "import-${UUID.randomUUID()}.pdf")
            val images = mutableListOf<File>()
            try {
                context.contentResolver.openInputStream(source)?.use { input ->
                    sourceFile.outputStream().use { input.copyTo(it) }
                } ?: error("The PDF could not be opened")
                val name = MediaStore.displayName(context, source).replace(Regex("(?i)\\.pdf$"), "")
                val backgrounds = mutableListOf<AttachmentEntity>()
                var spec = PageSpec.Default
                PdfRenderer(ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                    require(pdf.pageCount in 1..MAX_NOTEBOOK_PAGES) {
                        "Choose a PDF with 1–$MAX_NOTEBOOK_PAGES pages"
                    }
                    repeat(pdf.pageCount) { index ->
                        currentCoroutineContext().ensureActive()
                        pdf.openPage(index).use { page ->
                            if (index == 0) {
                                val scale = minOf(1f, 2000f / maxOf(page.width, page.height))
                                spec = PageSpec(customWidth = (page.width * scale).coerceAtLeast(72f),
                                    customHeight = (page.height * scale).coerceAtLeast(72f))
                            }
                            // One bitmap at a time, bounded even for oversized architectural PDFs.
                            val scale = minOf(3f, 3072f / maxOf(page.width, page.height))
                            val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            val file = MediaStore.newFile(context, "png").also(images::add)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                            } finally { bitmap.recycle() }
                            backgrounds += pageImage(0, file, index, spec, page.width, page.height, margin = 0f)
                        }
                        onProgress(index + 1, pdf.pageCount)
                    }
                }
                repo.createImportedNotebook(name.ifBlank { "Imported PDF" }, folderId, spec, backgrounds)
            } catch (error: Exception) {
                images.forEach { it.delete() }
                throw error
            } finally { sourceFile.delete() }
        }
}

/** Page-local image bounds shared by PDF import, photo placement, rendering and export. */
fun pageImage(
    noteId: Long, file: File, index: Int, spec: PageSpec,
    width: Int, height: Int, margin: Float = 24f,
): AttachmentEntity {
    val scale = minOf((spec.widthDp - margin * 2) / width.coerceAtLeast(1),
        (spec.heightDp - margin * 2) / height.coerceAtLeast(1))
    val w = width * scale
    val h = height * scale
    return AttachmentEntity(noteId = noteId, path = file.absolutePath, type = AttachmentType.IMAGE,
        pageIndex = index, pageX = (spec.widthDp - w) / 2, pageY = (spec.heightDp - h) / 2,
        pageWidth = w, pageHeight = h)
}
