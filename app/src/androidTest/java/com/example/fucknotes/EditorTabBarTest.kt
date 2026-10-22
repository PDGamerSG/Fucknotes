package com.example.fucknotes

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.fucknotes.ui.editor.tabs.EditorTabBar
import com.example.fucknotes.ui.editor.tabs.OpenTabs
import com.example.fucknotes.ui.theme.FucknotesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The tab bar lists every open note, marks the active one, and closes or switches on a tap. */
@RunWith(AndroidJUnit4::class)
class EditorTabBarTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun showsEveryOpenNoteAndRoutesSelectCloseAndOpenAnother() {
        val selected = mutableListOf<Long>()
        val closed = mutableListOf<Long>()
        var openAnother = 0
        val tabs = listOf(OpenTabs.Tab(1, "Lecture"), OpenTabs.Tab(2, ""), OpenTabs.Tab(3, "Scratch"))
        compose.setContent {
            FucknotesTheme {
                EditorTabBar(
                    tabs = tabs, activeNoteId = 2,
                    onSelect = { selected += it }, onClose = { closed += it },
                    onOpenAnother = { openAnother++ },
                )
            }
        }
        compose.onNodeWithContentDescription("Lecture tab").assertIsDisplayed().assertIsNotSelected()
        compose.onNodeWithContentDescription("Untitled tab").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithContentDescription("Scratch tab").assertIsDisplayed().assertIsNotSelected()

        compose.onNodeWithContentDescription("Scratch tab").performClick()
        compose.onNodeWithContentDescription("Close Lecture").performClick()
        compose.onNodeWithContentDescription("Open another note").performClick()
        compose.runOnIdle {
            assertEquals(listOf(3L), selected)
            assertEquals(listOf(1L), closed)
            assertEquals(1, openAnother)
        }
    }
}
