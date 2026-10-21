package com.example.fucknotes.export

import com.example.fucknotes.data.db.FolderEntity
import com.example.fucknotes.data.db.NoteEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ExportPathsTest {
    @Test fun collectionExportPreservesNestedFoldersAndDuplicateTitles() {
        val folders = listOf(
            FolderEntity(1, "College"),
            FolderEntity(2, "Physics", 1),
            FolderEntity(3, "Physics", 1),
        )
        val notes = listOf(
            NoteEntity(id = 10, title = "Lecture", folderId = 2),
            NoteEntity(id = 11, title = "Lecture", folderId = 2),
            NoteEntity(id = 12, title = "Lecture", folderId = 3),
        )
        val paths = exportPathsFor(notes, folders, rootId = 1)
        assertEquals(listOf("Physics", "Lecture.pdf"), paths[10L])
        assertEquals(listOf("Physics", "Lecture (2).pdf"), paths[11L])
        assertEquals(listOf("Physics (2)", "Lecture.pdf"), paths[12L])
    }

    @Test fun pdfFilenameCannotCollideWithChildDirectory() {
        val folders = listOf(FolderEntity(1, "Collection"), FolderEntity(2, "lecture.PDF", 1))
        val notes = listOf(
            NoteEntity(id = 10, title = "Lecture", folderId = 1),
            NoteEntity(id = 11, title = "Notes", folderId = 2),
        )
        val paths = exportPathsFor(notes, folders, rootId = 1)
        assertEquals(listOf("Lecture (2).pdf"), paths[10L])
        assertEquals(listOf("lecture.PDF", "Notes.pdf"), paths[11L])
    }
}
