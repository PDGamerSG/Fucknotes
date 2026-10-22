package com.example.fucknotes.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.fucknotes.data.model.AttachmentType
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.PageAppearance
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
    /** ARGB of the rules, grid or dots; 0 means "use the default rule tone". */
    val lineColorArgb: Int = 0,
    /** Distance between rules in points; 0 means "use the default spacing". */
    val lineSpacingDp: Int = 0,
    /** Sheet size and direction, encoded by `PageSpec.encode()` — "A4", "A5_L" and so on. */
    val pageFormat: String = "A4",
    /** Number of notebook sheets explicitly created, including empty sheets. */
    val pageCount: Int = 1,
    /** True once the user has used the page controls; protects the count from legacy stroke bounds. */
    val pagesExplicitlyCreated: Boolean = false,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val locked: Boolean = false,
    /** Epoch millis the note was moved to trash, or null while it is live. */
    val deletedAt: Long? = null,
    /** Epoch millis for a scheduled reminder, or null. */
    val reminderAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * Where the note sits on the shelf in manual order; lower comes first.
     *
     * Seeded from [createdAt], so a note starts where it was made, and only a drag rewrites it.
     * Gaps are kept wide so a move usually lands between its neighbours without renumbering.
     */
    val position: Long = 0,
    /** Light or night colours for this note's pages; FOLLOW takes the app-wide setting. */
    val appearance: PageAppearance = PageAppearance.FOLLOW,
)

/** Which Drive file carries a note, and the state of that note when it was last uploaded. */
@Entity(tableName = "drive_files")
data class DriveFileEntity(
    @PrimaryKey val noteId: Long,
    val fileId: String,
    /** The sync folder the file sits in: "unfiled", "archive" or "folder:<id>". */
    val folderKey: String,
    /** The file name used on Drive, so a renamed note is renamed there too. */
    val name: String,
    /** The note's updatedAt at upload time; a newer local edit means an upload is due. */
    val syncedUpdatedAt: Long,
    val syncedAt: Long,
)

/** Which Drive folder stands for a sync folder key ("root", "unfiled", "archive", "folder:<id>"). */
@Entity(tableName = "drive_folders")
data class DriveFolderEntity(
    @PrimaryKey val folderKey: String,
    val fileId: String,
    val name: String,
    val parentKey: String?,
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
    /** Null for a standalone attachment; otherwise an image placed underneath the page's ink. */
    val pageIndex: Int? = null,
    val pageX: Float = 0f,
    val pageY: Float = 0f,
    val pageWidth: Float = 0f,
    val pageHeight: Float = 0f,
)
