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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
    /** A note was dropped between two neighbours after a hold-to-drag. */
    onMoveNote: (noteId: Long, beforeId: Long?, afterId: Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 26.dp, end = 26.dp, top = 4.dp, bottom = 14.dp)) {
            Text(
                state.title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.brand.heading,
            )
            if (!state.loading && state.notes.isNotEmpty()) {
                Text(
                    "${state.notes.size} ${if (state.notes.size == 1) "note" else "notes"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        when {
            state.loading -> Spacer(Modifier.fillMaxSize())

            state.notes.isEmpty() -> ShelfEmptyState(
                scope = state.scope,
                onNewNotebook = onNewNotebook,
                modifier = Modifier.fillMaxSize(),
            )

            state.layout == LayoutMode.GRID -> {
                val gridState = rememberLazyGridState()
                val scope = rememberCoroutineScope()
                val move by rememberUpdatedState(onMoveNote)
                val reorder = remember(gridState) {
                    NoteReorderState(
                        visibleItems = {
                            gridState.layoutInfo.visibleItemsInfo.map {
                                ShelfItemBox(
                                    key = it.key as Long,
                                    left = it.offset.x.toFloat(), top = it.offset.y.toFloat(),
                                    width = it.size.width.toFloat(), height = it.size.height.toFloat(),
                                )
                            }
                        },
                        viewportHeight = { gridState.layoutInfo.viewportSize.height.toFloat() },
                        scrollBy = { gridState.scrollBy(it) },
                        scope = scope,
                        onMove = { id, before, after -> move(id, before, after) },
                    )
                }
                val ordered = reorder.arrange(state.notes) { it.id }
                LaunchedEffect(state.notes) { reorder.settle(state.notes.map { it.id }) }
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(156.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 104.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(ordered, key = { it.id }) { note ->
                        NoteTile(
                            note = note,
                            preview = previewOf(note),
                            selected = note.id in state.selection,
                            selectionMode = state.inSelectionMode,
                            hasImages = (state.mediaByNote[note.id]?.images ?: 0) > 0,
                            hasAudio = (state.mediaByNote[note.id]?.audio ?: 0) > 0,
                            firstPageImagePath = state.firstPageImageByNote[note.id],
                            darkReader = state.settings.darkReader,
                            interaction = Modifier.holdToDrag(
                                state = reorder,
                                id = note.id,
                                currentIds = { ordered.map { it.id } },
                                boxOf = {
                                    gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == note.id }?.let {
                                        ShelfItemBox(note.id, it.offset.x.toFloat(), it.offset.y.toFloat(),
                                            it.size.width.toFloat(), it.size.height.toFloat())
                                    }
                                },
                                onClick = {
                                    if (state.inSelectionMode) onToggleSelect(note.id) else onOpenNote(note.id)
                                },
                                onLongClick = { onToggleSelect(note.id) },
                            ),
                            // The lifted note is placed by the finger; the slot animation would
                            // pull it away from the finger each time it swaps places.
                            modifier = Modifier
                                .then(if (reorder.draggingId == note.id) Modifier else Modifier.animateItem())
                                .reorderItem(reorder, note.id),
                        )
                    }
                }
            }

            else -> {
                val listState = rememberLazyListState()
                val scope = rememberCoroutineScope()
                val move by rememberUpdatedState(onMoveNote)
                val reorder = remember(listState) {
                    NoteReorderState(
                        visibleItems = {
                            val width = listState.layoutInfo.viewportSize.width.toFloat()
                            listState.layoutInfo.visibleItemsInfo.map {
                                ShelfItemBox(it.key as Long, 0f, it.offset.toFloat(), width, it.size.toFloat())
                            }
                        },
                        viewportHeight = { listState.layoutInfo.viewportSize.height.toFloat() },
                        scrollBy = { listState.scrollBy(it) },
                        scope = scope,
                        onMove = { id, before, after -> move(id, before, after) },
                    )
                }
                val ordered = reorder.arrange(state.notes) { it.id }
                LaunchedEffect(state.notes) { reorder.settle(state.notes.map { it.id }) }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ordered, key = { it.id }) { note ->
                        NoteRow(
                            note = note,
                            preview = previewOf(note),
                            firstPageImagePath = state.firstPageImageByNote[note.id],
                            darkReader = state.settings.darkReader,
                            selected = note.id in state.selection,
                            interaction = Modifier.holdToDrag(
                                state = reorder,
                                id = note.id,
                                currentIds = { ordered.map { it.id } },
                                boxOf = {
                                    listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == note.id }?.let {
                                        ShelfItemBox(note.id, 0f, it.offset.toFloat(),
                                            listState.layoutInfo.viewportSize.width.toFloat(), it.size.toFloat())
                                    }
                                },
                                onClick = {
                                    if (state.inSelectionMode) onToggleSelect(note.id) else onOpenNote(note.id)
                                },
                                onLongClick = { onToggleSelect(note.id) },
                            ),
                            modifier = Modifier
                                .then(if (reorder.draggingId == note.id) Modifier else Modifier.animateItem())
                                .reorderItem(reorder, note.id),
                        )
                    }
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
