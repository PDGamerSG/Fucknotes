package com.example.fucknotes.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.model.LayoutMode
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.SortOrder
import com.example.fucknotes.ui.common.ToolButton

/** Everything the create menu can start. Kept as one type so the menu and its handler cannot drift. */
enum class CreateAction(val label: String) {
    NEW_NOTEBOOK("New notebook"),
    NEW_CANVAS("New canvas"),
    IMPORT_PDF("Import PDF"),
    IMPORT_SLIDES("Import PowerPoint"),
    ;

    companion object {
        const val MIME_PDF = com.example.fucknotes.media.DocumentType.MIME_PDF
        const val MIME_PPTX = com.example.fucknotes.media.DocumentType.MIME_PPTX
        const val MIME_PPT = com.example.fucknotes.media.DocumentType.MIME_PPT
    }
}

/**
 * The sheet's action row.
 *
 * Only verbs live here — find, arrange, back up, create — because the rail already answers "where
 * am I". On a phone the row also carries the button that brings the rail in, which is why
 * [onOpenRail] is nullable rather than always present.
 */
@Composable
fun SheetTopBar(
    layout: LayoutMode,
    sort: SortOrder,
    onOpenRail: (() -> Unit)?,
    onBackup: () -> Unit,
    onExport: () -> Unit,
    onSearch: () -> Unit,
    onToggleLayout: () -> Unit,
    onSort: (SortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortMenu by remember { mutableStateOf(false) }

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onOpenRail?.let {
            ToolButton(icon = Icons.Outlined.Menu, label = "Show folders", onClick = it)
        }

        Spacer(Modifier.weight(1f))

        ToolButton(icon = Icons.Outlined.CloudUpload, label = "Back up", onClick = onBackup)
        ToolButton(icon = Icons.Outlined.FileDownload, label = "Export notes as PDF", onClick = onExport)
        ToolButton(icon = Icons.Outlined.Search, label = "Search", onClick = onSearch)
        ToolButton(
            icon = if (layout == LayoutMode.GRID) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
            label = if (layout == LayoutMode.GRID) "Show as a list" else "Show as a grid",
            onClick = onToggleLayout,
        )

        Box {
            ToolButton(
                icon = Icons.AutoMirrored.Outlined.Sort,
                label = "Sort notes",
                onClick = { sortMenu = true },
            )
            SortMenu(
                expanded = sortMenu,
                current = sort,
                onDismiss = { sortMenu = false },
                onPick = { sortMenu = false; onSort(it) },
            )
        }


    }
}

/** The create menu, in the order the work usually starts: write, then organise, then bring in. */
@Composable
fun CreateMenu(expanded: Boolean, onDismiss: () -> Unit, onPick: (CreateAction) -> Unit) {
    DropdownMenu(expanded, onDismissRequest = onDismiss, modifier = Modifier.width(240.dp)) {
        CreateAction.entries.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                trailingIcon = {
                    Icon(
                        when (action) {
                            CreateAction.NEW_NOTEBOOK -> Icons.Outlined.MenuBook
                            CreateAction.NEW_CANVAS -> Icons.Outlined.Brush
                            CreateAction.IMPORT_PDF -> Icons.Outlined.PictureAsPdf
                            CreateAction.IMPORT_SLIDES -> Icons.Outlined.Slideshow
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = { onPick(action) },
            )
        }
    }
}

@Composable
private fun SortMenu(
    expanded: Boolean,
    current: SortOrder,
    onDismiss: () -> Unit,
    onPick: (SortOrder) -> Unit,
) {
    val labels = listOf(
        SortOrder.MANUAL to "Manual order",
        SortOrder.UPDATED_DESC to "Last edited",
        SortOrder.UPDATED_ASC to "Least recently edited",
        SortOrder.CREATED_DESC to "Newest first",
        SortOrder.CREATED_ASC to "Oldest first",
        SortOrder.TITLE_ASC to "Title A–Z",
        SortOrder.TITLE_DESC to "Title Z–A",
    )
    DropdownMenu(expanded, onDismissRequest = onDismiss) {
        labels.forEach { (order, label) ->
            DropdownMenuItem(
                text = { Text(label) },
                trailingIcon = { if (order == current) Icon(Icons.Filled.Check, null) },
                onClick = { onPick(order) },
            )
        }
    }
}

/** The note type a create action starts, or null for a picker. */
fun CreateAction.noteType(): NoteType? = when (this) {
    CreateAction.NEW_CANVAS -> NoteType.DRAWING
    CreateAction.NEW_NOTEBOOK, CreateAction.IMPORT_PDF, CreateAction.IMPORT_SLIDES -> null
}
