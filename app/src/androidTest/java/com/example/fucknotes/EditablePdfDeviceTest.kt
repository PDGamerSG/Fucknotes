package com.example.fucknotes

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fucknotes.data.db.AppDatabase
import com.example.fucknotes.data.ink.InkCodec
import com.example.fucknotes.data.ink.InkStroke
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.PageAppearance
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.data.repo.NotesRepository
import com.example.fucknotes.export.EditablePdf
import com.example.fucknotes.export.NoteExporter
import com.example.fucknotes.media.NoteRestorer
import com.example.fucknotes.media.PdfImporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A PDF the app exports must still be a PDF every reader opens, and must come back as the same
 * note — strokes, paper, pages, tags — when imported again.
 */
@RunWith(AndroidJUnit4::class)
class EditablePdfDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private fun strokes(): List<InkStroke> {
        val brush = InkCodec.brushFor(InkTool.PEN, 0xFF171B23.toInt(), 3f, 1f)
        val inputs = MutableStrokeInputBatch()
        repeat(8) { inputs.add(InputToolType.STYLUS, 40f + it * 10f, 60f + (it % 2) * 6f, it * 16L) }
        return listOf(InkStroke(Stroke(brush, inputs), InkTool.PEN))
    }

    @Test fun anExportedNotebookOpensInAReaderAndComesBackEditable() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = NotesRepository(db)
            val folder = repo.createFolder("DSA", null)
            val id = repo.createNote(NoteType.NOTEBOOK, folder, "Graphs", PaperStyle.DOTTED, pageCount = 2,
                pagesExplicitlyCreated = true, appearance = PageAppearance.DARK)
            val original = requireNotNull(repo.getNote(id))
            repo.save(original.copy(drawingJson = InkCodec.encode(strokes())))
            repo.addTagToNote(id, "graphs")
            val note = requireNotNull(repo.getNote(id))

            val pdf = NoteExporter(context, repo).exportEditablePdf(note)
            try {
                // Still a PDF with the same pages for any reader.
                PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { assertEquals(2, it.pageCount) }
                val parts = EditablePdf.read(pdf)
                val payload = requireNotNull(NoteRestorer.payloadOf(parts))
                assertEquals("Graphs", payload.title)
                assertEquals(listOf("DSA"), payload.folderPath)
                assertEquals(listOf("graphs"), payload.tags)

                // The same install, same note, no newer edit: importing opens the note that exists.
                val sameId = PdfImporter(context, repo).import(Uri.fromFile(pdf), null)
                assertEquals(id, sameId)

                // From another install it becomes a new note with the same ink and paper.
                val foreign = parts.map { part ->
                    if (part.name != EditablePdf.PART_NOTE) part
                    else EditablePdf.Part(part.name, payload.copy(installId = "elsewhere").encode().toByteArray())
                }
                val restored = NoteRestorer(context, repo, ServiceLocator.settings(context)).restore(foreign, folderId = null)
                assertTrue(restored.created)
                assertNotEquals(id, restored.noteId)
                val copy = requireNotNull(repo.getNote(restored.noteId))
                assertEquals(note.drawingJson, copy.drawingJson)
                assertEquals(PaperStyle.DOTTED, copy.paperStyle)
                assertEquals(PageAppearance.DARK, copy.appearance)
                assertEquals(2, copy.pageCount)
                assertEquals(listOf("graphs"), repo.observeTagsForNote(copy.id).first().map { it.name })
                assertEquals(1, InkCodec.decode(copy.drawingJson).size)
            } finally { pdf.parentFile?.deleteRecursively() }
        } finally { db.close() }
    }

    @Test fun aPlainPdfStillImportsAsPagesWithInkOnTop() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = NotesRepository(db)
            val id = repo.createNote(NoteType.NOTEBOOK, null, "Flat", PaperStyle.LINED, pageCount = 1, pagesExplicitlyCreated = true)
            val pdf = NoteExporter(context, repo).export(requireNotNull(repo.getNote(id)), com.example.fucknotes.export.ExportFormat.PDF)
            try {
                assertTrue(EditablePdf.read(pdf).isEmpty())
                val imported = PdfImporter(context, repo).import(Uri.fromFile(pdf), null)
                assertNotEquals(id, imported)
                assertEquals(1, db.attachmentDao().forNote(imported).size)
            } finally { pdf.parentFile?.deleteRecursively() }
        } finally { db.close() }
    }

    @Test fun theDriveTablesArriveWithVersion8() {
        val name = "drive-migration-test"
        migrations.createDatabase(name, 7).close()
        val db = migrations.runMigrationsAndValidate(name, 8, true, AppDatabase.migrationForTests(7, 8))
        db.query("SELECT COUNT(*) FROM drive_files").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM drive_folders").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.close()
    }
}
