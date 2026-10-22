package com.example.fucknotes.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    /**
     * Live notes, newest activity first, pinned notes always on top.
     *
     * The `:folderId` filter is skipped when [filterFolder] is false so that a single query can
     * back both the "all notes" screen and a notebook screen.
     */
    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAt IS NULL
          AND archived = :archived
          AND (:filterFolder = 0 OR folderId IS :folderId)
        ORDER BY pinned DESC,
          CASE WHEN :sort = 'UPDATED_DESC' THEN updatedAt END DESC,
          CASE WHEN :sort = 'UPDATED_ASC'  THEN updatedAt END ASC,
          CASE WHEN :sort = 'CREATED_DESC' THEN createdAt END DESC,
          CASE WHEN :sort = 'CREATED_ASC'  THEN createdAt END ASC,
          CASE WHEN :sort = 'TITLE_ASC'    THEN title END ASC,
          CASE WHEN :sort = 'TITLE_DESC'   THEN title END DESC,
          updatedAt DESC
        """
    )
    fun observeNotes(
        archived: Boolean,
        filterFolder: Boolean,
        folderId: Long?,
        sort: String,
    ): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeNote(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNote(id: Long): NoteEntity?

    @Query(
        """
        SELECT * FROM notes
        WHERE deletedAt IS NULL
          AND (title LIKE '%' || :query || '%' OR body LIKE '%' || :query || '%')
        ORDER BY pinned DESC, updatedAt DESC
        """
    )
    fun search(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND reminderAt IS NOT NULL")
    suspend fun notesWithReminders(): List<NoteEntity>

    @Query("SELECT * FROM notes")
    suspend fun allNotes(): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes WHERE deletedAt IS NULL AND archived = 0 AND folderId IS :folderId")
    fun countInFolder(folderId: Long?): Flow<Int>

    /**
     * Every folder's tally in one query.
     *
     * The rail shows a count on each row, and asking per row would open one query per folder on a
     * screen that already has a live note list running.
     */
    @Query(
        """
        SELECT folderId AS folderId, COUNT(*) AS count FROM notes
        WHERE deletedAt IS NULL AND archived = 0
        GROUP BY folderId
        """
    )
    fun observeFolderCounts(): Flow<List<FolderCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Upsert
    suspend fun upsertAll(notes: List<NoteEntity>)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("UPDATE notes SET deletedAt = :now WHERE id IN (:ids)")
    suspend fun moveToTrash(ids: List<Long>, now: Long)

    @Query("UPDATE notes SET deletedAt = NULL, updatedAt = :now WHERE id IN (:ids)")
    suspend fun restore(ids: List<Long>, now: Long)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteForever(ids: List<Long>)

    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    /** Trash auto-purge, matching the source app's 30-day retention. */
    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeTrashOlderThan(cutoff: Long)

    @Query("UPDATE notes SET pinned = :pinned, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setPinned(ids: List<Long>, pinned: Boolean, now: Long)

    @Query("UPDATE notes SET archived = :archived, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setArchived(ids: List<Long>, archived: Boolean, now: Long)

    @Query("UPDATE notes SET locked = :locked, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setLocked(ids: List<Long>, locked: Boolean, now: Long)

    /** Persists notebook sheet creation without waiting for the editor's debounced full save. */
    @Query(
        "UPDATE notes SET pageCount = :pageCount, pagesExplicitlyCreated = :explicit, " +
            "updatedAt = :now WHERE id = :id"
    )
    suspend fun setPageState(id: Long, pageCount: Int, explicit: Boolean, now: Long)

    @Query("UPDATE notes SET locked = 0, updatedAt = :now WHERE locked = 1")
    suspend fun unlockAll(now: Long)

    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :now WHERE id IN (:ids)")
    suspend fun moveToFolder(ids: List<Long>, folderId: Long?, now: Long)

    @Query("UPDATE notes SET colorArgb = :color, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setColor(ids: List<Long>, color: Int, now: Long)

    @Query("UPDATE notes SET reminderAt = :at, updatedAt = :now WHERE id = :id")
    suspend fun setReminder(id: Long, at: Long?, now: Long)

    @Query("UPDATE notes SET folderId = NULL WHERE folderId IN (:folderIds)")
    suspend fun detachFromFolders(folderIds: List<Long>)
}

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders")
    suspend fun allFolders(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolder(id: Long): FolderEntity?

    @Query("SELECT * FROM folders WHERE parentId IS :parentId ORDER BY name COLLATE NOCASE ASC")
    suspend fun children(parentId: Long?): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Upsert
    suspend fun upsertAll(folders: List<FolderEntity>)

    @Query("DELETE FROM folders WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<Long>)
}

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags")
    suspend fun allTags(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Upsert
    suspend fun upsertAll(tags: List<TagEntity>)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)

    @Query(
        """
        SELECT t.* FROM tags t
        INNER JOIN note_tags nt ON nt.tagId = t.id
        WHERE nt.noteId = :noteId
        ORDER BY t.name COLLATE NOCASE ASC
        """
    )
    fun observeTagsForNote(noteId: Long): Flow<List<TagEntity>>

    @Query(
        """
        SELECT n.* FROM notes n
        INNER JOIN note_tags nt ON nt.noteId = n.id
        WHERE nt.tagId = :tagId AND n.deletedAt IS NULL
        ORDER BY n.pinned DESC, n.updatedAt DESC
        """
    )
    fun observeNotesForTag(tagId: Long): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(ref: NoteTagCrossRef)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId AND tagId = :tagId")
    suspend fun unlink(noteId: Long, tagId: Long)

    @Query("SELECT * FROM note_tags")
    suspend fun allCrossRefs(): List<NoteTagCrossRef>

    @Upsert
    suspend fun upsertCrossRefs(refs: List<NoteTagCrossRef>)

    @Query(
        """
        SELECT nt.noteId AS noteId, t.name AS name
        FROM note_tags nt
        INNER JOIN tags t ON t.id = nt.tagId
        ORDER BY t.name COLLATE NOCASE ASC
        """
    )
    fun observeAllLabels(): Flow<List<NoteTagLabel>>
}

/** Flat tag label attached to a note id, used to decorate list cards without an N+1 query. */
data class NoteTagLabel(val noteId: Long, val name: String)

/** Per-note attachment tally so a card can show camera / mic badges. */
data class NoteMediaFlags(val noteId: Long, val images: Int, val audio: Int)

/** How many live notes sit in one folder. A null id is the unfiled bucket. */
data class FolderCount(val folderId: Long?, val count: Int)

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt ASC")
    fun observeForNote(noteId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE noteId = :noteId ORDER BY createdAt ASC")
    suspend fun forNote(noteId: Long): List<AttachmentEntity>

    /** First-page images are used as the shelf preview for imported PDFs and placed page images. */
    @Query("SELECT * FROM attachments WHERE type = 'IMAGE' AND pageIndex = 0 ORDER BY createdAt ASC")
    fun observeFirstPageImages(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments")
    suspend fun allAttachments(): List<AttachmentEntity>

    @Query(
        """
        SELECT noteId AS noteId,
               SUM(CASE WHEN type = 'IMAGE' THEN 1 ELSE 0 END) AS images,
               SUM(CASE WHEN type = 'AUDIO' THEN 1 ELSE 0 END) AS audio
        FROM attachments
        GROUP BY noteId
        """
    )
    fun observeMediaFlags(): Flow<List<NoteMediaFlags>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: AttachmentEntity): Long

    @Update
    suspend fun update(attachment: AttachmentEntity)

    @Upsert
    suspend fun upsertAll(attachments: List<AttachmentEntity>)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: Long)

    @Transaction
    @Query("DELETE FROM attachments WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)
}
