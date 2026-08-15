package com.example.fucknotes.data.repo

import com.example.fucknotes.data.db.AppDatabase
import com.example.fucknotes.data.db.AttachmentEntity
import com.example.fucknotes.data.db.FolderEntity
import com.example.fucknotes.data.db.NoteEntity
import com.example.fucknotes.data.db.NoteTagCrossRef
import com.example.fucknotes.data.db.TagEntity
import com.example.fucknotes.data.model.ChecklistItem
import com.example.fucknotes.data.model.Drawing
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

/** Single funnel for every read and write the UI performs. */
class NotesRepository(private val db: AppDatabase) {

    private val notes = db.noteDao()
    private val folders = db.folderDao()
    private val tags = db.tagDao()
    private val attachments = db.attachmentDao()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ---- notes -------------------------------------------------------------

    fun observeNotes(folderId: Long?, inFolderView: Boolean, archived: Boolean, sort: SortOrder) =
        notes.observeNotes(archived, inFolderView, folderId, sort.name)

    fun observeNote(id: Long): Flow<NoteEntity?> = notes.observeNote(id)

    suspend fun getNote(id: Long): NoteEntity? = notes.getNote(id)

    fun search(query: String): Flow<List<NoteEntity>> = notes.search(query.trim())

    fun observeTrash(): Flow<List<NoteEntity>> = notes.observeTrash()

    fun countInFolder(folderId: Long?): Flow<Int> = notes.countInFolder(folderId)

    suspend fun createNote(type: NoteType, folderId: Long?): Long {
        val now = System.currentTimeMillis()
        return notes.insert(
            NoteEntity(
                type = type,
                folderId = folderId,
                createdAt = now,
                updatedAt = now,
                checklistJson = if (type == NoteType.CHECKLIST) json.encodeToString(emptyList<ChecklistItem>()) else "",
                drawingJson = if (type == NoteType.DRAWING) json.encodeToString(Drawing()) else "",
            )
        )
    }

    suspend fun save(note: NoteEntity) = notes.update(note.copy(updatedAt = System.currentTimeMillis()))

    suspend fun trash(ids: List<Long>) = notes.moveToTrash(ids, System.currentTimeMillis())

    suspend fun restore(ids: List<Long>) = notes.restore(ids, System.currentTimeMillis())

    suspend fun deleteForever(ids: List<Long>) {
        ids.forEach { removeAttachmentFiles(it) }
        notes.deleteForever(ids)
    }

    suspend fun emptyTrash() {
        notes.allNotes().filter { it.deletedAt != null }.forEach { removeAttachmentFiles(it.id) }
        notes.emptyTrash()
    }

    suspend fun purgeOldTrash(retentionDays: Int = 30) =
        notes.purgeTrashOlderThan(System.currentTimeMillis() - retentionDays * 24L * 60 * 60 * 1000)

    suspend fun setPinned(ids: List<Long>, pinned: Boolean) =
        notes.setPinned(ids, pinned, System.currentTimeMillis())

    suspend fun setArchived(ids: List<Long>, archived: Boolean) =
        notes.setArchived(ids, archived, System.currentTimeMillis())

    suspend fun setLocked(ids: List<Long>, locked: Boolean) =
        notes.setLocked(ids, locked, System.currentTimeMillis())

    suspend fun unlockAll() = notes.unlockAll(System.currentTimeMillis())

    suspend fun moveToFolder(ids: List<Long>, folderId: Long?) =
        notes.moveToFolder(ids, folderId, System.currentTimeMillis())

    suspend fun setColor(ids: List<Long>, color: Int) =
        notes.setColor(ids, color, System.currentTimeMillis())

    suspend fun setReminder(id: Long, at: Long?) =
        notes.setReminder(id, at, System.currentTimeMillis())

    suspend fun notesWithReminders() = notes.notesWithReminders()

    /** Duplicates a note, its tag links and its attachment rows. */
    suspend fun duplicate(id: Long): Long? {
        val src = notes.getNote(id) ?: return null
        val now = System.currentTimeMillis()
        val newId = notes.insert(src.copy(id = 0, title = src.title + " (copy)", createdAt = now, updatedAt = now))
        tags.allCrossRefs().filter { it.noteId == id }.forEach { tags.link(NoteTagCrossRef(newId, it.tagId)) }
        attachments.forNote(id).forEach { attachments.insert(it.copy(id = 0, noteId = newId)) }
        return newId
    }

