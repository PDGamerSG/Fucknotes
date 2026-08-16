package com.example.fucknotes.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fucknotes.data.backup.BackupManager
import com.example.fucknotes.data.db.FolderEntity
import com.example.fucknotes.data.model.LayoutMode
import com.example.fucknotes.ui.components.ColorPickerDialog
import com.example.fucknotes.ui.components.FolderPickerDialog
import com.example.fucknotes.ui.components.TextPromptDialog
import com.example.fucknotes.ui.theme.brand
import kotlinx.coroutines.launch

/**
 * The shelf.
 *
 * Two panes: the rail says which notes, the sheet shows them. On a tablet both are on screen at
 * once; below [RailBreakpoint] there is not enough width for a 280 dp rail and a readable grid, so
 * the same rail becomes a drawer — one composable, two ways of arriving at it.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenNote: (Long) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var expandedFolders by remember { mutableStateOf(emptySet<Long>()) }
    var newFolderParent by remember { mutableStateOf<Long?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FolderEntity?>(null) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showNewNotebook by remember { mutableStateOf(false) }
    var showStationery by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.createNoteFromImage(it, onOpenNote) } }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupManager.MIME)
    ) { uri -> uri?.let(viewModel::backupTo) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    BackHandler(enabled = state.inSelectionMode) { viewModel.clearSelection() }

    val onCreate: (CreateAction) -> Unit = { action ->
        when (action) {
            CreateAction.NEW_NOTEBOOK -> showNewNotebook = true
            CreateAction.NEW_FOLDER -> {
                newFolderParent = null
                showNewFolder = true
            }

            CreateAction.IMAGE -> pickImage.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )

            else -> action.noteType()?.let { viewModel.createNote(it, onOpenNote) }
        }
    }

    val rail: @Composable (onCollapse: (() -> Unit)?) -> Unit = { onCollapse ->
        HomeRail(
            state = state,
            expandedFolders = expandedFolders,
            onToggleFolder = { id ->
                expandedFolders = if (id in expandedFolders) expandedFolders - id else expandedFolders + id
            },
            onScope = {
                viewModel.setScope(it)
                scope.launch { drawerState.close() }
            },
            onNewFolder = { parent ->
                newFolderParent = parent
                showNewFolder = true
            },
            onRenameFolder = { renaming = it },
            onDeleteFolder = viewModel::deleteFolder,
            onDeleteTag = viewModel::deleteTag,
            onStationery = { showStationery = true },
            onTrash = {
                scope.launch { drawerState.close() }
                onOpenTrash()
            },
            onSettings = {
                scope.launch { drawerState.close() }
                onOpenSettings()
            },
            onCollapse = onCollapse,
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= RailBreakpoint
        val openRail: (() -> Unit)? = if (wide) null else {
            { scope.launch { drawerState.open() }; Unit }
        }

        val sheet: @Composable () -> Unit = {
            HomeSheet(
                state = state,
                viewModel = viewModel,
                snackbar = snackbar,
                onOpenRail = openRail,
                onBackup = { createBackup.launch(BackupManager.suggestedFileName()) },
                onSearch = onOpenSearch,
                onCreate = onCreate,
                onNewNotebook = { showNewNotebook = true },
                onOpenNote = onOpenNote,
                onMove = { showMoveDialog = true },
                onColour = { showColorDialog = true },
            )
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                rail(null)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) { sheet() }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(drawerContainerColor = MaterialTheme.brand.railTop) {
                        rail { scope.launch { drawerState.close() } }
                    }
                },
                content = sheet,
            )
        }
    }

    if (showNewFolder) {
        TextPromptDialog(
            title = if (newFolderParent == null) "New folder" else "New subfolder",
            label = "Name",
            confirmText = "Create",
            onDismiss = { showNewFolder = false },
            onConfirm = {
                viewModel.createFolder(it, newFolderParent)
                showNewFolder = false
            },
        )
    }

    renaming?.let { folder ->
        TextPromptDialog(
            title = "Rename folder",
            label = "Name",
            initial = folder.name,
            confirmText = "Save",
            onDismiss = { renaming = null },
            onConfirm = {
                viewModel.renameFolder(folder, it)
                renaming = null
            },
        )
    }

    if (showMoveDialog) {
        FolderPickerDialog(
            folders = state.folders,
            onDismiss = { showMoveDialog = false },
            onPick = {
                viewModel.moveSelected(it)
                showMoveDialog = false
            },
        )
    }

    if (showColorDialog) {
        ColorPickerDialog(
            selected = 0,
            onDismiss = { showColorDialog = false },
            onPick = {
                viewModel.colorSelected(it)
                showColorDialog = false
            },
        )
    }

    if (showNewNotebook) {
        NewNotebookDialog(
            initialTitle = remember { viewModel.suggestedNotebookTitle() },
            onDismiss = { showNewNotebook = false },
            onCreate = { draft ->
                showNewNotebook = false
                viewModel.createNotebook(draft, onOpenNote)
            },
        )
    }

    if (showStationery) {
        StationerySheet(
            pens = state.settings.penTray,
            onEditPen = viewModel::editPen,
            onDismiss = { showStationery = false },
        )
    }
}

/** The right-hand pane: the action row, or the selection bar, above the shelf. */
@Composable
private fun HomeSheet(
    state: HomeUiState,
    viewModel: HomeViewModel,
    snackbar: SnackbarHostState,
    onOpenRail: (() -> Unit)?,
    onBackup: () -> Unit,
    onSearch: () -> Unit,
    onCreate: (CreateAction) -> Unit,
    onNewNotebook: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onMove: () -> Unit,
    onColour: () -> Unit,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.brand.sheet,
        topBar = {
            if (state.inSelectionMode) {
                SelectionBar(
                    count = state.selection.size,
                    archived = state.scope == Scope.Archive,
                    onClose = viewModel::clearSelection,
                    onPin = { viewModel.pinSelected(true) },
                    onUnpin = { viewModel.pinSelected(false) },
                    onArchive = { viewModel.archiveSelected(state.scope != Scope.Archive) },
                    onLock = { viewModel.lockSelected(true) },
                    onUnlock = { viewModel.lockSelected(false) },
                    onColor = onColour,
                    onMove = onMove,
                    onDuplicate = viewModel::duplicateSelected,
                    onMerge = { viewModel.mergeSelected(onOpenNote) },
                    onDelete = viewModel::trashSelected,
                    onSelectAll = { viewModel.selectAll(state.notes.map { it.id }) },
                )
            } else {
                SheetTopBar(
                    layout = state.layout,
                    sort = state.sort,
                    onOpenRail = onOpenRail,
                    onBackup = onBackup,
                    onSearch = onSearch,
                    onToggleLayout = {
                        viewModel.setLayout(
                            if (state.layout == LayoutMode.GRID) LayoutMode.LIST else LayoutMode.GRID
                        )
                    },
                    onSort = viewModel::setSort,
                    onCreate = onCreate,
                )
            }
        },
    ) { padding ->
        HomeShelf(
            state = state,
            previewOf = viewModel::previewOf,
            onOpenNote = onOpenNote,
            onToggleSelect = viewModel::toggleSelected,
            onNewNotebook = onNewNotebook,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.brand.sheet),
        )
    }
}

/** Below this width a permanent rail would leave the grid too narrow to read. */
private val RailBreakpoint = 720.dp
