package com.example.fucknotes

import androidx.room.testing.MigrationTestHelper
import androidx.room.useWriterConnection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fucknotes.data.db.AppDatabase
import com.example.fucknotes.data.db.AttachmentEntity
import com.example.fucknotes.data.db.NoteEntity
import com.example.fucknotes.data.ink.PageSpec
import com.example.fucknotes.data.model.AttachmentType
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.PageAppearance
import com.example.fucknotes.data.model.SortOrder
import com.example.fucknotes.data.repo.NotesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Uses the app's database configuration and a separate file from the tablet owner's notes. */
@RunWith(AndroidJUnit4::class)
class LargeDrawingCursorTest {
    @get:Rule val migrations = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java,
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "large-drawing-${UUID.randomUUID()}.db"
    private var db = AppDatabase.builder(context, name).build()

    @After fun cleanup() {
        db.close()
        context.deleteDatabase(name)
    }

    @Test fun largeNotebookInkSurvivesReadSaveAndReopen() = runBlocking {
        val ink = "x".repeat(3_000_000)
        val id = db.noteDao().insert(
            NoteEntity(
                title = "Large notebook",
                type = NoteType.NOTEBOOK,
                drawingJson = ink,
            )
        )

        assertEquals(ink, db.noteDao().getNote(id)?.drawingJson)
        val notes = db.noteDao().observeNotes(
            archived = false,
            filterFolder = false,
            folderId = null,
            sort = "UPDATED_DESC",
        ).first()

        assertEquals(listOf("Large notebook"), notes.map { it.title })
        assertEquals(ink, notes.single().drawingJson)
        assertEquals(ink, db.noteDao().observeNote(id).first()?.drawingJson)
        assertEquals(ink, db.noteDao().allNotes().single().drawingJson)

        val repo = NotesRepository(db)
        repo.save(requireNotNull(repo.getNote(id)).copy(title = "Renamed notebook"))
        val copy = requireNotNull(repo.duplicate(id))
        assertEquals(ink, repo.getNote(copy)?.drawingJson)

        db.close()
        db = AppDatabase.builder(context, name).build()
        assertEquals("Renamed notebook", db.noteDao().getNote(id)?.title)
        assertEquals(ink, db.noteDao().getNote(id)?.drawingJson)
    }

    @Test fun oldestSchemaUpgradesWithoutLosingLargeInk() = runBlocking {
        db.close()
        val ink = "x".repeat(3_000_000)
        migrations.createDatabase(name, 1).apply {
            execSQL(
                "INSERT INTO notes (id, title, body, type, checklistJson, drawingJson, colorArgb, " +
                    "paperStyle, pinned, archived, locked, createdAt, updatedAt) " +
                    "VALUES (1, 'Old notebook', '', 'NOTEBOOK', '', ?, 0, 'LINED', 0, 0, 0, 123, 123)",
                arrayOf(ink),
            )
            close()
        }

        db = AppDatabase.builder(context, name).build()
        val note = requireNotNull(db.noteDao().getNote(1))
        assertEquals(ink, note.drawingJson)
        assertEquals("Old notebook", note.title)
        assertEquals("A4", note.pageFormat)
        assertEquals(PageAppearance.FOLLOW, note.appearance)
        assertEquals(123L, note.position)
        assertTrue(db.driveDao().allFiles().isEmpty())
    }

    @Test fun notebookImportAndReorderCommitWithTheConfiguredDriver() = runBlocking {
        val repo = NotesRepository(db)
        val image = AttachmentEntity(noteId = 0, path = "isolated-test-image.png", type = AttachmentType.IMAGE, pageIndex = 0)
        val imported = repo.createImportedNotebook("Imported", null, PageSpec.Default, listOf(image))
        val restored = repo.restoreNote(NoteEntity(title = "Restored"), emptyList(), listOf("lecture"))
        repo.replaceNoteContent(requireNotNull(repo.getNote(restored)).copy(title = "Updated"), emptyList(), listOf("revised"))
        repo.adoptOrder(listOf(restored, imported))
        repo.moveNote(imported, beforeId = null, afterId = restored)

        assertEquals(1, repo.getNote(imported)?.pageCount)
        assertEquals(image.path, repo.observeAttachments(imported).first().single().path)
        assertEquals("Updated", repo.getNote(restored)?.title)
        assertEquals(listOf("lecture", "revised"), repo.observeTagsForNote(restored).first().map { it.name })
        assertEquals(listOf(imported, restored), repo.observeNotes(null, false, false, SortOrder.MANUAL).first().map { it.id })
    }

    @Test fun failedAttachmentInsertRollsBackRestoredNote() = runBlocking {
        db.useWriterConnection { connection ->
            connection.usePrepared(
                "CREATE TRIGGER reject_test_attachment BEFORE INSERT ON attachments " +
                    "BEGIN SELECT RAISE(ABORT, 'Test attachment failure'); END"
            ) { it.step() }
        }
        val failure = runCatching {
            NotesRepository(db).restoreNote(
                NoteEntity(title = "Must roll back"),
                listOf(AttachmentEntity(noteId = 0, path = "rejected.png", type = AttachmentType.IMAGE)),
                emptyList(),
            )
        }.exceptionOrNull()

        assertTrue("Expected the attachment insert to fail", failure != null)
        assertTrue("A partial note survived the failed import", db.noteDao().allNotes().isEmpty())
    }
}
