package com.example.fucknotes.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fucknotes.data.db.FolderEntity
import com.example.fucknotes.data.model.LayoutMode
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.SortOrder
import com.example.fucknotes.ui.components.EmptyState
import com.example.fucknotes.ui.components.NoteCard
import com.example.fucknotes.ui.components.ColorPickerDialog
import com.example.fucknotes.ui.components.FolderPickerDialog
import com.example.fucknotes.ui.components.TextPromptDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenNote: (Long) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var showSortMenu by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }
    var newFolderParent by remember { mutableStateOf<Long?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FolderEntity?>(null) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = state.inSelectionMode) { viewModel.clearSelection() }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                state = state,
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
                onTrash = {
                    scope.launch { drawerState.close() }
                    onOpenTrash()
                },
                onSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                },
            )
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
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
                        onColor = { showColorDialog = true },
                        onMove = { showMoveDialog = true },
                        onDuplicate = viewModel::duplicateSelected,
                        onMerge = { viewModel.mergeSelected(onOpenNote) },
                        onDelete = viewModel::trashSelected,
                        onSelectAll = { viewModel.selectAll(state.notes.map { it.id }) },
                    )
                } else {
                    TopAppBar(
                        title = { Text(state.title) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Open menu")
                            }
                        },
                        actions = {
                            IconButton(onClick = onOpenSearch) {
                                Icon(Icons.Filled.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = {
                                viewModel.setLayout(
                                    if (state.layout == LayoutMode.GRID) LayoutMode.LIST else LayoutMode.GRID
                                )
                            }) {
                                Icon(
                                    if (state.layout == LayoutMode.GRID) Icons.Outlined.ViewAgenda
                                    else Icons.Outlined.GridView,
                                    contentDescription = "Switch layout",
                                )
                            }
                            Box {
                                IconButton(onClick = { showOverflow = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                                }
                                DropdownMenu(showOverflow, onDismissRequest = { showOverflow = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Sort by") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Sort, null) },
                                        onClick = {
                                            showOverflow = false
                                            showSortMenu = true
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Archive") },
                                        leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                                        onClick = {
                                            showOverflow = false
                                            viewModel.setScope(Scope.Archive)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Trash") },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                        onClick = {
                                            showOverflow = false
                                            onOpenTrash()
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                                        onClick = {
                                            showOverflow = false
                                            onOpenSettings()
                                        },
                                    )
                                }
                                SortMenu(
                                    expanded = showSortMenu,
                                    current = state.sort,
                                    onDismiss = { showSortMenu = false },
                                    onPick = {
                                        viewModel.setSort(it)
                                        showSortMenu = false
                                    },
                                )
                            }
                        },
                    )
                }
            },
            floatingActionButton = {
                if (!state.inSelectionMode) {
                    Column(horizontalAlignment = Alignment.End) {
                        AnimatedVisibility(
                            visible = showFabMenu,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(bottom = 12.dp),
                            ) {
                                MiniFab("Canvas", Icons.Outlined.Brush) {
                                    showFabMenu = false
                                    viewModel.createNote(NoteType.DRAWING, onOpenNote)
                                }
                                MiniFab("Notebook", Icons.Outlined.MenuBook) {
                                    showFabMenu = false
                                    viewModel.createNote(NoteType.NOTEBOOK, onOpenNote)
                                }
                                MiniFab("Checklist", Icons.Outlined.CheckBox) {
                                    showFabMenu = false
                                    viewModel.createNote(NoteType.CHECKLIST, onOpenNote)
                                }
                                MiniFab("Text note", Icons.Outlined.Description) {
                                    showFabMenu = false
                                    viewModel.createNote(NoteType.TEXT, onOpenNote)
                                }
                            }
                        }
                        FloatingActionButton(onClick = { showFabMenu = !showFabMenu }) {
                            Icon(
                                if (showFabMenu) Icons.Filled.Close else Icons.Filled.Add,
                                contentDescription = "New note",
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when {
                    state.loading -> Unit

                    state.notes.isEmpty() -> EmptyState(
                        icon = Icons.AutoMirrored.Outlined.Notes,
                        title = if (state.scope == Scope.Archive) "Archive is empty" else "No notes yet",
                        subtitle = "Tap + to start a text note, checklist or handwritten page.",
                        modifier = Modifier.align(Alignment.Center),
                    )

                    state.layout == LayoutMode.GRID -> LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Adaptive(180.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalItemSpacing = 10.dp,
                    ) {
                        items(state.notes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                preview = viewModel.previewOf(note),
                                tagNames = state.tagsByNote[note.id].orEmpty(),
                                selected = note.id in state.selection,
                                selectionMode = state.inSelectionMode,
                                hasImages = (state.mediaByNote[note.id]?.images ?: 0) > 0,
                                hasAudio = (state.mediaByNote[note.id]?.audio ?: 0) > 0,
                                onClick = {
                                    if (state.inSelectionMode) viewModel.toggleSelected(note.id)
                                    else onOpenNote(note.id)
                                },
                                onLongClick = { viewModel.toggleSelected(note.id) },
                            )
                        }
                    }

                    else -> LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp, 12.dp, 12.dp, 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.notes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                preview = viewModel.previewOf(note),
                                tagNames = state.tagsByNote[note.id].orEmpty(),
                                selected = note.id in state.selection,
                                selectionMode = state.inSelectionMode,
                                hasImages = (state.mediaByNote[note.id]?.images ?: 0) > 0,
                                hasAudio = (state.mediaByNote[note.id]?.audio ?: 0) > 0,
                                onClick = {
                                    if (state.inSelectionMode) viewModel.toggleSelected(note.id)
                                    else onOpenNote(note.id)
                                },
                                onLongClick = { viewModel.toggleSelected(note.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewFolder) {
        TextPromptDialog(
            title = if (newFolderParent == null) "New notebook" else "New subfolder",
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
}

@Composable
private fun MiniFab(label: String, icon: ImageVector, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(label) },
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    archived: Boolean,
    onClose: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onArchive: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onColor: () -> Unit,
    onMove: () -> Unit,
    onDuplicate: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Clear selection")
            }
        },
        actions = {
            IconButton(onClick = onPin) { Icon(Icons.Outlined.PushPin, contentDescription = "Pin") }
            IconButton(onClick = onColor) { Icon(Icons.Outlined.Palette, contentDescription = "Colour") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
            Box {
                IconButton(onClick = { more = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(
                        text = { Text("Select all") },
                        leadingIcon = { Icon(Icons.Filled.Check, null) },
                        onClick = { more = false; onSelectAll() },
                    )
                    DropdownMenuItem(
                        text = { Text("Unpin") },
                        leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                        onClick = { more = false; onUnpin() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (archived) "Unarchive" else "Archive") },
                        leadingIcon = {
                            Icon(if (archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, null)
                        },
                        onClick = { more = false; onArchive() },
                    )
                    DropdownMenuItem(
                        text = { Text("Move to folder") },
                        leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                        onClick = { more = false; onMove() },
                    )
                    DropdownMenuItem(
                        text = { Text("Add lock") },
                        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                        onClick = { more = false; onLock() },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove lock") },
                        leadingIcon = { Icon(Icons.Outlined.LockOpen, null) },
                        onClick = { more = false; onUnlock() },
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                        onClick = { more = false; onDuplicate() },
                    )
                    DropdownMenuItem(
                        text = { Text("Merge notes") },
                        leadingIcon = { Icon(Icons.Outlined.Merge, null) },
                        onClick = { more = false; onMerge() },
                    )
                }
            }
        },
    )
}

@Composable
private fun SortMenu(
    expanded: Boolean,
    current: SortOrder,
    onDismiss: () -> Unit,
    onPick: (SortOrder) -> Unit,
) {
    val labels = listOf(
        SortOrder.UPDATED_DESC to "Modified date (newest)",
        SortOrder.UPDATED_ASC to "Modified date (oldest)",
        SortOrder.CREATED_DESC to "Created date (newest)",
        SortOrder.CREATED_ASC to "Created date (oldest)",
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

@Composable
private fun DrawerContent(
    state: HomeUiState,
    onScope: (Scope) -> Unit,
    onNewFolder: (Long?) -> Unit,
    onRenameFolder: (FolderEntity) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onDeleteTag: (Long) -> Unit,
    onTrash: () -> Unit,
    onSettings: () -> Unit,
) {
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(
                "Fucknotes",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(24.dp, 24.dp, 24.dp, 12.dp),
            )

            NavigationDrawerItem(
                label = { Text("All notes") },
                icon = { Icon(Icons.AutoMirrored.Outlined.Notes, null) },
                selected = state.scope == Scope.All,
                onClick = { onScope(Scope.All) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            NavigationDrawerItem(
                label = { Text("Archive") },
                icon = { Icon(Icons.Outlined.Archive, null) },
                selected = state.scope == Scope.Archive,
                onClick = { onScope(Scope.Archive) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )

            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

            Row(
                Modifier.fillMaxWidth().padding(start = 28.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Notebooks",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onNewFolder(null) }) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = "New notebook")
                }
            }

            FolderTree(
                folders = state.folders,
                parentId = null,
                depth = 0,
                state = state,
                onScope = onScope,
                onNewFolder = onNewFolder,
                onRenameFolder = onRenameFolder,
                onDeleteFolder = onDeleteFolder,
            )

            if (state.tags.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                Text(
                    "Tags",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, bottom = 4.dp),
                )
                state.tags.forEach { tag ->
                    var menu by remember(tag.id) { mutableStateOf(false) }
                    Box {
                        NavigationDrawerItem(
                            label = { Text("#${tag.name}") },
                            icon = { Icon(Icons.AutoMirrored.Outlined.Label, null) },
                            selected = (state.scope as? Scope.Tag)?.id == tag.id,
                            onClick = { onScope(Scope.Tag(tag.id, tag.name)) },
                            badge = {
                                IconButton(onClick = { menu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Tag options")
                                }
                            },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete tag") },
                                onClick = { menu = false; onDeleteTag(tag.id) },
                            )
                        }
                    }
                }
            }

            HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp))

            NavigationDrawerItem(
                label = { Text("Trash") },
                icon = { Icon(Icons.Outlined.Delete, null) },
                selected = false,
                onClick = onTrash,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            NavigationDrawerItem(
                label = { Text("Settings") },
                icon = { Icon(Icons.Outlined.Settings, null) },
                selected = false,
                onClick = onSettings,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Renders the notebook hierarchy, indenting one level at a time. */
@Composable
private fun FolderTree(
    folders: List<FolderEntity>,
    parentId: Long?,
    depth: Int,
    state: HomeUiState,
    onScope: (Scope) -> Unit,
    onNewFolder: (Long?) -> Unit,
    onRenameFolder: (FolderEntity) -> Unit,
    onDeleteFolder: (Long) -> Unit,
) {
    folders.filter { it.parentId == parentId }.forEach { folder ->
        var menu by remember(folder.id) { mutableStateOf(false) }
        Box {
            NavigationDrawerItem(
                label = { Text(folder.name) },
                icon = { Icon(Icons.Outlined.Folder, null) },
                selected = (state.scope as? Scope.Folder)?.id == folder.id,
                onClick = { onScope(Scope.Folder(folder.id, folder.name)) },
                badge = {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Folder options")
                    }
                },
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .padding(start = (depth * 14).dp),
            )
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                if (depth + 1 < HomeViewModel.MAX_FOLDER_DEPTH) {
                    DropdownMenuItem(
                        text = { Text("New subfolder") },
                        onClick = { menu = false; onNewFolder(folder.id) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { menu = false; onRenameFolder(folder) },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { menu = false; onDeleteFolder(folder.id) },
                )
            }
        }
        FolderTree(folders, folder.id, depth + 1, state, onScope, onNewFolder, onRenameFolder, onDeleteFolder)
    }
}
