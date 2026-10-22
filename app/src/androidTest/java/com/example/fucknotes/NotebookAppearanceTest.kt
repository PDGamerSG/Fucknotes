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
 * A notebook picks light or dark pages when it is made, keeps the choice with the note, and old
 * rows come through the schema change still following the app-wide setting.
 */
@RunWith(AndroidJUnit4::class)
class NotebookAppearanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val migrations = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test fun theSheetStartsFromTheAppSettingAndTheChoiceReachesTheDraft() {
        var created: NotebookDraft? = null
        compose.setContent {
            FucknotesTheme {
                NewNotebookDialog(initialTitle = "Night", initialDark = false, onDismiss = {}, onCreate = { created = it })
            }
        }
        compose.onNodeWithContentDescription("Light mode").assertIsDisplayed()
        compose.onNodeWithContentDescription("Dark mode").performClick()
        // The paper tone has no say on a night page, so its row steps aside.
        compose.onNodeWithText("Paper colour").assertDoesNotExist()
        compose.onNodeWithText("Create").performClick()
        assertEquals(PageAppearance.DARK, created?.appearance)
    }

    @Test fun theSheetPreselectsDarkWhenTheAppIsDark() {
        var created: NotebookDraft? = null
        compose.setContent {
            FucknotesTheme(darkReader = true) {
                NewNotebookDialog(initialTitle = "Day", initialDark = true, onDismiss = {}, onCreate = { created = it })
            }
        }
        compose.onNodeWithContentDescription("Light mode").performClick()
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
