package com.example.fucknotes

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.fucknotes.data.db.AppDatabase
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.NotebookDraft
import com.example.fucknotes.data.model.PageAppearance
import com.example.fucknotes.data.repo.NotesRepository
import com.example.fucknotes.ui.home.NewNotebookDialog
import com.example.fucknotes.ui.theme.FucknotesTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A notebook is made light, a note keeps the mode it is switched to, and old rows come through
 * the schema change still following the app-wide setting.
 */
@RunWith(AndroidJUnit4::class)
class NotebookAppearanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val migrations = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test fun aNewNotebookIsLightAndTheSheetOffersNoDarkPage() {
        var created: NotebookDraft? = null
        compose.setContent {
            // Even with the app in dark mode: dark is a switch inside the note, not a kind of paper.
            FucknotesTheme(darkReader = true) {
                NewNotebookDialog(initialTitle = "Day", onDismiss = {}, onCreate = { created = it })
            }
        }
        compose.onNodeWithText("Mode").assertDoesNotExist()
        compose.onNodeWithContentDescription("Light mode").assertDoesNotExist()
        compose.onNodeWithContentDescription("Dark mode").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pitch black mode").assertDoesNotExist()
        compose.onNodeWithText("Paper colour").assertIsDisplayed()
        compose.onNodeWithText("Create").performClick()
        assertEquals(PageAppearance.LIGHT, created?.appearance)
    }

    @Test fun theChoiceIsStoredWithTheNoteAndCopiedWithIt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val repo = NotesRepository(db)
            val dark = repo.createNote(NoteType.NOTEBOOK, null, "dark", appearance = PageAppearance.DARK)
            val plain = repo.createNote(NoteType.NOTEBOOK, null, "plain")
            assertEquals(PageAppearance.DARK, repo.getNote(dark)?.appearance)
            assertEquals(PageAppearance.FOLLOW, repo.getNote(plain)?.appearance)
            val copy = requireNotNull(repo.duplicate(dark))
            assertEquals(PageAppearance.DARK, repo.getNote(copy)?.appearance)
        } finally {
            db.close()
        }
    }

    @Test fun existingNotesFollowTheAppAfterTheMigration() {
        val name = "appearance-migration-test"
        migrations.createDatabase(name, 6).apply {
            execSQL(
                "INSERT INTO notes (title, body, type, checklistJson, drawingJson, colorArgb, paperStyle, " +
                    "lineColorArgb, lineSpacingDp, pageFormat, pageCount, pagesExplicitlyCreated, pinned, archived, " +
                    "locked, createdAt, updatedAt, position) VALUES ('old', '', 'NOTEBOOK', '', '', 0, 'LINED', " +
                    "0, 0, 'A4', 1, 1, 0, 0, 0, 1, 1, 1)"
            )
            close()
        }
        val db = migrations.runMigrationsAndValidate(name, 7, true, AppDatabase.migrationForTests(6, 7))
        db.query("SELECT appearance FROM notes WHERE title = 'old'").use { c ->
            c.moveToFirst()
            assertEquals("FOLLOW", c.getString(0))
        }
        db.close()
    }
}
