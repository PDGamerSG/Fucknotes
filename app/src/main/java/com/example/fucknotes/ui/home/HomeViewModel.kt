package com.example.fucknotes.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fucknotes.ServiceLocator
import com.example.fucknotes.data.db.FolderEntity
import com.example.fucknotes.data.db.NoteEntity
import com.example.fucknotes.data.db.NoteMediaFlags
import com.example.fucknotes.data.db.TagEntity
import com.example.fucknotes.data.model.LayoutMode
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.SortOrder
import com.example.fucknotes.data.repo.SettingsRepository
import com.example.fucknotes.reminder.ReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Which collection of notes the list is currently showing. */
sealed interface Scope {
    data object All : Scope
    data object Archive : Scope
    data class Folder(val id: Long, val name: String) : Scope
    data class Tag(val id: Long, val name: String) : Scope
    data class Search(val query: String) : Scope
}

data class HomeUiState(
    val notes: List<NoteEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val scope: Scope = Scope.All,
    val layout: LayoutMode = LayoutMode.GRID,
    val sort: SortOrder = SortOrder.UPDATED_DESC,
    val selection: Set<Long> = emptySet(),
    val settings: SettingsRepository.Settings = SettingsRepository.Settings(),
    val tagsByNote: Map<Long, List<String>> = emptyMap(),
    val mediaByNote: Map<Long, NoteMediaFlags> = emptyMap(),
    val loading: Boolean = true,
) {
    val inSelectionMode: Boolean get() = selection.isNotEmpty()
    val title: String
        get() = when (scope) {
            Scope.All -> "All notes"
            Scope.Archive -> "Archive"
            is Scope.Folder -> scope.name
            is Scope.Tag -> "#${scope.name}"
            is Scope.Search -> "Search"
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.notes(app)
    private val settingsRepo = ServiceLocator.settings(app)

    private val scope = MutableStateFlow<Scope>(Scope.All)
    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    private val notesFlow = combine(scope, settingsRepo.settings) { s, settings -> s to settings.sort }
        .flatMapLatest { (s, sort) ->
            when (s) {
                Scope.All -> repo.observeNotes(null, inFolderView = false, archived = false, sort = sort)
                Scope.Archive -> repo.observeNotes(null, inFolderView = false, archived = true, sort = sort)
                is Scope.Folder -> repo.observeNotes(s.id, inFolderView = true, archived = false, sort = sort)
                is Scope.Tag -> repo.observeNotesForTag(s.id)
                is Scope.Search -> if (s.query.isBlank()) flowOf(emptyList()) else repo.search(s.query)
            }
        }

    private data class Decorations(
        val tagsByNote: Map<Long, List<String>>,
        val mediaByNote: Map<Long, NoteMediaFlags>,
    )

    private val decorations = combine(repo.observeTagLabels(), repo.observeMediaFlags()) { labels, media ->
        Decorations(
            tagsByNote = labels.groupBy({ it.noteId }, { it.name }),
            mediaByNote = media.associateBy { it.noteId },
        )
    }

    val state: StateFlow<HomeUiState> = combine(
        notesFlow,
        combine(repo.observeFolders(), repo.observeTags()) { folders, tags -> folders to tags },
        settingsRepo.settings,
        combine(scope, selection) { s, sel -> s to sel },
        decorations,
    ) { notes, (folders, tags), settings, (currentScope, sel), decor ->
        HomeUiState(
            notes = notes,
            folders = folders,
            tags = tags,
            scope = currentScope,
            layout = settings.layout,
            sort = settings.sort,
            // Drop ids that vanished from the visible list so the action bar cannot act on ghosts.
            selection = sel intersect notes.map { it.id }.toSet(),
            settings = settings,
            tagsByNote = decor.tagsByNote,
            mediaByNote = decor.mediaByNote,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun previewOf(note: NoteEntity): String = repo.plainTextOf(note).take(240)

    // ---- navigation --------------------------------------------------------

    fun setScope(next: Scope) {
        scope.value = next
        selection.value = emptySet()
    }

    fun setSearchQuery(query: String) {
        scope.value = Scope.Search(query)
    }

    // ---- selection ---------------------------------------------------------

    fun toggleSelected(id: Long) {
        selection.value = selection.value.let { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        selection.value = emptySet()
    }

    fun selectAll(ids: List<Long>) {
        selection.value = ids.toSet()
    }

    // ---- actions -----------------------------------------------------------

    fun createNote(type: NoteType, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val folderId = (scope.value as? Scope.Folder)?.id
        onCreated(repo.createNote(type, folderId))
    }

    fun trashSelected() = withSelection { ids ->
        ids.forEach { ReminderScheduler.cancel(getApplication<Application>(), it) }
        repo.trash(ids)
    }

    fun pinSelected(pinned: Boolean) = withSelection { repo.setPinned(it, pinned) }

    fun archiveSelected(archived: Boolean) = withSelection { repo.setArchived(it, archived) }

    fun lockSelected(locked: Boolean) = withSelection { repo.setLocked(it, locked) }

    fun colorSelected(color: Int) = withSelection { repo.setColor(it, color) }

    fun moveSelected(folderId: Long?) = withSelection { repo.moveToFolder(it, folderId) }

    fun duplicateSelected() = withSelection { ids -> ids.forEach { repo.duplicate(it) } }

    fun mergeSelected(onMerged: (Long) -> Unit) {
        val ids = selection.value.toList()
        if (ids.size < 2) return
        viewModelScope.launch {
            repo.merge(ids)?.let(onMerged)
            selection.value = emptySet()
        }
    }

    private fun withSelection(block: suspend (List<Long>) -> Unit) {
        val ids = selection.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            block(ids)
            selection.value = emptySet()
        }
    }

    // ---- preferences -------------------------------------------------------

    fun setLayout(mode: LayoutMode) = viewModelScope.launch { settingsRepo.setLayout(mode) }

    fun setSort(order: SortOrder) = viewModelScope.launch { settingsRepo.setSort(order) }

    // ---- folders & tags ----------------------------------------------------

    fun createFolder(name: String, parentId: Long?) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        // The source app caps notebooks at three levels; keep the same guard.
        if (repo.folderDepth(parentId) >= MAX_FOLDER_DEPTH) return@launch
        repo.createFolder(name.trim(), parentId)
    }

    fun renameFolder(folder: FolderEntity, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) repo.renameFolder(folder, name.trim())
    }

    fun deleteFolder(id: Long) = viewModelScope.launch {
        repo.deleteFolderTree(id)
        if ((scope.value as? Scope.Folder)?.id == id) scope.value = Scope.All
    }

    fun deleteTag(id: Long) = viewModelScope.launch {
        repo.deleteTag(id)
        if ((scope.value as? Scope.Tag)?.id == id) scope.value = Scope.All
    }

    companion object {
        const val MAX_FOLDER_DEPTH = 3
    }
}
