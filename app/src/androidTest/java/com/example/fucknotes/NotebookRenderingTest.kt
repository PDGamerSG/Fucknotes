package com.example.fucknotes

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fucknotes.data.db.AppDatabase
import com.example.fucknotes.data.db.AttachmentEntity
import com.example.fucknotes.data.db.FolderEntity
import com.example.fucknotes.data.ink.InkCodec
import com.example.fucknotes.data.ink.InkStroke
import com.example.fucknotes.data.model.AttachmentType
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.repo.NotesRepository
import com.example.fucknotes.export.ExportFormat
import com.example.fucknotes.export.FolderExporter
import com.example.fucknotes.export.NoteExporter
import com.example.fucknotes.media.MediaStore
import com.example.fucknotes.media.PdfImporter
import com.example.fucknotes.ui.home.HomeExportMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/** Real Android codecs and PDF/ink rendering, isolated from the tablet owner's notes. */
@RunWith(AndroidJUnit4::class)
class NotebookRenderingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val repo = NotesRepository(db)
    private val exporter = NoteExporter(context, repo)
    private val files = mutableListOf<File>()
    private val notes = mutableListOf<Long>()

    private fun temporary(extension: String) = File(context.cacheDir,
        "notebook-render-${UUID.randomUUID()}.$extension").also(files::add)

    @After fun cleanup() = runBlocking {
        repo.deleteForever(notes)
        files.forEach { it.delete() }
        db.close()
    }

    @Test fun savedInkAndHighlightExportAboveOriginalPdfContent() = runBlocking {
        val source = temporary("pdf")
        val original = PdfDocument()
        try {
            val page = original.startPage(PdfDocument.PageInfo.Builder(300, 400, 1).create())
            page.canvas.drawColor(Color.WHITE)
            // A black glyph-like shape makes preservation measurable without font rasterization differences.
            page.canvas.drawRect(140f, 190f, 160f, 210f, Paint().apply { color = Color.BLACK })
            original.finishPage(page)
            source.outputStream().use { original.writeTo(it) }
        } finally { original.close() }
        val id = PdfImporter(context, repo).import(Uri.fromFile(source), null).also(notes::add)
        val note = requireNotNull(repo.getNote(id))
        val strokes = listOf(stroke(InkTool.MARKER, Color.BLACK, 10f, 90f),
            stroke(InkTool.HIGHLIGHTER, Color.YELLOW, 36f, 200f))
        repo.save(note.copy(drawingJson = InkCodec.encode(strokes)))
        val saved = requireNotNull(repo.getNote(id))
        assertEquals(2, repo.inkOf(saved).size)
        val output = exporter.export(saved, ExportFormat.PDF).also(files::add)
        PdfRenderer(ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY)).use { pdf ->
            pdf.openPage(0).use { page ->
                val bitmap = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
                try {
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    assertTrue("Saved handwriting is missing", Color.red(bitmap.getPixel(150, 90)) < 80)
                    val highlightedPaper = bitmap.getPixel(100, 200)
                    assertTrue("Highlight missing over white paper", Color.blue(highlightedPaper) < 245)
                    val originalGlyph = bitmap.getPixel(150, 200)
                    assertTrue("Highlighter hides original black content", Color.red(originalGlyph) < 150)
                    assertTrue(Color.red(highlightedPaper) > Color.red(originalGlyph) + 80)
                } finally { bitmap.recycle() }
            }
        }
    }

    @Test fun imageImportPreservesTransparencyAndDuplicatedImages() = runBlocking {
        val source = temporary("png")
        val bitmap = Bitmap.createBitmap(80, 50, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.TRANSPARENT)
            bitmap.setPixel(40, 25, Color.RED)
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
        val imported = requireNotNull(MediaStore.importImage(context, Uri.fromFile(source))).also(files::add)
        MediaStore.decodeImage(imported).let {
            try {
                assertEquals(0, Color.alpha(it.getPixel(0, 0)))
                assertEquals(Color.RED, it.getPixel(40, 25))
            } finally { it.recycle() }
        }
        val id = repo.createNote(NoteType.NOTEBOOK, null).also(notes::add)
        repo.addAttachment(AttachmentEntity(noteId = id, path = imported.path, type = AttachmentType.IMAGE))
        val copy = requireNotNull(repo.duplicate(id)).also(notes::add)
        repo.deleteAttachment(repo.observeAttachments(id).first().single())
        assertTrue("Removing one copy destroyed the other's image", imported.exists())
        assertEquals(imported.path, repo.observeAttachments(copy).first().single().path)
    }

    @Test fun cameraExifOrientationIsAppliedDuringImport() = runBlocking {
        val source = temporary("jpg")
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.RED)
            source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        } finally { bitmap.recycle() }
        ExifInterface(source.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val imported = requireNotNull(MediaStore.importImage(context, Uri.fromFile(source))).also(files::add)
        MediaStore.decodeImage(imported).let {
            try { assertEquals(40, it.width); assertEquals(80, it.height) }
            finally { it.recycle() }
        }
    }

    @Test fun folderZipContainsActualPdfsAndPreservesNestedNames() = runBlocking {
        val root = repo.createFolder("College", null)
        val folder = repo.createFolder("Lectures", root)
        repeat(2) { repo.createNote(NoteType.NOTEBOOK, folder, "Lecture", pageCount = 2,
            pagesExplicitlyCreated = true).also(notes::add) }
        val selected = repo.notesInFolderTree(root)
        val zip = temporary("zip")
        FolderExporter(context, exporter).save(selected,
            listOf(FolderEntity(root, "College"), FolderEntity(folder, "Lectures", root)),
            root, "College", HomeExportMode.ZIP, Uri.fromFile(zip)) { _, _ -> }
        ZipFile(zip).use { archive ->
            assertEquals(setOf("Lectures/Lecture.pdf", "Lectures/Lecture (2).pdf"),
                archive.entries().asSequence().map { it.name }.toSet())
            archive.entries().asSequence().forEach { entry ->
                val pdfFile = temporary("pdf")
                archive.getInputStream(entry).use { input -> pdfFile.outputStream().use { input.copyTo(it) } }
                PdfRenderer(ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)).use {
                    assertEquals(2, it.pageCount)
                }
            }
        }
    }

    private fun stroke(tool: InkTool, color: Int, width: Float, y: Float): InkStroke {
        val inputs = MutableStrokeInputBatch()
        repeat(10) { inputs.add(InputToolType.STYLUS, 60f + it * 20f, y, it * 16L) }
        return InkStroke(Stroke(InkCodec.brushFor(tool, color, width, 1f), inputs), tool)
    }
}
