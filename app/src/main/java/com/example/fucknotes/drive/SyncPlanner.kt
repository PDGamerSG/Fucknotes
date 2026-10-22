package com.example.fucknotes.drive

import com.example.fucknotes.export.ExportNames

/**
 * Decides what a sync has to do, from the shelf and the bookkeeping alone.
 *
 * Pure: no Drive, no database, so the rules can be checked on the JVM. Each live note has one
 * place in the Drive tree — its folder, "Unfiled", or "Archive" — and one file name; a note that
 * moved, was renamed, or changed since its last upload gets the matching action, and a note that
 * left the shelf has its file trashed on Drive too.
 */
object SyncPlanner {
    const val KEY_ROOT = "root"
    const val KEY_UNFILED = "unfiled"
    const val KEY_ARCHIVE = "archive"
    const val NAME_UNFILED = "Unfiled"
    const val NAME_ARCHIVE = "Archive"

    data class LocalNote(
        val id: Long,
        val title: String,
        val updatedAt: Long,
        val folderId: Long?,
        val archived: Boolean,
        val trashed: Boolean,
    ) {
        val folderKey: String get() = folderKey(folderId, archived)
    }

    data class Mapping(
        val noteId: Long,
        val fileId: String,
        val folderKey: String,
        val name: String,
        val syncedUpdatedAt: Long,
    )

    sealed class Action {
        abstract val noteId: Long

        /** Export and upload; [existingFileId] is replaced in place when present. */
        data class Upload(override val noteId: Long, val name: String, val folderKey: String, val existingFileId: String?) : Action()
        data class Move(override val noteId: Long, val fileId: String, val fromKey: String, val toKey: String) : Action()
        data class Rename(override val noteId: Long, val fileId: String, val name: String) : Action()

        /** The note is gone from the shelf; trash its file and drop the mapping. */
        data class Trash(override val noteId: Long, val fileId: String) : Action()
    }

    fun folderKey(folderId: Long?, archived: Boolean): String = when {
        archived -> KEY_ARCHIVE
        folderId == null -> KEY_UNFILED
        else -> "folder:$folderId"
    }

    fun folderIdOf(key: String): Long? = key.removePrefix("folder:").toLongOrNull()?.takeIf { key.startsWith("folder:") }

    /** File names per note, unique within each Drive folder, in note-id order so they stay put. */
    fun fileNames(notes: List<LocalNote>): Map<Long, String> {
        val result = mutableMapOf<Long, String>()
        notes.filter { !it.trashed }.groupBy { it.folderKey }.values.forEach { group ->
            val used = mutableSetOf<String>()
            group.sortedBy { it.id }.forEach { note ->
                result[note.id] = ExportNames.unique(note.title.ifBlank { "Untitled" }, used, ".pdf")
            }
        }
        return result
    }

    fun plan(notes: List<LocalNote>, mappings: List<Mapping>): List<Action> {
        val actions = mutableListOf<Action>()
        val names = fileNames(notes)
        val byId = mappings.associateBy { it.noteId }
        val live = notes.filter { !it.trashed }

        live.forEach { note ->
            val name = names.getValue(note.id)
            val mapping = byId[note.id]
            val target = note.folderKey
            if (mapping == null) {
                actions += Action.Upload(note.id, name, target, existingFileId = null)
                return@forEach
            }
            val changed = note.updatedAt > mapping.syncedUpdatedAt
            if (changed) actions += Action.Upload(note.id, name, target, mapping.fileId)
            else if (mapping.name != name) actions += Action.Rename(note.id, mapping.fileId, name)
            if (mapping.folderKey != target) actions += Action.Move(note.id, mapping.fileId, mapping.folderKey, target)
        }

        val liveIds = live.map { it.id }.toSet()
        mappings.filter { it.noteId !in liveIds }.forEach { actions += Action.Trash(it.noteId, it.fileId) }
        return actions
    }
}
