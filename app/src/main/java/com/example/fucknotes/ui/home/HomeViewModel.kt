package com.example.fucknotes.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fucknotes.ServiceLocator
import com.example.fucknotes.data.db.AttachmentEntity
import com.example.fucknotes.data.db.FolderEntity
import com.example.fucknotes.data.db.NoteEntity
import com.example.fucknotes.data.db.NoteMediaFlags
import com.example.fucknotes.data.db.TagEntity
import com.example.fucknotes.data.model.AttachmentType
import com.example.fucknotes.data.model.LayoutMode
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.NotebookDraft
import com.example.fucknotes.data.model.SortOrder
import com.example.fucknotes.data.repo.SettingsRepository
import com.example.fucknotes.export.ExportFormat
import com.example.fucknotes.export.NoteExporter
import com.example.fucknotes.media.MediaStore
import com.example.fucknotes.reminder.ReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Which collection of notes the list is currently showing. */
sealed interface Scope {
    data object All : Scope
    data object Archive : Scope

    /** Notes that were never filed into a folder. */
    data object Unfiled : Scope
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
    /** Live note tally per folder id, for the rail's row counts. */
    val countByFolder: Map<Long?, Int> = emptyMap(),
    val loading: Boolean = true,
    val progress: String? = null,
    val busy: Boolean = false,
) {
    val inSelectionMode: Boolean get() = selection.isNotEmpty()
    val title: String
        get() = when (scope) {
            Scope.All -> "All notes"
            Scope.Archive -> "Archive"
            Scope.Unfiled -> "Unfiled"
            is Scope.Folder -> scope.name
            is Scope.Tag -> "#${scope.name}"
            is Scope.Search -> "Search"
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ServiceLocator.notes(app)
    private val settingsRepo = ServiceLocator.settings(app)
    private val backup = ServiceLocator.backup(app)
    private val exporter = NoteExporter(app, repo)

    private val _message = MutableStateFlow<String?>(null)
    private val progress = MutableStateFlow<String?>(null)
    var pendingExport: HomeExportRequest? = null

    /** One-shot text for the snackbar: the result of a backup, and nothing else so far. */
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() {
        _message.value = null
    }

    private val scope = MutableStateFlow<Scope>(Scope.All)
    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    private val notesFlow = combine(scope, settingsRepo.settings) { s, settings -> s to settings.sort }
        .flatMapLatest { (s, sort) ->
            when (s) {
                Scope.All -> repo.observeNotes(null, inFolderView = false, archived = false, sort = sort)
                Scope.Archive -> repo.observeNotes(null, inFolderView = false, archived = true, sort = sort)
                Scope.Unfiled -> repo.observeNotes(null, inFolderView = true, archived = false, sort = sort)
                is Scope.Folder -> repo.observeNotes(s.id, inFolderView = true, archived = false, sort = sort)
                is Scope.Tag -> repo.observeNotesForTag(s.id)
                is Scope.Search -> if (s.query.isBlank()) flowOf(emptyList()) else repo.search(s.query)
            }
        }

    private data class Decorations(
        val tagsByNote: Map<Long, List<String>>,
        val mediaByNote: Map<Long, NoteMediaFlags>,
        val countByFolder: Map<Long?, Int>,
    )

    private val decorations = combine(
        repo.observeTagLabels(),
        repo.observeMediaFlags(),
        repo.observeFolderCounts(),
    ) { labels, media, counts ->
        Decorations(
            tagsByNote = labels.groupBy({ it.noteId }, { it.name }),
            mediaByNote = media.associateBy { it.noteId },
            countByFolder = counts,
        )
    }

    val state: StateFlow<HomeUiState> = combine(
        notesFlow,
        combine(repo.observeFolders(), repo.observeTags()) { folders, tags -> folders to tags },
        settingsRepo.settings,
        combine(scope, selection, progress) { s, sel, progress -> Triple(s, sel, progress) },
        decorations,
    ) { notes, (folders, tags), settings, (currentScope, sel, progress), decor ->
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
            countByFolder = decor.countByFolder,
            loading = false,
            progress = progress,
            busy = progress != null,
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
        onCreated(repo.createNote(type, currentFolderId()))
    }

    /** Creates the notebook the New Notebook sheet describes, in the folder currently open. */
    fun createNotebook(draft: NotebookDraft, onCreated: (Long) -> Unit) = viewModelScope.launch {
        onCreated(
            repo.createNote(
                type = draft.type,
                folderId = currentFolderId(),
                title = draft.title,
                paperStyle = draft.paperStyle,
                paperColorArgb = draft.paperColorArgb,
                lineColorArgb = draft.lineColorArgb,
                lineSpacingDp = draft.spacingDp,
                pageSpec = draft.page,
                pageCount = draft.initialPageCount,
                pagesExplicitlyCreated = true,
            )
        )
    }

    /**
     * Starts a note from a picture.
     *
     * The image is copied into the app's own media directory first: the picker hands back a URI
     * that is only readable for as long as the permission grant lives, so a note that kept the URI
     * would show a broken thumbnail the next time it opened.
     */
    fun createNoteFromImage(uri: Uri, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val app = getApplication<Application>()
        val file = MediaStore.importImage(app, uri)
        if (file == null) {
            _message.value = "That image could not be imported"
            return@launch
        }
        val id = repo.createNote(NoteType.TEXT, currentFolderId())
        repo.addAttachment(
            AttachmentEntity(noteId = id, path = file.absolutePath, type = AttachmentType.IMAGE)
        )
        onCreated(id)
    }

    /**
     * Writes a full backup to the file the user picked.
     *
     * The stamp is recorded on success only, so the Settings screen's "last backup" line never
     * claims a backup that failed halfway.
     */
    fun backupTo(target: Uri) = viewModelScope.launch {
        runCatching { backup.exportTo(target) }
            .onSuccess { count ->
                settingsRepo.setLastBackupAt(
                    java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date())
                )
                _message.value = "Backed up $count notes"
            }
            .onFailure { _message.value = "Backup failed: ${it.message}" }
    }

    /** Exports the notes currently shown on the home shelf as a title-based PDF. */
    fun exportVisiblePdf(onReady: (File, String) -> Unit) = exportPdf(state.value.notes, onReady)

    /** Exports only the notes selected in the home shelf. */
    fun exportSelectedPdf(onReady: (File, String) -> Unit) =
        exportPdf(state.value.notes.filter { it.id in selection.value }, onReady)

    fun shareIntentFor(file: File, mime: String) = exporter.shareIntent(file, mime)

    private fun exportPdf(notes: List<NoteEntity>, onReady: (File, String) -> Unit) = viewModelScope.launch {
        if (notes.isEmpty()) {
            _message.value = "There are no notes to export"
            return@launch
        }
        runCatching { exporter.exportAll(notes, ExportFormat.PDF) }
            .onSuccess { onReady(it, ExportFormat.PDF.mime) }
            .onFailure { _message.value = "Export failed: ${it.message}" }
    }

    /** The folder a new note should land in: the one being viewed, or none. */
    private fun currentFolderId(): Long? = (scope.value as? Scope.Folder)?.id

    /** A default name for the next notebook, so the sheet opens with the field already filled. */
    fun suggestedNotebookTitle(): String {
        val used = state.value.notes.count { it.type == NoteType.NOTEBOOK }
        return "Untitled Notebook ${used + 1}"
    }

    fun trashSelected() = withSelection { ids ->
        ids.forEach { ReminderScheduler.cancel(getApplication<Application>(), it) }
        repo.trash(ids)
    }

    fun archiveSelected(archived: Boolean) = withSelection { repo.setArchived(it, archived) }

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

    fun toggleTheme() = viewModelScope.launch {
        val theme = state.value.settings.themeMode
        val systemDark = (getApplication<Application>().resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val dark = theme == com.example.fucknotes.data.model.ThemeMode.DARK ||
            (theme == com.example.fucknotes.data.model.ThemeMode.SYSTEM && systemDark)
        settingsRepo.setThemeMode(if (dark) com.example.fucknotes.data.model.ThemeMode.LIGHT else com.example.fucknotes.data.model.ThemeMode.DARK)
    }

    fun importPdf(uri: Uri, onCreated: (Long) -> Unit) = viewModelScope.launch {
        if (progress.value != null) return@launch
        val folder = currentFolderId()
        progress.value = "Opening PDF?"
        try {
            val id = com.example.fucknotes.media.PdfImporter(getApplication(), repo).import(uri, folder) { page, total ->
                progress.value = "Importing page $page of $total?"
            }
            onCreated(id)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            _message.value = "PDF import failed: ${error.message ?: "The file may be damaged or password protected"}"
        } finally { progress.value = null }
    }

    fun exportTarget(selected: Boolean = false): HomeExportTarget {
        val s = state.value
        if (!selected && s.scope is Scope.Folder) return HomeExportTarget(s.scope.name, folderId = s.scope.id)
        val notes = s.notes.filter { !selected || it.id in s.selection }
        return HomeExportTarget(if (notes.size == 1) notes.first().title.ifBlank { "Untitled" } else s.title,
            noteIds = notes.map { it.id })
    }

    fun saveExport(request: HomeExportRequest, destination: Uri) = viewModelScope.launch {
        if (progress.value != null) return@launch
        progress.value = "Preparing PDFs?"
        try {
            val target = request.target
            val notes = target.folderId?.let { repo.notesInFolderTree(it) }
                ?: target.noteIds.mapNotNull { repo.getNote(it) }
            com.example.fucknotes.export.FolderExporter(getApplication(), exporter).save(
                notes, state.value.folders, target.folderId, target.title, request.mode, destination,
            ) { done, total -> progress.value = "Exporting note $done of $total?" }
            _message.value = "Saved ${notes.size} ${if (notes.size == 1) "note" else "notes"}"
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            _message.value = "Export failed: ${error.message}"
        } finally { progress.value = null }
    }

    // ---- preferences -------------------------------------------------------

    fun setLayout(mode: LayoutMode) = viewModelScope.launch { settingsRepo.setLayout(mode) }

    fun setSort(order: SortOrder) = viewModelScope.launch { settingsRepo.setSort(order) }

    /**
     * Edits one slot of the app-wide pen tray from the stationery sheet.
     *
     * The tray is a property of the user's pen case rather than of any note, which is why it lives
     * in settings and can be arranged from the home screen at all.
     */
    fun editPen(index: Int, colorArgb: Int, widthDp: Float) = viewModelScope.launch {
        val tray = state.value.settings.penTray.toMutableList()
        if (index !in tray.indices) return@launch
        tray[index] = tray[index].copy(colorArgb = colorArgb, widthDp = widthDp)
        settingsRepo.setPenTray(tray)
    }

    // ---- folders & tags ----------------------------------------------------

    fun createFolder(name: String, parentId: Long?) = viewModelScope.launch {
        if (name.isBlank()) return@launch
        // The source app caps notebooks at three levels; keep the same guard.
        if (repo.folderDepth(parentId) >= MAX_FOLDER_DEPTH) return@launch
        repo.createFolder(name.trim(), parentId)
    }

    fun renameFolder(folder: FolderEntity, name: String) = viewModelScope.launch {
        if (name.isNotBlank()) {
            repo.renameFolder(folder, name.trim())
            if ((scope.value as? Scope.Folder)?.id == folder.id) scope.value = Scope.Folder(folder.id, name.trim())
        }
    }

    fun deleteFolder(id: Long) = viewModelScope.launch {
        val removed = mutableSetOf(id)
        var changed: Boolean
        do {
            changed = false
            state.value.folders.filter { it.parentId in removed }.forEach { if (removed.add(it.id)) changed = true }
        } while (changed)
        repo.deleteFolderTree(id)
        if ((scope.value as? Scope.Folder)?.id in removed) scope.value = Scope.All
    }

    fun deleteTag(id: Long) = viewModelScope.launch {
        repo.deleteTag(id)
        if ((scope.value as? Scope.Tag)?.id == id) scope.value = Scope.All
    }

    companion object {
        const val MAX_FOLDER_DEPTH = 3
    }
}
