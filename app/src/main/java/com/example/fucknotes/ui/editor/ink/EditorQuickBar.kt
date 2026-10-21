package com.example.fucknotes.ui.editor.ink

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fucknotes.ui.common.FloatingPanel
import com.example.fucknotes.ui.common.ToolButton
import com.example.fucknotes.ui.common.ToolDivider

/**
 * The left-hand bar: what you do to the note, rather than to the page.
 *
 * Leaving, moving between pages and undoing are the three things reached for mid-sentence, so they
 * sit under the hand that is not holding the pen and never move as the note's mode changes.
 */
@Composable
fun EditorQuickBar(
    canUndo: Boolean,
    canRedo: Boolean,
    notebook: Boolean,
    onBack: () -> Unit,
    onPages: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onAddPage: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingPanel(modifier) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                label = "Back to notes",
                onClick = onBack,
            )
            if (notebook) {
                ToolButton(icon = Icons.Outlined.GridView, label = "All pages", onClick = onPages)
            }

            ToolDivider()

            ToolButton(
                icon = Icons.AutoMirrored.Outlined.Undo,
                label = "Undo",
                enabled = canUndo,
                onClick = onUndo,
            )
            ToolButton(
                icon = Icons.AutoMirrored.Outlined.Redo,
                label = "Redo",
                enabled = canRedo,
                onClick = onRedo,
            )

            ToolDivider()

            if (notebook) {
                ToolButton(icon = Icons.Outlined.NoteAdd, label = "Add a new page", onClick = onAddPage)
            }
            ToolButton(icon = Icons.Filled.MoreHoriz, label = "More", onClick = onMore)
        }
    }
}
