package com.example.fucknotes.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NoteEntity::class,
        FolderEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        AttachmentEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun tagDao(): TagDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        private const val NAME = "fucknotes.db"

        /**
         * Paper became a property of the note rather than of the app: a page carries the rule
         * colour, the rule spacing and the sheet size it was created with, so two notebooks can
         * look different. Existing rows take zeroes, which every reader treats as "use the
         * default", and A4 — which is what they were drawn on.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN lineColorArgb INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN lineSpacingDp INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN pageFormat TEXT NOT NULL DEFAULT 'A4'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN pageCount INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN pagesExplicitlyCreated INTEGER NOT NULL DEFAULT 0")
                // A recorded count can include real writing on later pages. Preserve it when
                // adopting explicit page creation instead of making those pages inaccessible.
                db.execSQL("UPDATE notes SET pageCount = MAX(1, MIN(pageCount, 500))")
                db.execSQL("UPDATE notes SET pagesExplicitlyCreated = 1 WHERE pageCount > 1")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attachments ADD COLUMN pageIndex INTEGER")
                db.execSQL("ALTER TABLE attachments ADD COLUMN pageX REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE attachments ADD COLUMN pageY REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE attachments ADD COLUMN pageWidth REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE attachments ADD COLUMN pageHeight REAL NOT NULL DEFAULT 0")
                // These features were removed; existing notes remain available and normally sorted.
                db.execSQL("UPDATE notes SET locked = 0, pinned = 0, reminderAt = NULL")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                NAME,
            )
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                // Foreign keys drive the note -> tag/attachment cascades.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
                .also { instance = it }
        }
    }
}
