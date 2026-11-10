package com.example.fucknotes.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL

/** Runs the same upgrade statements with either Room's legacy or native SQLite connection. */
private abstract class SqlMigration(from: Int, to: Int) : Migration(from, to) {
    final override fun migrate(db: SupportSQLiteDatabase) = migrateStatements(db::execSQL)

    final override fun migrate(connection: SQLiteConnection) = migrateStatements(connection::execSQL)

    abstract fun migrateStatements(execute: (String) -> Unit)
}

@Database(
    entities = [
        NoteEntity::class,
        FolderEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        AttachmentEntity::class,
        DriveFileEntity::class,
        DriveFolderEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun tagDao(): TagDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun driveDao(): DriveDao

    companion object {
        private const val NAME = "fucknotes.db"

        /**
         * Paper became a property of the note rather than of the app: a page carries the rule
         * colour, the rule spacing and the sheet size it was created with, so two notebooks can
         * look different. Existing rows take zeroes, which every reader treats as "use the
         * default", and A4 — which is what they were drawn on.
         */
        private val MIGRATION_1_2 = object : SqlMigration(1, 2) {
            override fun migrateStatements(execute: (String) -> Unit) {
                execute("ALTER TABLE notes ADD COLUMN lineColorArgb INTEGER NOT NULL DEFAULT 0")
                execute("ALTER TABLE notes ADD COLUMN lineSpacingDp INTEGER NOT NULL DEFAULT 0")
                execute("ALTER TABLE notes ADD COLUMN pageFormat TEXT NOT NULL DEFAULT 'A4'")
            }
        }

        private val MIGRATION_2_3 = object : SqlMigration(2, 3) {
            override fun migrateStatements(execute: (String) -> Unit) {
                execute("ALTER TABLE notes ADD COLUMN pageCount INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_3_4 = object : SqlMigration(3, 4) {
            override fun migrateStatements(execute: (String) -> Unit) {
                execute("ALTER TABLE notes ADD COLUMN pagesExplicitlyCreated INTEGER NOT NULL DEFAULT 0")
                // A recorded count can include real writing on later pages. Preserve it when
                // adopting explicit page creation instead of making those pages inaccessible.
                execute("UPDATE notes SET pageCount = MAX(1, MIN(pageCount, 500))")
                execute("UPDATE notes SET pagesExplicitlyCreated = 1 WHERE pageCount > 1")
            }
        }

        @Volatile private var instance: AppDatabase? = null

        private val MIGRATION_4_5 = object : SqlMigration(4, 5) {
            override fun migrateStatements(execute: (String) -> Unit) {
                execute("ALTER TABLE attachments ADD COLUMN pageIndex INTEGER")
                execute("ALTER TABLE attachments ADD COLUMN pageX REAL NOT NULL DEFAULT 0")
                execute("ALTER TABLE attachments ADD COLUMN pageY REAL NOT NULL DEFAULT 0")
                execute("ALTER TABLE attachments ADD COLUMN pageWidth REAL NOT NULL DEFAULT 0")
                execute("ALTER TABLE attachments ADD COLUMN pageHeight REAL NOT NULL DEFAULT 0")
                // These features were removed; existing notes remain available and normally sorted.
                execute("UPDATE notes SET locked = 0, pinned = 0, reminderAt = NULL")
            }
        }

        /**
         * Manual shelf order. Every existing note takes its creation time as its position, so
         * the shelf opens in creation order and nothing moves until the user drags it.
         */
        private val MIGRATION_5_6 = object : SqlMigration(5, 6) {
            override fun migrateStatements(execute: (String) -> Unit) {
                execute("ALTER TABLE notes ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
                execute("UPDATE notes SET position = createdAt")
            }
        }

        /**
         * Light or dark pages per note. Existing notes follow the app-wide setting, which is what
         * they did before, so nothing changes on screen until a note chooses for itself.
         */
        private val MIGRATION_6_7 = object : SqlMigration(6, 7) {
            override fun migrateStatements(execute: (String) -> Unit) {
                execute("ALTER TABLE notes ADD COLUMN appearance TEXT NOT NULL DEFAULT 'FOLLOW'")
            }
        }

        /** Google Drive sync bookkeeping: which Drive file and folder stand for which note and folder. */
        private val MIGRATION_7_8 = object : SqlMigration(7, 8) {
            override fun migrateStatements(execute: (String) -> Unit) {
                execute(
                    "CREATE TABLE IF NOT EXISTS `drive_files` (`noteId` INTEGER NOT NULL, `fileId` TEXT NOT NULL, " +
                        "`folderKey` TEXT NOT NULL, `name` TEXT NOT NULL, `syncedUpdatedAt` INTEGER NOT NULL, " +
                        "`syncedAt` INTEGER NOT NULL, PRIMARY KEY(`noteId`))"
                )
                execute(
                    "CREATE TABLE IF NOT EXISTS `drive_folders` (`folderKey` TEXT NOT NULL, `fileId` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, `parentKey` TEXT, PRIMARY KEY(`folderKey`))"
                )
            }
        }

        /** The migration between two adjacent versions, for the instrumentation tests. */
        fun migrationForTests(from: Int, to: Int): Migration =
            listOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .first { it.startVersion == from && it.endVersion == to }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: builder(context).build().also { instance = it }
        }

        /** Shared database configuration for the app and isolated database instances. */
        fun builder(context: Context, name: String = NAME): Builder<AppDatabase> =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                name,
            )
                // Native statements read large ink strings directly, without CursorWindow's
                // ~2 MB row limit. The existing SQLite file and note encoding stay compatible.
                .setDriver(BundledSQLiteDriver())
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .addMigrations(MIGRATION_5_6)
                .addMigrations(MIGRATION_6_7)
                .addMigrations(MIGRATION_7_8)
                // Foreign keys drive the note -> tag/attachment cascades.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
    }
}
