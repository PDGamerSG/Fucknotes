package com.example.fucknotes.export

import android.content.Context
import com.example.fucknotes.data.db.AttachmentEntity
import com.example.fucknotes.data.db.NoteEntity
import com.example.fucknotes.data.model.AttachmentType
import com.example.fucknotes.data.repo.NotesRepository
import com.example.fucknotes.data.repo.SettingsRepository
import com.example.fucknotes.media.ImageParts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Builds the parts that make a PDF export editable, and puts them in.
 *
 * `note.json` carries the note; each page image is its own part, named so the JSON can refer to
 * it. Voice recordings stay out: they are not on the page, and they are large.
 */
class EditableNotes(
    private val context: Context,
    private val repo: NotesRepository,
    private val settings: SettingsRepository,
) {
    suspend fun parts(note: NoteEntity, attachments: List<AttachmentEntity>): List<EditablePdf.Part> =
        withContext(Dispatchers.IO) {
            val images = mutableListOf<NotePayload.ImageRef>()
            val parts = mutableListOf<EditablePdf.Part>()
            attachments.filter { it.type == AttachmentType.IMAGE }.forEachIndexed { index, attachment ->
                val encoded = ImageParts.encode(File(attachment.path)) ?: return@forEachIndexed
                val name = "image-${String.format(java.util.Locale.ROOT, "%04d", index + 1)}.${encoded.extension}"
                parts += EditablePdf.Part(name, encoded.bytes)
                images += NotePayload.ImageRef(
                    part = name, pageIndex = attachment.pageIndex,
                    x = attachment.pageX, y = attachment.pageY, width = attachment.pageWidth, height = attachment.pageHeight,
                    createdAt = attachment.createdAt,
                )
            }
            val payload = NotePayload(
                installId = settings.installId(),
                sourceNoteId = note.id,
                title = note.title,
                type = note.type.name,
                body = note.body,
                checklistJson = note.checklistJson,
                drawingJson = note.drawingJson,
                paperStyle = note.paperStyle.name,
                colorArgb = note.colorArgb,
                lineColorArgb = note.lineColorArgb,
                lineSpacingDp = note.lineSpacingDp,
                pageFormat = note.pageFormat,
                pageCount = note.pageCount,
                pagesExplicitlyCreated = note.pagesExplicitlyCreated,
                appearance = note.appearance.name,
                createdAt = note.createdAt,
                updatedAt = note.updatedAt,
                tags = repo.observeTagsForNote(note.id).first().map { it.name },
                folderPath = repo.folderPath(note.folderId),
                images = images,
            )
            listOf(EditablePdf.Part(EditablePdf.PART_NOTE, payload.encode().toByteArray())) + parts
        }

    /** Attaches the note to [pdf] in place. A PDF this code cannot update is left as it is. */
    suspend fun embedInto(pdf: File, note: NoteEntity, attachments: List<AttachmentEntity>): Boolean =
        withContext(Dispatchers.IO) {
            val parts = parts(note, attachments)
            try {
                EditablePdf.embed(pdf, parts)
                true
            } catch (error: EditablePdf.UnsupportedPdfException) {
                android.util.Log.w("EditableNotes", "PDF not made editable: ${error.message}")
                false
            }
        }
}
