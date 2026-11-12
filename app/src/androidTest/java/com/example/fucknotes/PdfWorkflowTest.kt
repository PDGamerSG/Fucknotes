package com.example.fucknotes

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fucknotes.data.db.AppDatabase
import com.example.fucknotes.data.repo.NotesRepository
import com.example.fucknotes.export.ExportFormat
import com.example.fucknotes.export.NoteExporter
import com.example.fucknotes.media.PdfImporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Deferred device regression: imported pages must survive persistence and PDF export. */
@RunWith(AndroidJUnit4::class)
class PdfWorkflowTest {
    @Test fun darkNotebookExportsBlackAndKeepsItsEditableAppearance() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repo = NotesRepository(db)
        var exported: File? = null
        try {
            val id = repo.createNote(com.example.fucknotes.data.model.NoteType.NOTEBOOK, null,
                "Black notebook", appearance = com.example.fucknotes.data.model.PageAppearance.DARK)
            val note = requireNotNull(repo.getNote(id))
            val file = NoteExporter(context, repo).export(note, ExportFormat.PDF, darkStyle = true, editable = true)
            exported = file
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                pdf.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    try {
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        assertEquals(Color.BLACK, bitmap.getPixel(20, 20))
                    } finally { bitmap.recycle() }
                }
            }
            val parts = com.example.fucknotes.export.EditablePdf.read(file)
            val payload = requireNotNull(com.example.fucknotes.media.NoteRestorer.payloadOf(parts))
            assertEquals("DARK", payload.appearance)
        } finally { exported?.parentFile?.deleteRecursively(); db.close() }
    }

    /**
     * Dot paper in a PDF: the dots are marks to write along, not beads. They came out 2.6 points
     * across — wider than the pen — and the owner found them "very big" (2026-11-13).
     */
    @Test fun dottedPaperExportsFineDots() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repo = NotesRepository(db)
        var exported: File? = null
        try {
            val id = repo.createNote(com.example.fucknotes.data.model.NoteType.NOTEBOOK, null, "Dots",
                paperStyle = com.example.fucknotes.data.model.PaperStyle.DOTTED,
                paperColorArgb = Color.WHITE, lineSpacingDp = 24,
                appearance = com.example.fucknotes.data.model.PageAppearance.LIGHT)
            val file = NoteExporter(context, repo).export(requireNotNull(repo.getNote(id)), ExportFormat.PDF)
            exported = file
            val zoom = 8 // pixels per point: enough to measure a dot
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                pdf.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(60 * zoom, 60 * zoom, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, android.graphics.Matrix().apply { setScale(zoom.toFloat(), zoom.toFloat()) },
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        // The first dot sits one spacing in from the corner; walk the row through it.
                        val row = 24 * zoom
                        val across = (12 * zoom until 36 * zoom).count { x -> Color.red(bitmap.getPixel(x, row)) < 235 }
                        assertTrue("There must be a dot at 24,24 (found $across px)", across >= zoom / 2)
                        assertTrue("A dot is at most 1.6 points across, was ${across.toFloat() / zoom}", across <= 1.6f * zoom)
                    } finally { bitmap.recycle() }
                }
            }
        } finally { exported?.parentFile?.deleteRecursively(); db.close() }
    }

    @Test fun legacyNotebookExportsEveryStoredPage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repo = NotesRepository(db)
        var exported: File? = null
        try {
            val id = repo.createNote(com.example.fucknotes.data.model.NoteType.NOTEBOOK,
                folderId = null, title = "Older notebook", pageCount = 3, pagesExplicitlyCreated = false)
            val output = NoteExporter(context, repo).export(requireNotNull(repo.getNote(id)), ExportFormat.PDF)
            exported = output
            PdfRenderer(ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                assertEquals(3, pdf.pageCount)
            }
        } finally {
            exported?.delete()
            db.close()
        }
    }

    @Test fun importedPdfKeepsTitlePageCountAndBackgrounds() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repo = NotesRepository(db)
        val directory = File(context.cacheDir, "pdf-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val source = File(directory, "Lecture 1.Pdf")
        var importedId: Long? = null
        var exported: File? = null
        try {
            val pdf = PdfDocument()
            try {
                listOf(Color.RED, Color.BLUE).forEachIndexed { index, color ->
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(300, 400, index + 1).create())
                    page.canvas.drawColor(color)
                    page.canvas.drawText("Original page ${index + 1}", 30f, 100f,
                        Paint().apply { this.color = Color.BLACK; textSize = 20f })
                    pdf.finishPage(page)
                }
                source.outputStream().use { pdf.writeTo(it) }
            } finally { pdf.close() }
            val id = PdfImporter(context, repo).import(Uri.fromFile(source), null)
            importedId = id
            val note = requireNotNull(repo.getNote(id))
            assertEquals("Lecture 1", note.title)
            assertEquals(2, note.pageCount)
            val images = repo.observeAttachments(id).first()
            assertEquals(listOf(0, 1), images.map { it.pageIndex })
            assertTrue(images.all { File(it.path).length() > 0 })

            val output = NoteExporter(context, repo).export(note, ExportFormat.PDF)
            exported = output
            assertEquals("Lecture 1.pdf", output.name)
            PdfRenderer(ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
                assertEquals(2, pdf.pageCount)
                repeat(2) { index ->
                    pdf.openPage(index).use { page ->
                        assertEquals(300, page.width)
                        assertEquals(400, page.height)
                        val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            assertEquals(if (index == 0) Color.RED else Color.BLUE, bitmap.getPixel(20, 20))
                        } finally { bitmap.recycle() }
                    }
                }
            }
        } finally {
            importedId?.let { repo.deleteForever(listOf(it)) }
            exported?.delete()
            source.delete()
            directory.delete()
            db.close()
        }
    }
}
