package com.example.fucknotes.media

import android.content.Context
import com.example.fucknotes.data.db.AttachmentEntity
import com.example.fucknotes.data.db.NoteEntity
import com.example.fucknotes.data.model.AttachmentType
import com.example.fucknotes.data.model.MAX_NOTEBOOK_PAGES
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.PageAppearance
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.data.repo.NotesRepository
import com.example.fucknotes.data.repo.SettingsRepository
import com.example.fucknotes.export.EditablePdf
import com.example.fucknotes.export.NotePayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Turns the parts found in an editable PDF back into a note on the shelf.
 *
 * A file coming home to the install that wrote it finds its own note: nothing is duplicated, and
 * the note is only overwritten when the file is the newer of the two. A file from elsewhere makes
 * a new note. Either way the result is the note as it was — ink, paper, pages, images — not a
 * picture of it.
 */
class NoteRestorer(
    private val context: Context,
    private val repo: NotesRepository,
    private val settings: SettingsRepository,
) {
    /** What happened to the file: which note it is now, and whether that note is new or was updated. */
    data class Outcome(val noteId: Long, val created: Boolean, val updated: Boolean)

    /** The decision rule on its own, so it can be checked without a database. */
    enum class Decision { CREATE, UPDATE_EXISTING, KEEP_EXISTING }

    companion object {
        fun decide(payload: NotePayload, installId: String, existing: NoteEntity?): Decision = when {
            payload.installId != installId || existing == null || existing.deletedAt != null -> Decision.CREATE
            payload.updatedAt > existing.updatedAt -> Decision.UPDATE_EXISTING
            else -> Decision.KEEP_EXISTING
        }

        /** The note data inside [parts], or null when the parts are not an editable note. */
        fun payloadOf(parts: List<EditablePdf.Part>): NotePayload? =
            parts.firstOrNull { it.name == EditablePdf.PART_NOTE }
                ?.let { NotePayload.decode(it.bytes.toString(Charsets.UTF_8)) }
    }

    suspend fun restore(
        parts: List<EditablePdf.Part>,
        folderId: Long?,
        archived: Boolean = false,
    ): Outcome = withContext(Dispatchers.IO) {
        val payload = payloadOf(parts) ?: error("The PDF carries no note data")
        val installId = settings.installId()
        val existing = repo.getNote(payload.sourceNoteId)
        when (decide(payload, installId, existing)) {
            Decision.KEEP_EXISTING -> Outcome(existing!!.id, created = false, updated = false)
            Decision.UPDATE_EXISTING -> {
                val images = writeImages(payload, parts, existing!!.id)
                repo.replaceNoteContent(existing.applyPayload(payload), images, payload.tags)
                Outcome(existing.id, created = false, updated = true)
            }
            Decision.CREATE -> {
                val entity = NoteEntity(folderId = folderId, archived = archived, position = System.currentTimeMillis())
                    .applyPayload(payload)
                val id = repo.restoreNote(entity, emptyList(), payload.tags)
                val images = writeImages(payload, parts, id)
                if (images.isNotEmpty()) repo.replaceNoteContent(entity.copy(id = id), images, payload.tags)
                Outcome(id, created = true, updated = false)
            }
        }
    }

    private fun NoteEntity.applyPayload(payload: NotePayload): NoteEntity = copy(
        title = payload.title,
        type = runCatching { NoteType.valueOf(payload.type) }.getOrDefault(NoteType.NOTEBOOK),
        body = payload.body,
        checklistJson = payload.checklistJson,
        drawingJson = payload.drawingJson,
        paperStyle = runCatching { PaperStyle.valueOf(payload.paperStyle) }.getOrDefault(PaperStyle.PLAIN),
        colorArgb = payload.colorArgb,
        lineColorArgb = payload.lineColorArgb,
        lineSpacingDp = payload.lineSpacingDp,
        pageFormat = payload.pageFormat,
        pageCount = payload.pageCount.coerceIn(1, MAX_NOTEBOOK_PAGES),
        pagesExplicitlyCreated = payload.pagesExplicitlyCreated,
        appearance = runCatching { PageAppearance.valueOf(payload.appearance) }.getOrDefault(PageAppearance.FOLLOW),
        createdAt = if (createdAt == 0L) payload.createdAt else createdAt,
        updatedAt = payload.updatedAt,
    )

    private fun writeImages(payload: NotePayload, parts: List<EditablePdf.Part>, noteId: Long): List<AttachmentEntity> {
        val byName = parts.associateBy { it.name }
        return payload.images.mapNotNull { ref ->
            val part = byName[ref.part] ?: return@mapNotNull null
            val extension = ref.part.substringAfterLast('.', "jpg")
            val file = MediaStore.newFile(context, extension)
            file.writeBytes(part.bytes)
            AttachmentEntity(
                noteId = noteId, path = file.absolutePath, type = AttachmentType.IMAGE,
                createdAt = ref.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                pageIndex = ref.pageIndex, pageX = ref.x, pageY = ref.y, pageWidth = ref.width, pageHeight = ref.height,
            )
        }
    }
}
