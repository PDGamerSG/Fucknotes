package com.example.fucknotes.data.db

import androidx.room.TypeConverter
import com.example.fucknotes.data.model.AttachmentType
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.PageAppearance
import com.example.fucknotes.data.model.PaperStyle

/** Enums are stored as their names so the schema stays readable in exported backups. */
class Converters {
    @TypeConverter fun noteTypeToString(v: NoteType): String = v.name

    @TypeConverter fun stringToNoteType(v: String): NoteType =
        runCatching { NoteType.valueOf(v) }.getOrDefault(NoteType.TEXT)

    @TypeConverter fun paperToString(v: PaperStyle): String = v.name

    @TypeConverter fun stringToPaper(v: String): PaperStyle =
        runCatching { PaperStyle.valueOf(v) }.getOrDefault(PaperStyle.PLAIN)

    @TypeConverter fun appearanceToString(v: PageAppearance): String = v.name

    @TypeConverter fun stringToAppearance(v: String): PageAppearance =
        runCatching { PageAppearance.valueOf(v) }.getOrDefault(PageAppearance.FOLLOW)

    @TypeConverter fun attachmentTypeToString(v: AttachmentType): String = v.name

    @TypeConverter fun stringToAttachmentType(v: String): AttachmentType =
        runCatching { AttachmentType.valueOf(v) }.getOrDefault(AttachmentType.IMAGE)
}
