package com.example.fucknotes.ui.trash

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.fucknotes.ServiceLocator
import com.example.fucknotes.data.db.NoteEntity
import com.example.fucknotes.ui.components.ConfirmDialog
import com.example.fucknotes.ui.components.EmptyState
import com.example.fucknotes.ui.components.NoteCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrashViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.notes(app)

    val notes: StateFlow<List<NoteEntity>> = repo.observeTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(id: Long) = viewModelScope.launch { repo.restore(listOf(id)) }

    fun deleteForever(id: Long) = viewModelScope.launch { repo.deleteForever(listOf(id)) }

    fun emptyTrash() = viewModelScope.launch { repo.emptyTrash() }

    fun previewOf(note: NoteEntity): String = repo.plainTextOf(note).take(240)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(viewModel: TrashViewModel, onBack: () -> Unit) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<NoteEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trash") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (notes.isNotEmpty()) {
                        IconButton(onClick = { confirmEmpty = true }) {
                            Icon(Icons.Outlined.DeleteForever, contentDescription = "Empty trash")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (notes.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.DeleteOutline,
                    title = "Trash bin is empty",
                    subtitle = "Deleted notes stay here for 30 days.",
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        Text(
                            "Notes here are removed for good 30 days after deletion.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    items(notes, key = { it.id }) { note ->
                        Box {
                            NoteCard(
                                note = note,
                                preview = viewModel.previewOf(note),
                                tagNames = emptyList(),
                                selected = false,
                                selectionMode = false,
                                hasImages = false,
                                hasAudio = false,
                                onClick = { viewModel.restore(note.id) },
                                onLongClick = { confirmDelete = note },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            androidx.compose.foundation.layout.Row(
                                Modifier.align(Alignment.BottomEnd).padding(4.dp)
                            ) {
                                IconButton(onClick = { viewModel.restore(note.id) }) {
                                    Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "Restore")
                                }
                                IconButton(onClick = { confirmDelete = note }) {
                                    Icon(Icons.Outlined.DeleteForever, contentDescription = "Delete forever")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = "Empty trash?",
            message = "Every note in the trash will be removed permanently.",
            confirmText = "Empty trash",
            destructive = true,
            onDismiss = { confirmEmpty = false },
            onConfirm = {
                confirmEmpty = false
                viewModel.emptyTrash()
            },
        )
    }

    confirmDelete?.let { note ->
        ConfirmDialog(
            title = "Delete permanently?",
            message = "\"${note.title.ifBlank { "Untitled" }}\" cannot be recovered afterwards.",
            confirmText = "Delete",
            destructive = true,
            onDismiss = { confirmDelete = null },
            onConfirm = {
                viewModel.deleteForever(note.id)
                confirmDelete = null
            },
        )
    }
}
