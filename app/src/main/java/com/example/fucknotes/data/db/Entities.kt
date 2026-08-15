package com.example.fucknotes.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.fucknotes.data.model.AttachmentType
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.PaperStyle

@Entity(
    tableName = "notes",
    indices = [Index("folderId"), Index("updatedAt"), Index("deletedAt")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    /** Plain-text body for TEXT notes; also used as the search index for other types. */
    val body: String = "",
    val type: NoteType = NoteType.TEXT,
    /** JSON `List<ChecklistItem>` when [type] is CHECKLIST. */
    val checklistJson: String = "",
    /** JSON `Drawing` when [type] is DRAWING, or when a text note has ink layered on it. */
    val drawingJson: String = "",
    val folderId: Long? = null,
    /** ARGB tint of the note card; 0 means "follow the theme". */
    val colorArgb: Int = 0,
    val paperStyle: PaperStyle = PaperStyle.PLAIN,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val locked: Boolean = false,
    /** Epoch millis the note was moved to trash, or null while it is live. */
    val deletedAt: Long? = null,
    /** Epoch millis for a scheduled reminder, or null. */
    val reminderAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "folders",
    indices = [Index("parentId")],
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Null for a top-level notebook; otherwise the parent folder id. */
    val parentId: Long? = null,
    val colorArgb: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int = 0,
)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    indices = [Index("tagId")],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class NoteTagCrossRef(
    val noteId: Long,
    val tagId: Long,
)

@Entity(
    tableName = "attachments",
    indices = [Index("noteId")],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    /** Absolute path of the file inside the app's private media directory. */
    @ColumnInfo(name = "path") val path: String,
    val type: AttachmentType,
    /** Recording length in millis for AUDIO; 0 for images. */
    val durationMs: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
