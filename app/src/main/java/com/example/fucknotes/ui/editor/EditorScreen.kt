package com.example.fucknotes.ui.editor

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.export.ExportFormat
import com.example.fucknotes.media.AudioPlayer
import com.example.fucknotes.media.MediaStore
import com.example.fucknotes.ui.common.paperColorOr
import com.example.fucknotes.ui.components.ColorPickerDialog
import com.example.fucknotes.ui.components.ConfirmDialog
import com.example.fucknotes.ui.components.TextPromptDialog
import com.example.fucknotes.ui.editor.ink.DefaultRuleColor
import com.example.fucknotes.ui.editor.ink.InkCamera
import com.example.fucknotes.ui.editor.ink.InkSurface
import com.example.fucknotes.ui.editor.ink.PaperWhite
import com.example.fucknotes.ui.editor.tabs.EditorTabBar
import com.example.fucknotes.ui.editor.tabs.OpenTabs
import com.example.fucknotes.ui.theme.brand
import kotlinx.coroutines.launch

/**
 * One open note.
 *
 * The screen splits by what the note *is*: an ink note is a page with chrome floating over it
 * ([InkEditor]), a typed note is a form with a bar at each end ([TypedEditor]). This function owns
 * only what both need — the note's state, the system pickers, and the dialogs the menu opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val player = remember { AudioPlayer() }
    var playing by remember { mutableStateOf<String?>(null) }

    // Pan and zoom belong to the view, not the note, so the camera is remembered here.
    val camera = remember { InkCamera(LocalDensity.current.density) }

    val isInkNote = state.type.isInk

    // Handed to the ink surface as a stable lambda so committing a stroke does not recompose it.
    val inkStrokes = remember(viewModel) { { viewModel.strokes } }

    var overflow by remember { mutableStateOf(false) }
    var showColor by remember { mutableStateOf(false) }
    var showPaper by remember { mutableStateOf(false) }
    var showPages by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var pendingCameraFile by remember { mutableStateOf<java.io.File?>(null) }

    // Notes flagged as locked stay hidden until this session unlocks them.
    var unlocked by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let(viewModel::attachImage) }

    val takePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingCameraFile
        pendingCameraFile = null
        if (success && file != null) viewModel.attachCameraPhoto(file) else file?.delete()
    }

    val requestAudioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startRecording()
        else scope.launch { snackbar.showSnackbar("Microphone access is needed to record a voice note") }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // The tab bar names notes by their title, and a note is usually named after it is started.
    LaunchedEffect(state.noteId, state.title) {
        OpenTabs.open(state.noteId, state.title.ifBlank { "Untitled" })
    }

    if (state.locked && !unlocked) {
        LockedNoteGate(onUnlock = { unlocked = true }, onBack = onBack)
        return
    }

    val leaveNote: () -> Unit = {
        scope.launch {
            viewModel.discardIfEmpty()
            OpenTabs.forget(state.noteId)
            onBack()
        }
    }

    val closeTab: (Long) -> Unit = { id ->
        val next = OpenTabs.close(id)
        when {
            id != state.noteId -> Unit
            next == null -> leaveNote()
            else -> onOpenNote(next)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                EditorTabBar(
                    tabs = OpenTabs.tabs,
                    activeNoteId = state.noteId,
                    onSelect = { if (it != state.noteId) onOpenNote(it) },
                    onClose = closeTab,
                )
                // A typed note keeps an ordinary bar; an ink note's controls float over its page.
                if (!isInkNote) {
                    TopAppBar(
                        title = {},
                        navigationIcon = {
                            IconButton(onClick = leaveNote) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to notes")
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.setInkMode(!state.inkMode) }) {
                                Icon(
                                    Icons.Outlined.Brush,
                                    contentDescription = "Write over this note",
                                    tint = if (state.inkMode) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box {
                                IconButton(onClick = { overflow = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                                }
                                EditorMenu(
                                    expanded = overflow,
                                    state = state,
                                    viewModel = viewModel,
                                    context = context,
                                    onDismiss = { overflow = false },
                                    onColor = { showColor = true },
                                    onPaper = { showPaper = true },
                                    onTags = { showTagDialog = true },
                                    onExport = { showExport = true },
                                    onInfo = { showInfo = true },
                                    onDelete = { showDeleteConfirm = true },
                                    onOpenNote = onOpenNote,
                                )
                            }
                        },
                    )
                }
            }
        },
        bottomBar = {
            if (!isInkNote) {
                EditorBottomBar(
                    state = state,
                    onAddImage = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onTakePhoto = {
                        val file = MediaStore.newFile(context, "jpg")
                        pendingCameraFile = file
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        takePhoto.launch(uri)
                    },
                    onRecord = {
                        if (state.recording) viewModel.stopRecording()
                        else requestAudioPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                    },
                    onCancelRecording = viewModel::cancelRecording,
                    onAddChecklistItem = { viewModel.addChecklistItem() },
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (isInkNote) {
                InkEditor(
                    state = state,
                    strokes = inkStrokes,
                    camera = camera,
                    canUndo = viewModel.canUndoInk,
                    canRedo = viewModel.canRedoInk,
                    pageCount = { viewModel.pageCount },
                    onStrokesFinished = viewModel::commitStrokes,
                    onErase = viewModel::eraseStrokes,
                    onToggleEraser = viewModel::toggleEraser,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onSelectPen = viewModel::selectPen,
                    onEditPen = viewModel::editPen,
                    onSelectTool = viewModel::setTool,
                    onToggleFingerDrawing = viewModel::toggleFingerDrawing,
                    onToggleZoomLock = viewModel::toggleZoomLock,
                    onAppendPage = viewModel::appendPage,
                    onBack = leaveNote,
                    onPages = { showPages = true },
                    onPaper = { showPaper = true },
                    onAddImage = {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onMore = { overflow = true },
                )

                // Anchored under the quick bar, where the button that opens it sits.
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 56.dp)
                ) {
                    EditorMenu(
                        expanded = overflow,
                        state = state,
                        viewModel = viewModel,
                        context = context,
                        onDismiss = { overflow = false },
                        onColor = { showColor = true },
                        onPaper = { showPaper = true },
                        onTags = { showTagDialog = true },
                        onExport = { showExport = true },
                        onInfo = { showInfo = true },
                        onDelete = { showDeleteConfirm = true },
                        onOpenNote = onOpenNote,
                    )
                }
            } else {
                TypedEditor(
                    state = state,
                    viewModel = viewModel,
                    playingPath = playing,
                    onPlay = { path ->
                        player.toggle(path) { playing = null }
                        playing = player.playingPath
                    },
                    onAddTag = { showTagDialog = true },
                ) {
                    // Layered ink over a typed note: the same surface, transparent, with no paper
                    // of its own so the words underneath stay visible.
                    if (state.inkActive) {
                        InkSurface(
                            strokes = inkStrokes,
                            camera = camera,
                            tool = state.tool,
                            colorArgb = state.activePen.colorArgb,
                            widthDp = state.activePen.widthDp,
                            enabled = true,
                            fingerDrawing = state.fingerDrawing,
                            zoomLocked = state.zoomLocked,
                            notebook = false,
                            paperStyle = PaperStyle.PLAIN,
                            pageSpec = state.page,
                            ruleColor = state.lineColorArgb.paperColorOr(DefaultRuleColor),
                            ruleSpacingDp = state.ruleSpacingDp,
                            deskColor = MaterialTheme.brand.desk,
                            onStrokesFinished = viewModel::commitStrokes,
                            onErase = viewModel::eraseStrokes,
                            onToggleEraser = viewModel::toggleEraser,
                            modifier = Modifier.fillMaxSize(),
                            pageColor = androidx.compose.ui.graphics.Color.Transparent,
                        )
                    }
                }
            }
        }
    }

    if (showColor) {
        ColorPickerDialog(
            selected = state.colorArgb,
            onDismiss = { showColor = false },
            onPick = { viewModel.setColor(it); showColor = false },
        )
    }

    if (showPaper) {
        PaperSheet(
            style = state.paperStyle,
            paperColorArgb = state.colorArgb,
            lineColorArgb = state.lineColorArgb,
            spacingDp = state.lineSpacingDp,
            page = state.page,
            notebook = state.type == NoteType.NOTEBOOK,
            onStyle = viewModel::setPaperStyle,
            onPaperColor = viewModel::setColor,
            onLineColor = viewModel::setLineColor,
            onSpacing = viewModel::setLineSpacing,
            onPage = viewModel::setPageSpec,
            onDismiss = { showPaper = false },
        )
    }

    if (showPages) {
        PagesSheet(
            pageCount = viewModel.pageCount,
            currentPage = camera.pageAtCentre(state.page),
            style = state.paperStyle,
            paperColorArgb = state.colorArgb,
            lineColorArgb = state.lineColorArgb,
            spacingDp = state.ruleSpacingDp,
            page = state.page,
            onGoToPage = { index ->
                camera.goToPage(index, state.page)
                showPages = false
            },
            onDismiss = { showPages = false },
        )
    }

    if (showExport) {
        ExportDialog(
            onDismiss = { showExport = false },
            onExport = { format, dark ->
                showExport = false
                viewModel.export(format, dark) { file, mime ->
                    context.startActivity(
                        Intent.createChooser(viewModel.shareIntentFor(file, mime), "Export note")
                    )
                }
            },
        )
    }

    if (showTagDialog) {
        TextPromptDialog(
            title = "Add tag",
            label = "Tag name",
            confirmText = "Add",
            supportingText = "Tags group notes across notebooks.",
            onDismiss = { showTagDialog = false },
            onConfirm = { viewModel.addTag(it); showTagDialog = false },
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "Move to trash?",
            message = "The note stays in Trash for 30 days before it is removed for good.",
            confirmText = "Move to trash",
            destructive = true,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                OpenTabs.forget(state.noteId)
                viewModel.trash(onBack)
            },
        )
    }

    if (showInfo) {
        NoteInfoDialog(
            state = state,
            strokeCount = viewModel.strokes.size,
            pageCount = viewModel.pageCount,
            onDismiss = { showInfo = false },
        )
    }
}

/**
 * The overflow menu, wired to the view model.
 *
 * Both layouts open the same menu from different corners, so the wiring lives in one place rather
 * than being repeated at each anchor.
 */
