package com.example.fucknotes.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
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
    val context = LocalContext.current
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

    val pickDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importDocument(it, onOpenNote) }
    }
    var exportTarget by remember { mutableStateOf<HomeExportTarget?>(null) }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val request = viewModel.pendingExport
        viewModel.pendingExport = null
        if (uri != null && request != null) viewModel.saveExport(request, uri)
    }
    val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val request = viewModel.pendingExport
        viewModel.pendingExport = null
        if (uri != null && request != null) viewModel.saveExport(request, uri)
    }
    val saveFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val request = viewModel.pendingExport
        viewModel.pendingExport = null
        if (uri != null && request != null) viewModel.saveExport(request, uri)
    }

    val createBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupManager.MIME)
    ) { uri -> uri?.let(viewModel::backupTo) }

    // Google's account and consent sheet, when Drive is used for the first time.
    val driveConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.driveConsentResult(result.data)
    }
    val driveStatus by viewModel.driveStatus.collectAsStateWithLifecycle()
    val driveControl: @Composable () -> Unit = {
        DriveAction(
            email = state.settings.driveEmail,
            status = driveStatus,
            lastSyncAt = state.settings.driveLastSyncAt,
            lastError = state.settings.driveLastError,
            autoSync = state.settings.driveAutoSync,
            onLogin = { viewModel.driveLogin { intent -> driveConsent.launch(IntentSenderRequest.Builder(intent).build()) } },
            onSyncNow = viewModel::driveSyncNow,
            onRestore = viewModel::driveRestore,
            onOpenFolder = { viewModel.driveFolderUrl { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
            onAutoSync = viewModel::setDriveAutoSync,
            onLogout = viewModel::driveLogout,
        )
    }

    val exportVisiblePdf: () -> Unit = { exportTarget = viewModel.exportTarget() }
    val exportSelectedPdf: () -> Unit = { exportTarget = viewModel.exportTarget(selected = true) }

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
            CreateAction.IMPORT_PDF -> pickDocument.launch(arrayOf(CreateAction.MIME_PDF))
            CreateAction.IMPORT_SLIDES -> pickDocument.launch(arrayOf(CreateAction.MIME_PPTX, CreateAction.MIME_PPT))

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
            onExportFolder = { exportTarget = HomeExportTarget(it.name, folderId = it.id) },
            onToggleTheme = { viewModel.toggleTheme() },
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
                drive = driveControl,
                state = state,
                viewModel = viewModel,
                snackbar = snackbar,
                onOpenRail = openRail,
                onBackup = { createBackup.launch(BackupManager.suggestedFileName()) },
                onExport = exportVisiblePdf,
                onExportSelected = exportSelectedPdf,
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
            title = if (newFolderParent == null) "New collection" else "New folder",
            label = "Name",
            confirmText = "Create",
            onDismiss = { showNewFolder = false },
            onConfirm = {
                viewModel.createFolder(it, newFolderParent)
                newFolderParent?.let { expandedFolders = expandedFolders + it }
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
            initialDark = state.settings.darkReader,
            onDismiss = { showNewNotebook = false },
            onCreate = { draft ->
                showNewNotebook = false
                viewModel.createNotebook(draft, onOpenNote)
            },
        )
    }

    exportTarget?.let { target ->
        HomeExportDialog(target.title, state.busy, onDismiss = { exportTarget = null }, initialDark = state.settings.darkReader) { mode, dark ->
            exportTarget = null
            val request = HomeExportRequest(target, mode, dark)
            viewModel.pendingExport = request
            val name = com.example.fucknotes.export.ExportNames.safeBase(target.title)
            when (mode) {
                HomeExportMode.COMBINED -> savePdf.launch("$name.pdf")
                HomeExportMode.ZIP -> saveZip.launch("$name.zip")
                HomeExportMode.SEPARATE -> saveFolder.launch(null)
            }
        }
    }
    if (state.busy) {
        com.example.fucknotes.ui.components.ProgressDialog(
            title = if (state.progress?.contains("slide", ignoreCase = true) == true || state.progress?.contains("presentation", ignoreCase = true) == true) "Importing presentation"
            else if (state.progress?.contains("Export", ignoreCase = true) == true || state.progress?.contains("PDFs", ignoreCase = true) == true) "Exporting"
            else "Importing",
            message = state.progress,
        )
    }

}

/** The right-hand pane: the action row, or the selection bar, above the shelf. */
@Composable
private fun HomeSheet(
    drive: @Composable () -> Unit,
    state: HomeUiState,
    viewModel: HomeViewModel,
    snackbar: SnackbarHostState,
    onOpenRail: (() -> Unit)?,
    onBackup: () -> Unit,
    onExport: () -> Unit,
    onExportSelected: () -> Unit,
    onSearch: () -> Unit,
    onCreate: (CreateAction) -> Unit,
    onNewNotebook: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onMove: () -> Unit,
    onColour: () -> Unit,
) {
    var cornerCreateMenu by remember { mutableStateOf(false) }

    // The activity's Surface already paints the sheet colour; painting it twice more here was
    // two extra full-screen fills per frame on a 3200 px panel.
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent,
        topBar = {
            if (state.inSelectionMode) {
                SelectionBar(
                    count = state.selection.size,
                    archived = state.scope == Scope.Archive,
                    onClose = viewModel::clearSelection,
                    onExport = onExportSelected,
                    onArchive = { viewModel.archiveSelected(state.scope != Scope.Archive) },
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
                    onExport = onExport,
                    onSearch = onSearch,
                    drive = drive,
                    onToggleLayout = {
                        viewModel.setLayout(
                            if (state.layout == LayoutMode.GRID) LayoutMode.LIST else LayoutMode.GRID
                        )
                    },
                    onSort = viewModel::setSort,
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            HomeShelf(
                state = state,
                previewOf = viewModel::previewOf,
                onOpenNote = onOpenNote,
                onToggleSelect = viewModel::toggleSelected,
                onNewNotebook = onNewNotebook,
                onMoveNote = viewModel::moveNote,
                modifier = Modifier.fillMaxSize(),
            )

            // FreeNotes-style corner affordance: the same complete create menu is always within
            // reach, including when the top bar has scrolled out of view on a small display.
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
            ) {
                FloatingActionButton(
                    onClick = { cornerCreateMenu = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = "Create")
                }
                CreateMenu(
                    expanded = cornerCreateMenu,
                    onDismiss = { cornerCreateMenu = false },
                    onPick = { cornerCreateMenu = false; onCreate(it) },
                )
            }
        }
    }
}

/** Below this width a permanent rail would leave the grid too narrow to read. */
private val RailBreakpoint = 720.dp
