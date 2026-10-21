package com.example.fucknotes.export

import com.example.fucknotes.data.db.FolderEntity
import com.example.fucknotes.data.db.NoteEntity
import java.util.Locale

/** Shared by ZIP and directory exports so both retain the same unambiguous hierarchy. */
internal fun exportPathsFor(
    notes: List<NoteEntity>, folders: List<FolderEntity>, rootId: Long?,
): Map<Long, List<String>> {
    val byId = folders.associateBy { it.id }
    val usedFolders = mutableMapOf<Long?, MutableSet<String>>()
    val names = folders.sortedBy { it.id }.associate { folder ->
        folder.id to ExportNames.unique(folder.name, usedFolders.getOrPut(folder.parentId) { mutableSetOf() })
    }
    val usedFiles = mutableMapOf<Long?, MutableSet<String>>()
    return notes.associate { note ->
        val ancestors = mutableListOf<String>()
        val visited = mutableSetOf<Long>()
        var id = note.folderId
        while (id != null && id != rootId && visited.add(id)) {
            val folder = byId[id] ?: break
            ancestors.add(0, names.getValue(id))
            id = folder.parentId
        }
        // A folder named "Lecture.pdf" and a note named "Lecture" share a directory namespace.
        // Reserve subfolder names before allocating filenames, including on case-insensitive disks.
        val used = usedFiles.getOrPut(note.folderId) {
            folders.filter { it.parentId == note.folderId }
                .map { names.getValue(it.id).lowercase(Locale.ROOT) }.toMutableSet()
        }
        val name = ExportNames.unique(note.title, used, ".pdf")
        note.id to (ancestors + name)
    }
}
