package com.example.fucknotes.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything needed to rebuild a note as it is on the shelf: ink, paper, pages, images, tags.
 *
 * This rides inside the PDF the app exports, as an embedded file, so the PDF is both a document
 * any reader shows and the note itself. Pulling the PDF back in — from Drive, a share, a file
 * manager — restores an editable note rather than a flat picture of one: strokes can be erased,
 * paper changed, pages added.
 */
@Serializable
data class NotePayload(
    val format: Int = FORMAT_VERSION,
    val app: String = APP,
    /** Random id of the install that wrote the file, paired with [sourceNoteId]. */
    val installId: String,
    val sourceNoteId: Long,
    val title: String,
    val type: String,
    val body: String = "",
    val checklistJson: String = "",
    val drawingJson: String = "",
    val paperStyle: String,
    val colorArgb: Int = 0,
    val lineColorArgb: Int = 0,
    val lineSpacingDp: Int = 0,
    val pageFormat: String = "A4",
    val pageCount: Int = 1,
    val pagesExplicitlyCreated: Boolean = true,
    val appearance: String = "FOLLOW",
    val createdAt: Long,
    val updatedAt: Long,
    val tags: List<String> = emptyList(),
    /** Folder names from the top, so a restore elsewhere can file the note the same way. */
    val folderPath: List<String> = emptyList(),
    val images: List<ImageRef> = emptyList(),
) {
    /** One page image, stored as its own embedded part next to the note data. */
    @Serializable
    data class ImageRef(
        val part: String,
        val pageIndex: Int? = null,
        val x: Float = 0f,
        val y: Float = 0f,
        val width: Float = 0f,
        val height: Float = 0f,
        val createdAt: Long = 0,
    )

    fun encode(): String = json.encodeToString(this)

    companion object {
        const val FORMAT_VERSION = 1
        const val APP = "fucknotes"
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        /** Null when the text is not a note payload this build understands. */
        fun decode(text: String): NotePayload? = runCatching { json.decodeFromString<NotePayload>(text) }
            .getOrNull()?.takeIf { it.app == APP && it.format in 1..FORMAT_VERSION }
    }
}
