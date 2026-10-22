package com.example.fucknotes.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.db.NoteEntity
import com.example.fucknotes.data.model.LayoutMode
import com.example.fucknotes.ui.theme.brand

/**
 * The sheet: the scope's name, then its notes.
 *
 * Grid and list are the same content at two densities — the grid shows the pages, the list shows
 * the names — so both take the same tap and long-press contract from the caller.
 */
@Composable
fun HomeShelf(
    state: HomeUiState,
    previewOf: (NoteEntity) -> String,
    onOpenNote: (Long) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onNewNotebook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Text(
            state.title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.brand.heading,
            modifier = Modifier.padding(start = 26.dp, end = 26.dp, top = 4.dp, bottom = 14.dp),
        )

        when {
            state.loading -> Spacer(Modifier.fillMaxSize())

            state.notes.isEmpty() -> ShelfEmptyState(
                scope = state.scope,
                onNewNotebook = onNewNotebook,
                modifier = Modifier.fillMaxSize(),
            )

            state.layout == LayoutMode.GRID -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.notes, key = { it.id }) { note ->
                    NoteTile(
                        note = note,
                        preview = previewOf(note),
                        selected = note.id in state.selection,
                        selectionMode = state.inSelectionMode,
                        hasImages = (state.mediaByNote[note.id]?.images ?: 0) > 0,
                        hasAudio = (state.mediaByNote[note.id]?.audio ?: 0) > 0,
                        firstPageImagePath = state.firstPageImageByNote[note.id],
                        darkReader = state.settings.darkReader,
                        onClick = {
                            if (state.inSelectionMode) onToggleSelect(note.id) else onOpenNote(note.id)
                        },
                        onLongClick = { onToggleSelect(note.id) },
                    )
                }
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.notes, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        preview = previewOf(note),
                        firstPageImagePath = state.firstPageImageByNote[note.id],
                        darkReader = state.settings.darkReader,
                        selected = note.id in state.selection,
                        onClick = {
                            if (state.inSelectionMode) onToggleSelect(note.id) else onOpenNote(note.id)
                        },
                        onLongClick = { onToggleSelect(note.id) },
                    )
                }
            }
        }
    }
}

/** An empty shelf is an invitation, so it carries the action rather than describing the emptiness. */
@Composable
private fun ShelfEmptyState(scope: Scope, onNewNotebook: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.7f),
        ) {
            Icon(
                Icons.Outlined.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(44.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                when (scope) {
                    Scope.Archive -> "Nothing archived"
                    Scope.Unfiled -> "Everything is filed"
                    else -> "No notes here yet"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.brand.heading,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Start a notebook and pick the paper you want to write on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNewNotebook) { Text("New notebook") }
        }
    }
}
