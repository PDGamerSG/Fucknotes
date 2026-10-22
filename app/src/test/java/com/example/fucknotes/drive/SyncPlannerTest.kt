package com.example.fucknotes.drive

import com.example.fucknotes.drive.SyncPlanner.Action
import com.example.fucknotes.drive.SyncPlanner.LocalNote
import com.example.fucknotes.drive.SyncPlanner.Mapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlannerTest {
    private fun note(id: Long, title: String = "Note $id", updatedAt: Long = 10, folderId: Long? = null, archived: Boolean = false, trashed: Boolean = false) =
        LocalNote(id, title, updatedAt, folderId, archived, trashed)

    @Test fun folderKeysNameThePlaceInTheDriveTree() {
        assertEquals("unfiled", SyncPlanner.folderKey(null, false))
        assertEquals("folder:4", SyncPlanner.folderKey(4, false))
        assertEquals("archive", SyncPlanner.folderKey(4, true))
        assertEquals(4L, SyncPlanner.folderIdOf("folder:4"))
        assertEquals(null, SyncPlanner.folderIdOf("unfiled"))
    }

    @Test fun aNewNoteIsUploadedAndAnUntouchedOneIsLeftAlone() {
        val notes = listOf(note(1, updatedAt = 10), note(2, updatedAt = 10))
        val mappings = listOf(Mapping(2, "f2", "unfiled", "Note 2.pdf", syncedUpdatedAt = 10))
        assertEquals(listOf(Action.Upload(1, "Note 1.pdf", "unfiled", null)), SyncPlanner.plan(notes, mappings))
    }

    @Test fun anEditedNoteIsUploadedInPlace() {
        val notes = listOf(note(1, updatedAt = 20))
        val mappings = listOf(Mapping(1, "f1", "unfiled", "Note 1.pdf", syncedUpdatedAt = 10))
        assertEquals(listOf(Action.Upload(1, "Note 1.pdf", "unfiled", "f1")), SyncPlanner.plan(notes, mappings))
    }

    @Test fun aMovedOrRenamedNoteGetsExactlyThatAction() {
        val moved = listOf(note(1, folderId = 3))
        assertEquals(
            listOf(Action.Move(1, "f1", "unfiled", "folder:3")),
            SyncPlanner.plan(moved, listOf(Mapping(1, "f1", "unfiled", "Note 1.pdf", 10))),
        )
        val renamed = listOf(note(1, title = "Graphs"))
        assertEquals(
            listOf(Action.Rename(1, "f1", "Graphs.pdf")),
            SyncPlanner.plan(renamed, listOf(Mapping(1, "f1", "unfiled", "Note 1.pdf", 10))),
        )
        // Edited and moved: the upload carries the new name, the move follows.
        val both = listOf(note(1, title = "Graphs", updatedAt = 20, archived = true))
        assertEquals(
            listOf(Action.Upload(1, "Graphs.pdf", "archive", "f1"), Action.Move(1, "f1", "unfiled", "archive")),
            SyncPlanner.plan(both, listOf(Mapping(1, "f1", "unfiled", "Note 1.pdf", 10))),
        )
    }

    @Test fun aNoteGoneFromTheShelfHasItsFileTrashed() {
        val notes = listOf(note(1, trashed = true))
        val mappings = listOf(Mapping(1, "f1", "unfiled", "Note 1.pdf", 10), Mapping(9, "f9", "unfiled", "Gone.pdf", 10))
        assertEquals(listOf(Action.Trash(1, "f1"), Action.Trash(9, "f9")), SyncPlanner.plan(notes, mappings))
    }

    @Test fun namesAreUniquePerFolderAndStable() {
        val notes = listOf(note(3, title = "Untitled"), note(1, title = "Untitled"), note(2, title = "Untitled", folderId = 5), note(4, title = ""))
        val names = SyncPlanner.fileNames(notes)
        assertEquals("Untitled.pdf", names[1])
        assertEquals("Untitled (2).pdf", names[3])
        assertEquals("Untitled.pdf", names[2])
        assertEquals("Untitled (3).pdf", names[4])
        assertEquals("A: b?.pdf".replace(":", "_").replace("?", "_"), SyncPlanner.fileNames(listOf(note(8, title = "A: b?"))).getValue(8))
    }

    @Test fun driveQueriesEscapeQuotesAndBackslashes() {
        assertEquals("O\\'Brien \\\\ notes", DriveApi.q("O'Brien \\ notes"))
    }

    @Test fun foldersAreOrderedParentsFirst() {
        val folders = listOf(
            com.example.fucknotes.data.db.FolderEntity(id = 3, name = "Graphs", parentId = 1),
            com.example.fucknotes.data.db.FolderEntity(id = 1, name = "DSA", parentId = null),
            com.example.fucknotes.data.db.FolderEntity(id = 2, name = "System Design", parentId = null),
            com.example.fucknotes.data.db.FolderEntity(id = 9, name = "Orphan", parentId = 42),
        )
        val ordered = DriveSync.orderedFolders(folders).map { it.id }
        assertEquals(listOf(1L, 3L, 2L, 9L), ordered)
        assertTrue(ordered.indexOf(1L) < ordered.indexOf(3L))
    }

    @Test fun relativeTimesReadNaturally() {
        val now = 1_000_000_000L
        assertEquals("just now", com.example.fucknotes.ui.home.relative(now - 30_000, now))
        assertEquals("5 min ago", com.example.fucknotes.ui.home.relative(now - 5 * 60_000, now))
        assertEquals("3 h ago", com.example.fucknotes.ui.home.relative(now - 3 * 3_600_000, now))
    }
}