@Composable
private fun EditorMenu(
    expanded: Boolean,
    state: EditorUiState,
    viewModel: EditorViewModel,
    context: android.content.Context,
    onDismiss: () -> Unit,
    onColor: () -> Unit,
    onPaper: () -> Unit,
    onTags: () -> Unit,
    onExport: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onOpenNote: (Long) -> Unit,
) {
    EditorOverflowMenu(
        expanded = expanded,
        type = state.type,
        locked = state.locked,
        pinned = state.pinned,
        hasReminder = state.reminderAt != null,
        onDismiss = onDismiss,
        onConvert = { viewModel.convertType(it); onDismiss() },
        onPin = { onDismiss(); viewModel.togglePinned() },
        onColor = { onDismiss(); onColor() },
        onPaper = { onDismiss(); onPaper() },
        onTags = { onDismiss(); onTags() },
        onReminder = {
            onDismiss()
            pickDateTime(context) { millis -> viewModel.setReminder(millis) }
        },
        onClearReminder = { onDismiss(); viewModel.setReminder(null) },
        onLock = { onDismiss(); viewModel.setLocked(!state.locked) },
        onExport = { onDismiss(); onExport() },
        onExportPdf = {
            onDismiss()
            viewModel.export(ExportFormat.PDF, darkStyle = false) { file, mime ->
                context.startActivity(
                    Intent.createChooser(viewModel.shareIntentFor(file, mime), "Export PDF")
                )
            }
        },
        onShareText = {
            onDismiss()
            viewModel.shareAsText { text ->
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        },
                        "Share note",
                    )
                )
            }
        },
        onDuplicate = { onDismiss(); viewModel.duplicate(onOpenNote) },
        onInfo = { onDismiss(); onInfo() },
        onDelete = { onDismiss(); onDelete() },
    )
}
