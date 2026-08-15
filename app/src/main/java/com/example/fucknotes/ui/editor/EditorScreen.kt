package com.example.fucknotes.ui.editor

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.fucknotes.data.model.AttachmentType
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.export.ExportFormat
import com.example.fucknotes.media.AudioPlayer
import com.example.fucknotes.media.AudioRecorder
import com.example.fucknotes.media.MediaStore
import com.example.fucknotes.ui.components.ColorPickerDialog
import com.example.fucknotes.ui.components.ConfirmDialog
import com.example.fucknotes.ui.components.OptionListDialog
import com.example.fucknotes.ui.components.PaperBackground
import com.example.fucknotes.ui.editor.ink.InkCamera
import com.example.fucknotes.ui.editor.ink.InkSurface
import com.example.fucknotes.ui.editor.ink.InkToolbar
import com.example.fucknotes.ui.editor.ink.PaperWhite
import com.example.fucknotes.ui.components.TextPromptDialog
import com.example.fucknotes.ui.components.relativeStamp
import kotlinx.coroutines.launch
import java.util.Calendar

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
    val screenDensity = LocalDensity.current.density
    val camera = remember { InkCamera(screenDensity) }

    /** A handwriting note is presented as a full-bleed page rather than a text form. */
    val isCanvasNote = state.type == NoteType.DRAWING

    // Handed to the ink surface as a stable lambda so committing a stroke does not recompose it.
    val inkStrokes = remember(viewModel) { { viewModel.strokes } }

    var overflow by remember { mutableStateOf(false) }
    var showColor by remember { mutableStateOf(false) }
    var showPaper by remember { mutableStateOf(false) }
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

    if (state.locked && !unlocked) {
        LockedNoteGate(
            onUnlock = { unlocked = true },
            onBack = onBack,
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = {
                        scope.launch {
                            viewModel.discardIfEmpty()
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::togglePinned) {
                        Icon(
                            Icons.Outlined.PushPin,
                            contentDescription = "Pin",
                            tint = if (state.pinned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { viewModel.setInkMode(!state.inkMode) }) {
                        Icon(
                            Icons.Outlined.Brush,
                            contentDescription = "Handwriting",
                            tint = if (state.inkMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton(onClick = { overflow = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        EditorOverflowMenu(
                            expanded = overflow,
                            type = state.type,
                            locked = state.locked,
                            hasReminder = state.reminderAt != null,
                            onDismiss = { overflow = false },
                            onConvert = { viewModel.convertType(it); overflow = false },
                            onColor = { overflow = false; showColor = true },
                            onPaper = { overflow = false; showPaper = true },
                            onTags = { overflow = false; showTagDialog = true },
                            onReminder = {
                                overflow = false
                                pickDateTime(context) { millis -> viewModel.setReminder(millis) }
                            },
                            onClearReminder = { overflow = false; viewModel.setReminder(null) },
                            onLock = { overflow = false; viewModel.setLocked(!state.locked) },
                            onExport = { overflow = false; showExport = true },
                            onShareText = {
                                overflow = false
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
                            onDuplicate = { overflow = false; viewModel.duplicate(onOpenNote) },
                            onInfo = { overflow = false; showInfo = true },
                            onDelete = { overflow = false; showDeleteConfirm = true },
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (!isCanvasNote) {
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
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(
                    if (state.colorArgb != 0) Color(state.colorArgb) else MaterialTheme.colorScheme.background
                )
        ) {
            if (state.inkMode) {
                InkToolbar(
                    pens = state.pens,
                    selectedPen = state.selectedPen,
                    tool = state.tool,
                    zoom = camera.zoom,
                    canUndo = viewModel.canUndoInk,
                    canRedo = viewModel.canRedoInk,
                    fingerDrawing = state.fingerDrawing,
                    onToggleFingerDrawing = viewModel::toggleFingerDrawing,
                    onSelectPen = viewModel::selectPen,
                    onEditPen = viewModel::editPen,
                    onSelectTool = viewModel::setTool,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onResetView = camera::reset,
                )
                HorizontalDivider()
            }

            // A handwriting note is a page, not a form: the title, tags and media bar would all be
            // eating the surface the user came here to write on, so they move into the menu.
            if (!isCanvasNote) {
                TextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    placeholder = { Text("Title", style = MaterialTheme.typography.titleLarge) },
                    textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    singleLine = true,
                    colors = transparentTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Box(Modifier.weight(1f)) {
                // The canvas paints its own paper; text notes keep theirs behind the text.
                val paperUnderText = if (isCanvasNote) PaperStyle.PLAIN else state.paperStyle

                PaperBackground(style = paperUnderText, modifier = Modifier.fillMaxSize()) {
                    when (state.type) {
                        NoteType.CHECKLIST -> ChecklistBody(
                            state = state,
                            onToggle = viewModel::toggleChecklistItem,
                            onEdit = viewModel::updateChecklistItem,
                            onRemove = viewModel::removeChecklistItem,
                            onAdd = { viewModel.addChecklistItem() },
                            onUncheckAll = viewModel::uncheckAll,
                            onDeleteChecked = viewModel::deleteCheckedItems,
                        )

                        NoteType.DRAWING -> Box(Modifier.fillMaxSize())

                        NoteType.TEXT -> Column(Modifier.verticalScroll(rememberScrollState())) {
                            TextField(
                                value = state.body,
                                onValueChange = viewModel::setBody,
                                placeholder = { Text("Start writing…") },
                                colors = transparentTextFieldColors(),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp),
                            )
                            AttachmentStrip(
                                state = state,
                                playingPath = playing,
                                onPlay = { path ->
                                    player.toggle(path) { playing = null }
                                    playing = player.playingPath
                                },
                                onRemove = viewModel::removeAttachment,
                            )
                            Spacer(Modifier.height(80.dp))
                        }
                    }
                }

                // Ink sits above the text so handwriting can annotate typed content.
                InkSurface(
                    strokes = inkStrokes,
                    camera = camera,
                    tool = state.tool,
                    colorArgb = state.activePen.colorArgb,
                    widthDp = state.activePen.widthDp,
                    enabled = state.inkMode,
                    fingerDrawing = state.fingerDrawing,
                    paperStyle = if (isCanvasNote) state.paperStyle else PaperStyle.PLAIN,
                    onStrokesFinished = viewModel::commitStrokes,
                    onErase = viewModel::eraseStrokes,
                    modifier = Modifier.fillMaxSize(),
                    // Only a full-page canvas paints its own sheet; on a text note the ink layer
                    // must stay transparent so the typed content shows through.
                    pageColor = if (isCanvasNote) PaperWhite else Color.Transparent,
                )
            }

            if (!isCanvasNote) {
                if (state.type != NoteType.TEXT) {
                    AttachmentStrip(
                        state = state,
                        playingPath = playing,
                        onPlay = { path ->
                            player.toggle(path) { playing = null }
                            playing = player.playingPath
                        },
                        onRemove = viewModel::removeAttachment,
                    )
                }
                TagStrip(state = state, onAdd = { showTagDialog = true }, onRemove = viewModel::removeTag)
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
        OptionListDialog(
            title = "Paper style",
            options = PaperStyle.entries.map { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            selectedIndex = PaperStyle.entries.indexOf(state.paperStyle),
            onDismiss = { showPaper = false },
            onPick = { viewModel.setPaperStyle(PaperStyle.entries[it]); showPaper = false },
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
                viewModel.trash(onBack)
            },
        )
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Note info") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Words: ${state.wordCount}")
                    Text("Characters: ${state.charCount}")
                    Text("Strokes: ${viewModel.strokes.size}")
                    Text("Attachments: ${state.attachments.size}")
                    Text("Last edited: ${relativeStamp(state.updatedAt)}")
                    state.reminderAt?.let { Text("Reminder: ${relativeStamp(it)}") }
                }
            },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("Close") } },
        )
    }
}

// ---- pieces ----------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun transparentTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

@Composable
private fun ChecklistBody(
    state: EditorUiState,
    onToggle: (String) -> Unit,
    onEdit: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
    onUncheckAll: () -> Unit,
    onDeleteChecked: () -> Unit,
) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
        state.checklist.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.checked, onCheckedChange = { onToggle(item.id) })
                TextField(
                    value = item.text,
                    onValueChange = { onEdit(item.id, it) },
                    placeholder = { Text("List item") },
                    colors = transparentTextFieldColors(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (item.checked) {
                            androidx.compose.ui.text.style.TextDecoration.LineThrough
                        } else null,
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemove(item.id) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove item")
                }
            }
        }
        TextButton(onClick = onAdd, modifier = Modifier.padding(start = 8.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Add item")
        }
        if (state.checklist.any { it.checked }) {
            Row {
                TextButton(onClick = onUncheckAll) { Text("Uncheck all") }
                TextButton(onClick = onDeleteChecked) { Text("Delete checked") }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun AttachmentStrip(
    state: EditorUiState,
    playingPath: String?,
    onPlay: (String) -> Unit,
    onRemove: (com.example.fucknotes.data.db.AttachmentEntity) -> Unit,
) {
    if (state.attachments.isEmpty()) return
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
    ) {
        items(state.attachments, key = { it.id }) { attachment ->
            when (attachment.type) {
                AttachmentType.IMAGE -> Box {
                    Image(
                        painter = rememberAsyncImagePainter(attachment.path),
                        contentDescription = "Attached image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(110.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                    )
                    IconButton(
                        onClick = { onRemove(attachment) },
                        modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                    }
                }

                AttachmentType.AUDIO -> Row(
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onPlay(attachment.path) }) {
                        Icon(
                            if (playingPath == attachment.path) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = "Play recording",
                        )
                    }
                    Text(AudioRecorder.formatDuration(attachment.durationMs))
                    IconButton(onClick = { onRemove(attachment) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove recording")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagStrip(state: EditorUiState, onAdd: () -> Unit, onRemove: (Long) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(state.tags, key = { it.id }) { tag ->
            AssistChip(
                onClick = { onRemove(tag.id) },
                label = { Text("#${tag.name}") },
                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove tag", Modifier.size(16.dp)) },
            )
        }
        item {
            AssistChip(
                onClick = onAdd,
                label = { Text("Add tag") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null, Modifier.size(16.dp)) },
            )
        }
    }
}

@Composable
private fun EditorBottomBar(
    state: EditorUiState,
    onAddImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onRecord: () -> Unit,
    onCancelRecording: () -> Unit,
    onAddChecklistItem: () -> Unit,
) {
    Column {
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onAddImage) { Icon(Icons.Outlined.Image, contentDescription = "Add image") }
            IconButton(onClick = onTakePhoto) { Icon(Icons.Outlined.PhotoCamera, contentDescription = "Take photo") }
            IconButton(onClick = onRecord) {
                Icon(
                    if (state.recording) Icons.Filled.Stop else Icons.Outlined.Mic,
                    contentDescription = if (state.recording) "Stop recording" else "Record voice note",
                    tint = if (state.recording) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.recording) {
                Text(
                    AudioRecorder.formatDuration(state.recordingElapsedMs),
                    style = MaterialTheme.typography.labelLarge,
                )
                TextButton(onClick = onCancelRecording) { Text("Cancel") }
            }
            if (state.type == NoteType.CHECKLIST) {
                IconButton(onClick = onAddChecklistItem) {
                    Icon(Icons.Outlined.CheckBox, contentDescription = "Add list item")
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${state.wordCount} words",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 14.dp),
            )
        }
    }
}

@Composable
private fun EditorOverflowMenu(
    expanded: Boolean,
    type: NoteType,
    locked: Boolean,
    hasReminder: Boolean,
    onDismiss: () -> Unit,
    onConvert: (NoteType) -> Unit,
    onColor: () -> Unit,
    onPaper: () -> Unit,
    onTags: () -> Unit,
    onReminder: () -> Unit,
    onClearReminder: () -> Unit,
    onLock: () -> Unit,
    onExport: () -> Unit,
    onShareText: () -> Unit,
    onDuplicate: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded, onDismissRequest = onDismiss) {
        if (type != NoteType.CHECKLIST) {
            DropdownMenuItem(
                text = { Text("Convert to checklist") },
                leadingIcon = { Icon(Icons.Outlined.CheckBox, null) },
                onClick = { onConvert(NoteType.CHECKLIST) },
            )
        }
        if (type != NoteType.TEXT) {
            DropdownMenuItem(
                text = { Text("Convert to text") },
                leadingIcon = { Icon(Icons.Outlined.Description, null) },
                onClick = { onConvert(NoteType.TEXT) },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Note colour") },
            leadingIcon = { Icon(Icons.Outlined.Palette, null) },
            onClick = onColor,
        )
        DropdownMenuItem(
            text = { Text("Paper style") },
            leadingIcon = { Icon(Icons.Outlined.FormatPaint, null) },
            onClick = onPaper,
        )
        DropdownMenuItem(
            text = { Text("Tags") },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null) },
            onClick = onTags,
        )
        DropdownMenuItem(
            text = { Text(if (hasReminder) "Change reminder" else "Set reminder") },
            leadingIcon = { Icon(Icons.Outlined.Alarm, null) },
            onClick = onReminder,
        )
        if (hasReminder) {
            DropdownMenuItem(text = { Text("Remove reminder") }, onClick = onClearReminder)
        }
        DropdownMenuItem(
            text = { Text(if (locked) "Remove lock" else "Add lock") },
            leadingIcon = { Icon(if (locked) Icons.Outlined.LockOpen else Icons.Outlined.Lock, null) },
            onClick = onLock,
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Export…") },
            leadingIcon = { Icon(Icons.Outlined.UploadFile, null) },
            onClick = onExport,
        )
        DropdownMenuItem(
            text = { Text("Share as text") },
            leadingIcon = { Icon(Icons.Outlined.Share, null) },
            onClick = onShareText,
        )
        DropdownMenuItem(
            text = { Text("Duplicate") },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
            onClick = onDuplicate,
        )
        DropdownMenuItem(
            text = { Text("Note info") },
            leadingIcon = { Icon(Icons.Outlined.Info, null) },
            onClick = onInfo,
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Move to trash") },
            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
            onClick = onDelete,
        )
    }
}

@Composable
private fun ExportDialog(onDismiss: () -> Unit, onExport: (ExportFormat, Boolean) -> Unit) {
    var dark by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export & share") },
        text = {
            Column {
                ExportFormat.entries.forEach { format ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onExport(format, dark) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(format.label, Modifier.weight(1f))
                    }
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { dark = !dark }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = dark, onCheckedChange = { dark = it })
                    Text("Use dark page style")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LockedNoteGate(onUnlock: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("This note is locked", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Unlock with your fingerprint, face or device passcode.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = {
            val activity = context as? androidx.fragment.app.FragmentActivity
            if (activity == null) {
                error = "Unlock is unavailable here."
                return@TextButton
            }
            com.example.fucknotes.lock.BiometricGate.prompt(
                activity = activity,
                title = "Open locked note",
                subtitle = "Confirm it is you",
                onSuccess = onUnlock,
                onFailure = { error = it },
            )
        }) { Text("Unlock") }
        TextButton(onClick = onBack) { Text("Go back") }
    }
}

/** Chained date then time picker, returning the combined epoch millis. */
private fun pickDateTime(context: android.content.Context, onPicked: (Long) -> Unit) {
    val now = Calendar.getInstance()
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val target = Calendar.getInstance().apply {
                        set(year, month, day, hour, minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onPicked(target.timeInMillis)
                },
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                true,
            ).show()
        },
        now.get(Calendar.YEAR),
        now.get(Calendar.MONTH),
        now.get(Calendar.DAY_OF_MONTH),
    ).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
}
