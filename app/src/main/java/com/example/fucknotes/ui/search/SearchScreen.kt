package com.example.fucknotes.ui.search

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.fucknotes.ServiceLocator
import com.example.fucknotes.data.db.NoteEntity
import com.example.fucknotes.ui.components.EmptyState
import com.example.fucknotes.ui.home.NoteRow
import androidx.compose.foundation.clickable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.FlowPreview

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.notes(app)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: StateFlow<List<NoteEntity>> = _query
        .debounce(180)
        .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else repo.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tagsByNote: StateFlow<Map<Long, List<String>>> = repo.observeTagLabels()
        .map { labels -> labels.groupBy({ it.noteId }, { it.name }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** First-page images, so an imported PDF or deck shows its cover in the results. */
    val firstPageImageByNote: StateFlow<Map<Long, String>> = repo.observeFirstPageImages()
        .map { images -> images.groupBy { it.noteId }.mapValues { (_, list) -> list.first().path } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val darkReader: StateFlow<Boolean> = ServiceLocator.settings(app).settings
        .map { it.darkReader }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setQuery(value: String) {
        _query.value = value
    }

    fun previewOf(note: NoteEntity): String = repo.plainTextOf(note).take(240)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val tags by viewModel.tagsByNote.collectAsStateWithLifecycle()
    val covers by viewModel.firstPageImageByNote.collectAsStateWithLifecycle()
    val darkReader by viewModel.darkReader.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    TextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text("Search…") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                query.isBlank() -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "Search your notes",
                    subtitle = "Matches titles, body text and checklist items.",
                    modifier = Modifier.align(Alignment.Center),
                )

                results.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "No matches",
                    subtitle = "Nothing found for \"$query\".",
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results, key = { it.id }) { note ->
                        NoteRow(
                            note = note,
                            preview = viewModel.previewOf(note),
                            firstPageImagePath = covers[note.id],
                            darkReader = darkReader,
                            selected = false,
                            interaction = Modifier.clickable { onOpenNote(note.id) },
                        )
                    }
                }
            }
        }
    }
}
