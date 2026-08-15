package com.example.fucknotes.data.backup

import android.content.Context
import android.net.Uri
import com.example.fucknotes.data.db.AppDatabase
import com.example.fucknotes.data.db.AttachmentEntity
import com.example.fucknotes.data.db.FolderEntity
import com.example.fucknotes.data.db.NoteEntity
import com.example.fucknotes.data.db.NoteTagCrossRef
import com.example.fucknotes.data.db.TagEntity
import com.example.fucknotes.data.model.AttachmentType
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.media.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Local backup and restore.
 *
 * A backup is a zip holding `data.json` plus every attachment file under `media/`, so a restore on
 * a fresh install brings images and voice notes back with the text.
 */
class BackupManager(private val context: Context, private val db: AppDatabase) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    data class Payload(
        val version: Int = FORMAT_VERSION,
        val exportedAt: Long = System.currentTimeMillis(),
        val notes: List<NoteDto> = emptyList(),
        val folders: List<FolderDto> = emptyList(),
        val tags: List<TagDto> = emptyList(),
        val noteTags: List<NoteTagDto> = emptyList(),
        val attachments: List<AttachmentDto> = emptyList(),
    )

    @Serializable
    data class NoteDto(
        val id: Long, val title: String, val body: String, val type: String,
        val checklistJson: String, val drawingJson: String, val folderId: Long?,
        val colorArgb: Int, val paperStyle: String, val pinned: Boolean, val archived: Boolean,
        val locked: Boolean, val deletedAt: Long?, val reminderAt: Long?,
        val createdAt: Long, val updatedAt: Long,
    )

    @Serializable
    data class FolderDto(val id: Long, val name: String, val parentId: Long?, val colorArgb: Int, val createdAt: Long)

    @Serializable
    data class TagDto(val id: Long, val name: String, val colorArgb: Int)

    @Serializable
    data class NoteTagDto(val noteId: Long, val tagId: Long)

    @Serializable
    data class AttachmentDto(
        val id: Long, val noteId: Long, val fileName: String,
        val type: String, val durationMs: Long, val createdAt: Long,
    )

    /** Writes a full backup zip to [target] (a SAF document uri). Returns the note count written. */
    suspend fun exportTo(target: Uri): Int = withContext(Dispatchers.IO) {
        val notes = db.noteDao().allNotes()
        val attachments = db.attachmentDao().allAttachments()
        val payload = Payload(
            notes = notes.map { it.toDto() },
            folders = db.folderDao().allFolders().map { FolderDto(it.id, it.name, it.parentId, it.colorArgb, it.createdAt) },
            tags = db.tagDao().allTags().map { TagDto(it.id, it.name, it.colorArgb) },
            noteTags = db.tagDao().allCrossRefs().map { NoteTagDto(it.noteId, it.tagId) },
            attachments = attachments.map {
                AttachmentDto(it.id, it.noteId, File(it.path).name, it.type.name, it.durationMs, it.createdAt)
            },
        )

        context.contentResolver.openOutputStream(target)?.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(ENTRY_DATA))
                zip.write(json.encodeToString(payload).toByteArray())
                zip.closeEntry()

                attachments.forEach { attachment ->
                    val file = File(attachment.path)
                    if (!file.exists()) return@forEach
                    zip.putNextEntry(ZipEntry("$DIR_MEDIA${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: error("Could not open the backup destination for writing")

        notes.size
    }

    /**
     * Restores from [source]. Existing rows are replaced by id, so restoring the same backup twice
     * is idempotent rather than duplicating every note.
     */
    suspend fun importFrom(source: Uri): Int = withContext(Dispatchers.IO) {
        var payload: Payload? = null
        val mediaDir = MediaStore.mediaDir(context)

        context.contentResolver.openInputStream(source)?.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    when {
                        name == ENTRY_DATA -> payload = json.decodeFromString(zip.readBytes().decodeToString())

                        name.startsWith(DIR_MEDIA) && !entry.isDirectory -> {
                            val out = File(mediaDir, File(name).name)
                            out.outputStream().use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: error("Could not open the backup file for reading")

        val data = payload ?: error("Backup file has no data.json")
        require(data.version <= FORMAT_VERSION) { "Backup was made by a newer version of the app" }

        db.folderDao().upsertAll(data.folders.map { FolderEntity(it.id, it.name, it.parentId, it.colorArgb, it.createdAt) })
        db.noteDao().upsertAll(data.notes.map { it.toEntity() })
        db.tagDao().upsertAll(data.tags.map { TagEntity(it.id, it.name, it.colorArgb) })
        db.tagDao().upsertCrossRefs(data.noteTags.map { NoteTagCrossRef(it.noteId, it.tagId) })
        db.attachmentDao().upsertAll(
            data.attachments.map {
                AttachmentEntity(
                    id = it.id,
                    noteId = it.noteId,
                    path = File(mediaDir, it.fileName).absolutePath,
                    type = runCatching { AttachmentType.valueOf(it.type) }.getOrDefault(AttachmentType.IMAGE),
                    durationMs = it.durationMs,
                    createdAt = it.createdAt,
                )
            }
        )

        data.notes.size
    }

    private fun NoteEntity.toDto() = NoteDto(
        id, title, body, type.name, checklistJson, drawingJson, folderId, colorArgb,
        paperStyle.name, pinned, archived, locked, deletedAt, reminderAt, createdAt, updatedAt,
    )

    private fun NoteDto.toEntity() = NoteEntity(
        id = id,
        title = title,
        body = body,
        type = runCatching { NoteType.valueOf(type) }.getOrDefault(NoteType.TEXT),
        checklistJson = checklistJson,
        drawingJson = drawingJson,
        folderId = folderId,
        colorArgb = colorArgb,
        paperStyle = runCatching { PaperStyle.valueOf(paperStyle) }.getOrDefault(PaperStyle.PLAIN),
        pinned = pinned,
        archived = archived,
        locked = locked,
        deletedAt = deletedAt,
        reminderAt = reminderAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        const val FORMAT_VERSION = 1
        private const val ENTRY_DATA = "data.json"
        private const val DIR_MEDIA = "media/"
        const val MIME = "application/zip"

        fun suggestedFileName(): String {
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
                .format(java.util.Date())
            return "fucknotes-backup-$stamp.zip"
        }
    }
}
