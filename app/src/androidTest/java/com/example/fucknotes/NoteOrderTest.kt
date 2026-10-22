package com.example.fucknotes

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fucknotes.data.db.AppDatabase
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.SortOrder
import com.example.fucknotes.data.repo.NotesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Manual shelf order: creation order by default, moves land where dropped, edits never reorder. */
@RunWith(AndroidJUnit4::class)
class NoteOrderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val repo = NotesRepository(db)

    @After fun cleanup() = db.close()

    private suspend fun order() = repo.observeNotes(null, inFolderView = false, archived = false, sort = SortOrder.MANUAL)
        .first().map { it.id }

    @Test fun notesKeepCreationOrderAndMoveOnlyWhenDropped() = runBlocking {
        val a = repo.createNote(NoteType.NOTEBOOK, null, "a"); Thread.sleep(2)
        val b = repo.createNote(NoteType.NOTEBOOK, null, "b"); Thread.sleep(2)
        val c = repo.createNote(NoteType.NOTEBOOK, null, "c")
        assertEquals(listOf(a, b, c), order())

        // Editing the first note does not move it.
        repo.save(requireNotNull(repo.getNote(a)).copy(title = "a edited"))
        assertEquals(listOf(a, b, c), order())

        // Dropping c between a and b lands it there without touching its edit time.
        val before = requireNotNull(repo.getNote(c)).updatedAt
        repo.moveNote(c, beforeId = a, afterId = b)
        assertEquals(listOf(a, c, b), order())
        assertEquals(before, requireNotNull(repo.getNote(c)).updatedAt)

        // Dropping at the ends.
        repo.moveNote(a, beforeId = b, afterId = null)
        assertEquals(listOf(c, b, a), order())
        repo.moveNote(a, beforeId = null, afterId = c)
        assertEquals(listOf(a, c, b), order())
    }

    @Test fun squeezedNeighboursAreRenumberedBeforeTheDrop() = runBlocking {
        val a = repo.createNote(NoteType.NOTEBOOK, null, "a")
        val b = repo.createNote(NoteType.NOTEBOOK, null, "b")
        val c = repo.createNote(NoteType.NOTEBOOK, null, "c")
        db.noteDao().setPosition(a, 10); db.noteDao().setPosition(b, 11); db.noteDao().setPosition(c, 12)
        repo.moveNote(c, beforeId = a, afterId = b)
        assertEquals(listOf(a, c, b), order())
    }

    @Test fun adoptingAnOrderMakesItTheManualOrder() = runBlocking {
        val a = repo.createNote(NoteType.NOTEBOOK, null, "a")
        val b = repo.createNote(NoteType.NOTEBOOK, null, "b")
        val c = repo.createNote(NoteType.NOTEBOOK, null, "c")
        repo.adoptOrder(listOf(b, a, c))
        assertEquals(listOf(b, a, c), order())
    }
}