    /** Concatenates several notes into one, in the order given. */
    suspend fun merge(ids: List<Long>): Long? {
        if (ids.size < 2) return null
        val source = ids.mapNotNull { notes.getNote(it) }
        if (source.isEmpty()) return null
        val now = System.currentTimeMillis()
        val body = source.joinToString("\n\n") { n ->
            buildString {
                if (n.title.isNotBlank()) appendLine(n.title)
                append(plainTextOf(n))
            }.trim()
        }
        val mergedId = notes.insert(
            NoteEntity(
                title = source.first().title.ifBlank { "Merged note" },
                body = body,
                folderId = source.first().folderId,
                createdAt = now,
                updatedAt = now,
            )
        )
        trash(ids)
        return mergedId
    }

    // ---- folders -----------------------------------------------------------

    fun observeFolders(): Flow<List<FolderEntity>> = folders.observeAll()

    suspend fun createFolder(name: String, parentId: Long?, colorArgb: Int = 0): Long =
        folders.insert(FolderEntity(name = name, parentId = parentId, colorArgb = colorArgb))

    suspend fun renameFolder(folder: FolderEntity, name: String) = folders.update(folder.copy(name = name))

    suspend fun updateFolder(folder: FolderEntity) = folders.update(folder)

    /** Deletes a folder and every descendant, leaving their notes in "Uncategorised". */
    suspend fun deleteFolderTree(rootId: Long) {
        val all = folders.allFolders()
        val doomed = mutableListOf(rootId)
        var frontier = listOf(rootId)
        while (frontier.isNotEmpty()) {
            val next = all.filter { it.parentId in frontier }.map { it.id }
            doomed += next
            frontier = next
        }
        notes.detachFromFolders(doomed)
        folders.deleteAll(doomed)
    }

    /** Depth of a folder in the tree, used to enforce the nesting limit. */
    suspend fun folderDepth(parentId: Long?): Int {
        if (parentId == null) return 0
        val all = folders.allFolders().associateBy { it.id }
        var depth = 0
        var cursor = all[parentId]
        while (cursor != null) {
            depth++
            cursor = cursor.parentId?.let { all[it] }
        }
        return depth
    }

    // ---- tags --------------------------------------------------------------

    fun observeTags(): Flow<List<TagEntity>> = tags.observeAll()

    fun observeTagsForNote(noteId: Long): Flow<List<TagEntity>> = tags.observeTagsForNote(noteId)

    fun observeNotesForTag(tagId: Long): Flow<List<NoteEntity>> = tags.observeNotesForTag(tagId)

    suspend fun addTagToNote(noteId: Long, name: String) {
        val clean = name.trim().removePrefix("#")
        if (clean.isEmpty()) return
        val existing = tags.findByName(clean)
        val tagId = existing?.id ?: tags.insert(TagEntity(name = clean)).takeIf { it > 0 }
            ?: tags.findByName(clean)?.id ?: return
        tags.link(NoteTagCrossRef(noteId, tagId))
    }

    suspend fun removeTagFromNote(noteId: Long, tagId: Long) = tags.unlink(noteId, tagId)

    suspend fun deleteTag(tagId: Long) = tags.delete(tagId)

    // ---- attachments -------------------------------------------------------

    fun observeAttachments(noteId: Long): Flow<List<AttachmentEntity>> = attachments.observeForNote(noteId)

    suspend fun addAttachment(attachment: AttachmentEntity): Long = attachments.insert(attachment)

    suspend fun deleteAttachment(attachment: AttachmentEntity) {
        runCatching { java.io.File(attachment.path).delete() }
        attachments.delete(attachment.id)
    }

    private suspend fun removeAttachmentFiles(noteId: Long) {
        attachments.forNote(noteId).forEach { runCatching { java.io.File(it.path).delete() } }
    }

    // ---- helpers -----------------------------------------------------------

    fun checklistOf(note: NoteEntity): List<ChecklistItem> =
        if (note.checklistJson.isBlank()) emptyList()
        else runCatching { json.decodeFromString<List<ChecklistItem>>(note.checklistJson) }.getOrDefault(emptyList())

    fun encodeChecklist(items: List<ChecklistItem>): String = json.encodeToString(items)

    fun drawingOf(note: NoteEntity): Drawing =
        if (note.drawingJson.isBlank()) Drawing()
        else runCatching { json.decodeFromString<Drawing>(note.drawingJson) }.getOrDefault(Drawing())

    fun encodeDrawing(drawing: Drawing): String = json.encodeToString(drawing)

    /** Flattens any note type to searchable / exportable plain text. */
    fun plainTextOf(note: NoteEntity): String = when (note.type) {
        NoteType.TEXT -> note.body
        NoteType.CHECKLIST -> checklistOf(note).joinToString("\n") {
            (if (it.checked) "[x] " else "[ ] ") + it.text
        }
        NoteType.DRAWING -> note.body
    }
}
