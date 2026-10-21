package com.example.fucknotes.ui.editor

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fucknotes.ServiceLocator
import com.example.fucknotes.data.db.AttachmentEntity
import com.example.fucknotes.data.db.TagEntity
import androidx.ink.strokes.Stroke
import com.example.fucknotes.data.ink.DEFAULT_RULE_SPACING_DP
import com.example.fucknotes.data.ink.InkCodec
import com.example.fucknotes.data.ink.InkStroke
import com.example.fucknotes.data.ink.PageSpec
import com.example.fucknotes.data.model.AttachmentType
import com.example.fucknotes.data.model.ChecklistItem
import com.example.fucknotes.data.model.DefaultPenTray
import com.example.fucknotes.data.model.FingerInputMode
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.MAX_NOTEBOOK_PAGES
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.data.model.PenPreset
import com.example.fucknotes.data.model.PenButton
import com.example.fucknotes.data.model.PenButtonAction
import com.example.fucknotes.export.ExportFormat
import com.example.fucknotes.export.NoteExporter
import com.example.fucknotes.media.AudioRecorder
import com.example.fucknotes.media.MediaStore
import com.example.fucknotes.reminder.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class EditorUiState(
    val loaded: Boolean = false,
    val noteId: Long = 0,
    val title: String = "",
    val body: String = "",
    val type: NoteType = NoteType.TEXT,
    val checklist: List<ChecklistItem> = emptyList(),
    val paperStyle: PaperStyle = PaperStyle.PLAIN,
    val colorArgb: Int = 0,
    /** 0 keeps the default rule tone. */
    val lineColorArgb: Int = 0,
    /** 0 keeps the default rule spacing. */
    val lineSpacingDp: Int = 0,
    /** Sheet size and direction; only a notebook has sheets to size. */
    val page: PageSpec = PageSpec.Default,
    val pinned: Boolean = false,
    val locked: Boolean = false,
    val archived: Boolean = false,
    val reminderAt: Long? = null,
    val updatedAt: Long = 0,
    val attachments: List<AttachmentEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    // Ink editing state
    val inkMode: Boolean = false,
    val tool: InkTool = InkTool.PEN,
    val pens: List<PenPreset> = DefaultPenTray,
    val selectedPen: Int = 0,
    /** Mirrors the app-wide finger mode without changing the selected stylus tool. */
    val fingerInputMode: FingerInputMode = FingerInputMode.DRAW,
    /** Mirrors the app-wide setting; true pins the page scale against stray pinches. */
    val zoomLocked: Boolean = false,
    /** Actions assigned to the physical Xiaomi Pen controls. */
    val penPrimaryAction: PenButtonAction = PenButtonAction.TOGGLE_ERASER,
    val penSecondaryAction: PenButtonAction = PenButtonAction.UNDO,
    val penTertiaryAction: PenButtonAction = PenButtonAction.REDO,
    val recording: Boolean = false,
    val recordingElapsedMs: Long = 0,
    val message: String? = null,
) {
    /**
     * Whether the writing surface takes input.
     *
     * A canvas or notebook note is nothing but its ink, so writing is never off there whatever the
     * layered-ink toggle says; on a text note it follows the toggle.
     */
    val inkActive: Boolean get() = inkMode || type.isInk

    /** The pen the brush tools draw with. Falls back to the first slot if an index goes stale. */
    val activePen: PenPreset get() = pens.getOrElse(selectedPen) { DefaultPenTray.first() }
    /** The rule spacing to draw with, in world units. */
    val ruleSpacingDp: Float get() = lineSpacingDp.takeIf { it > 0 }?.toFloat() ?: DEFAULT_RULE_SPACING_DP

    val wordCount: Int get() = body.split(Regex("\\s+")).count { it.isNotBlank() }
    val charCount: Int get() = body.length
}

class EditorViewModel(app: Application, private val noteId: Long) : AndroidViewModel(app) {

    private val repo = ServiceLocator.notes(app)
    private val settings = ServiceLocator.settings(app)
    private val exporter = NoteExporter(app, repo)
    private val recorder = AudioRecorder(app)

    private val _state = MutableStateFlow(EditorUiState(noteId = noteId))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    /**
     * Ink lives in Compose snapshot state rather than in [state].
     *
     * `InProgressStrokes` hands finished strokes back on the UI thread and requires them to be
     * rendered by the dry layer in that same run loop. Routing them through a [StateFlow] adds a
     * collect hop, so for a frame the stroke belongs to neither layer and the page blinks. Writing
     * snapshot state directly in the callback keeps the handoff seamless.
     */
    var strokes: List<InkStroke> by mutableStateOf(emptyList())
        private set

    var canUndoInk: Boolean by mutableStateOf(false)
        private set

    var canRedoInk: Boolean by mutableStateOf(false)
        private set

    /**
     * How many sheets the notebook currently holds.
     *
     * Pages are implicit — a notebook is as long as its ink reaches — so this is derived from the
     * stroke bounds rather than stored, and kept as snapshot state next to [strokes] so the page
     * counter updates in the same frame a stroke lands on a new sheet.
     */
    // Pages are created explicitly from the page controls. Deriving this from stroke bounds makes
    // camera settling or legacy screen coordinates create phantom pages on an empty notebook.
    var pageCount: Int by mutableStateOf(1)
        private set

    /**
     * Undo history as whole-page snapshots.
     *
     * Snapshots share their [InkStroke] elements, so a step costs one list header rather than a
     * copy of the ink; that buys correct undo for erases and clears, not just for the last stroke.
     */
    private val undoStack = ArrayDeque<List<InkStroke>>()
    private val redoStack = ArrayDeque<List<InkStroke>>()

    private val saveMutex = Mutex()
    private var saveJob: Job? = null
    private var penSaveJob: Job? = null
    private var recordingTicker: Job? = null

    /** Set once the app-wide tray has arrived, so only the first copy may choose the tool. */
    private var trayRestored = false

    /** Set when the ink changed, so an autosave triggered by typing does not re-encode the page. */
    private var inkDirty = false
    private var encodedInk = ""

    init {
        // Stylus-only is a property of the user's hardware, and the pen tray of the pens the user
        // set up — neither belongs to one note, so both are kept app-wide and observed rather than
        // copied in once. A copy that matches what is already here is dropped by the StateFlow.
        viewModelScope.launch {
            settings.settings.collect { s ->
                val current = _state.value
                val index = s.selectedPen.coerceIn(0, s.penTray.lastIndex.coerceAtLeast(0))
                _state.value = current.copy(
                    fingerInputMode = s.fingerInputMode,
                    zoomLocked = s.zoomLocked,
                    pens = s.penTray,
                    selectedPen = index,
                    penPrimaryAction = s.penPrimaryAction,
                    penSecondaryAction = s.penSecondaryAction,
                    penTertiaryAction = s.penTertiaryAction,
                    // The restored slot picks the tool once, as the note opens. After that the
                    // user's current tool stands: a later tray write must not take the eraser out
                    // of their hand mid-page.
                    tool = if (trayRestored) current.tool
                    else s.penTray.getOrNull(index)?.tool ?: current.tool,
                )
                trayRestored = true
            }
        }

        viewModelScope.launch {
            val note = repo.getNote(noteId) ?: return@launch
            encodedInk = note.drawingJson
            strokes = withContext(Dispatchers.Default) { InkCodec.decode(note.drawingJson) }
            _state.value = _state.value.copy(
                loaded = true,
                title = note.title,
                body = note.body,
                type = note.type,
                checklist = repo.checklistOf(note),
                paperStyle = note.paperStyle,
                colorArgb = note.colorArgb,
                lineColorArgb = note.lineColorArgb,
                lineSpacingDp = note.lineSpacingDp,
                page = PageSpec.decode(note.pageFormat),
                pinned = false,
                locked = false,
                archived = note.archived,
                reminderAt = null,
                updatedAt = note.updatedAt,
                inkMode = note.type.isInk,
            )
            reachedPages = note.pageCount.coerceIn(1, MAX_NOTEBOOK_PAGES)
            pagesExplicitlyCreated = note.pagesExplicitlyCreated
            recountPages()
        }
        repo.observeAttachments(noteId)
            .onEach { list -> _state.value = _state.value.copy(attachments = list) }
            .launchIn(viewModelScope)
        repo.observeTagsForNote(noteId)
            .onEach { list -> _state.value = _state.value.copy(tags = list) }
            .launchIn(viewModelScope)
    }

    // ---- text --------------------------------------------------------------

    fun setTitle(value: String) {
        _state.value = _state.value.copy(title = value)
        scheduleSave()
    }

    fun setBody(value: String) {
        _state.value = _state.value.copy(body = value)
        scheduleSave()
    }

    // ---- checklist ---------------------------------------------------------

    fun addChecklistItem(text: String = "") {
        val item = ChecklistItem(id = UUID.randomUUID().toString(), text = text)
        _state.value = _state.value.copy(checklist = _state.value.checklist + item)
        scheduleSave()
    }

    fun updateChecklistItem(id: String, text: String) {
        _state.value = _state.value.copy(
            checklist = _state.value.checklist.map { if (it.id == id) it.copy(text = text) else it }
        )
        scheduleSave()
    }

    fun toggleChecklistItem(id: String) {
        _state.value = _state.value.copy(
            checklist = _state.value.checklist.map { if (it.id == id) it.copy(checked = !it.checked) else it }
        )
        scheduleSave()
    }

    fun removeChecklistItem(id: String) {
        _state.value = _state.value.copy(checklist = _state.value.checklist.filterNot { it.id == id })
        scheduleSave()
    }

    fun moveChecklistItem(from: Int, to: Int) {
        val items = _state.value.checklist.toMutableList()
        if (from !in items.indices || to !in items.indices) return
        items.add(to, items.removeAt(from))
        _state.value = _state.value.copy(checklist = items)
        scheduleSave()
    }

    fun uncheckAll() {
        _state.value = _state.value.copy(checklist = _state.value.checklist.map { it.copy(checked = false) })
        scheduleSave()
    }

    fun deleteCheckedItems() {
        _state.value = _state.value.copy(checklist = _state.value.checklist.filterNot { it.checked })
        scheduleSave()
    }

    /** Turns a text note into a checklist by splitting on newlines, and back by joining them. */
    fun convertType(target: NoteType) {
        val s = _state.value
        when {
            s.type == target -> return

            target == NoteType.CHECKLIST -> _state.value = s.copy(
                type = target,
                checklist = s.body.lines().filter { it.isNotBlank() }.map {
                    ChecklistItem(UUID.randomUUID().toString(), it.trim())
                },
                body = "",
                inkMode = false,
            )

            target == NoteType.TEXT -> _state.value = s.copy(
                type = target,
                body = if (s.type == NoteType.CHECKLIST) s.checklist.joinToString("\n") { it.text } else s.body,
                checklist = emptyList(),
                inkMode = false,
            )

            // Canvas and notebook differ only in how the page is laid out, so switching between
            // them keeps the ink exactly where it is.
            target.isInk -> _state.value = s.copy(type = target, inkMode = true)
        }
        scheduleSave()
    }

    // ---- ink ---------------------------------------------------------------

    fun setInkMode(on: Boolean) {
        _state.value = _state.value.copy(inkMode = on)
    }

    fun setTool(tool: InkTool) {
        // Tapping an active eraser or pan button a second time returns to the last pen.
        val next = if (_state.value.tool == tool && !tool.isBrush) _state.value.activePen.tool else tool
        if (next.isBrush) {
            // A tool shortcut (including a Xiaomi Pen button) should also move the tray selection
            // to a slot of that family. Otherwise a Marker action would leave the blue Pen slot
            // highlighted and silently use its colour and width.
            val index = _state.value.pens.indexOfFirst { it.tool == next }
            if (index >= 0) {
                _state.value = _state.value.copy(selectedPen = index, tool = next)
                viewModelScope.launch { settings.setSelectedPen(index) }
            } else {
                _state.value = _state.value.copy(tool = _state.value.activePen.tool)
            }
        } else {
            _state.value = _state.value.copy(tool = next)
        }
    }

    /**
     * The double-tap swap: eraser out, then the pen back.
     *
     * Unlike [setTool] this returns to the pen actually in the tray slot rather than to a plain
     * pen, so a double tap in the middle of a highlighter pass gives the highlighter back.
     */
    fun toggleEraser() {
        val s = _state.value
        _state.value = s.copy(tool = if (s.tool == InkTool.ERASER) s.activePen.tool else InkTool.ERASER)
    }

    /** Applies the action assigned to a physical Xiaomi Pen button. */
    fun handlePenButton(button: PenButton) {
        if (!_state.value.inkActive) return
        val current = _state.value
        val action = when (button) {
            PenButton.PRIMARY -> current.penPrimaryAction
            PenButton.SECONDARY -> current.penSecondaryAction
            PenButton.TERTIARY -> current.penTertiaryAction
        }
        when (action) {
            PenButtonAction.NONE -> Unit
            PenButtonAction.TOGGLE_ERASER -> toggleEraser()
            PenButtonAction.UNDO -> undo()
            PenButtonAction.REDO -> redo()
            PenButtonAction.PEN -> setTool(InkTool.PEN)
            PenButtonAction.MARKER -> setTool(InkTool.MARKER)
            PenButtonAction.HIGHLIGHTER -> setTool(InkTool.HIGHLIGHTER)
            PenButtonAction.PAN -> setTool(InkTool.PAN)
        }
    }

    /** Sets touch behavior independently of the pen/eraser, and restores a brush after legacy pan. */
    fun setFingerInputMode(mode: FingerInputMode) {
        val current = _state.value
        _state.value = current.copy(
            fingerInputMode = mode,
            tool = if (current.tool == InkTool.PAN) current.activePen.tool else current.tool,
        )
        viewModelScope.launch { settings.setFingerInputMode(mode) }
    }

    /** Pins or releases the page scale for every ink note. */
    fun toggleZoomLock() {
        viewModelScope.launch { settings.setZoomLocked(!_state.value.zoomLocked) }
    }

    fun selectPen(index: Int) {
        val pen = _state.value.pens.getOrNull(index) ?: return
        _state.value = _state.value.copy(selectedPen = index, tool = pen.tool)
        viewModelScope.launch { settings.setSelectedPen(index) }
    }

    /**
     * Edits a tray slot in place; the slot keeps its own colour and nib between switches, and now
     * between sessions too.
     *
     * State moves at once so the next stroke uses the new nib, while the store write is debounced —
     * a nib slider fires this on every frame of the drag, and each write is a whole-file rewrite.
     */
    fun editPen(index: Int, colorArgb: Int, widthDp: Float) {
        val pens = _state.value.pens.toMutableList()
        val pen = pens.getOrNull(index) ?: return
        pens[index] = pen.copy(colorArgb = colorArgb, widthDp = widthDp)
        _state.value = _state.value.copy(pens = pens)
        penSaveJob?.cancel()
        penSaveJob = viewModelScope.launch {
            delay(300)
            settings.setPenTray(pens)
        }
    }

    /**
     * Ink hands back every stroke that finished together. They must land in state during this call
     * so the dry layer paints them in the same frame the wet layer drops them — otherwise the
     * stroke flickers.
     */
    fun commitStrokes(finished: List<Stroke>) {
        if (finished.isEmpty()) return
        val tool = _state.value.tool.takeIf { it.isBrush } ?: _state.value.activePen.tool
        pushUndo()
        applyStrokes(strokes + finished.map { InkStroke(it, InkCodec.toolForStroke(it, tool)) })
    }

    /** The eraser removes whole strokes it touches, which is what suits a note-sized page. */
    fun eraseStrokes(indices: Set<Int>) {
        if (indices.isEmpty()) return
        pushUndo()
        applyStrokes(strokes.filterIndexed { index, _ -> index !in indices })
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(strokes)
        applyStrokes(previous)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(strokes)
        applyStrokes(next)
    }

    fun clearInk() {
        if (strokes.isEmpty()) return
        pushUndo()
        applyStrokes(emptyList())
    }

    private fun pushUndo() {
        undoStack.addLast(strokes)
        while (undoStack.size > MAX_UNDO_DEPTH) undoStack.removeFirst()
        redoStack.clear()
    }

    /**
     * Applied synchronously: this runs inside the `onStrokesFinished` callback, and the dry layer
     * has to see the new stroke during the very same frame.
     */
    private fun applyStrokes(next: List<InkStroke>) {
        strokes = next
        canUndoInk = undoStack.isNotEmpty()
        canRedoInk = redoStack.isNotEmpty()
        recountPages()
        inkDirty = true
        scheduleSave()
    }

    /**
     * Recomputes the sheet count from where the ink actually reaches.
     *
     * [reachedPages] lets the editor claim a page the user has scrolled to but not yet written on,
     * so "new page" produces a sheet that stays until the note is closed empty.
     */
    private fun recountPages() {
        pageCount = reachedPages.coerceAtLeast(1)
    }

    /** The furthest page the user has scrolled to this session. */
    private var reachedPages = 1
    private var pagesExplicitlyCreated = false

    /** Claims one more sheet at the end of the column, and reports its index. */
    fun appendPage(): Int {
        reachedPages = maxOf(reachedPages, (pageCount + 1).coerceAtMost(MAX_NOTEBOOK_PAGES))
        pagesExplicitlyCreated = true
        pageCount = reachedPages
        // Page creation is an explicit user action. Persist it immediately in the process scope so
        // a quick back/navigation cannot cancel the normal 400 ms editor autosave and lose sheets.
        flushPendingSave()
        scheduleSave()
        return pageCount - 1
    }

    // ---- note properties ---------------------------------------------------

    fun setPaperStyle(style: PaperStyle) {
        _state.value = _state.value.copy(paperStyle = style)
        scheduleSave()
    }

    /** Rule tone. 0 restores the default grey. */
    fun setLineColor(colorArgb: Int) {
        _state.value = _state.value.copy(lineColorArgb = colorArgb)
        scheduleSave()
    }

    /** Rule spacing in points. 0 restores the default. */
    fun setLineSpacing(spacingDp: Int) {
        _state.value = _state.value.copy(lineSpacingDp = spacingDp)
        scheduleSave()
    }

    /**
     * Resizes the sheets.
     *
     * The ink does not move: strokes are stored in page coordinates, so a taller sheet simply takes
     * in more of the column and a shorter one pushes the tail of a page over the fold. Nothing is
     * rescaled, which is the only behaviour that keeps handwriting the size it was written.
     */
    fun setPageSpec(spec: PageSpec) {
        _state.value = _state.value.copy(page = spec)
        recountPages()
        scheduleSave()
    }

    fun setColor(color: Int) {
        _state.value = _state.value.copy(colorArgb = color)
        scheduleSave()
    }

    // ---- tags --------------------------------------------------------------

    fun addTag(name: String) = viewModelScope.launch { repo.addTagToNote(noteId, name) }

    fun removeTag(tagId: Long) = viewModelScope.launch { repo.removeTagFromNote(noteId, tagId) }

    // ---- attachments -------------------------------------------------------

    fun attachImage(uri: Uri, pageIndex: Int = 0) = viewModelScope.launch {
        importPageImage(uri, pageIndex)
    }

    private suspend fun importPageImage(uri: Uri, pageIndex: Int) {
        val file = MediaStore.importImage(getApplication<Application>(), uri)
        if (file == null) {
            emit("That image could not be read")
            return
        }
        try {
            val s = _state.value
            val attachment = if (s.type.isInk) {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
                }
                com.example.fucknotes.media.pageImage(noteId, file,
                    if (s.type == NoteType.NOTEBOOK) pageIndex.coerceIn(0, pageCount - 1) else 0,
                    s.page, bounds.outWidth, bounds.outHeight)
            } else AttachmentEntity(noteId = noteId, path = file.absolutePath, type = AttachmentType.IMAGE)
            // Finish the database handoff before cancellation can trigger file cleanup.
            withContext(NonCancellable) { repo.addAttachment(attachment) }
            emit("Image added")
        } catch (error: Exception) {
            file.delete()
            if (error is kotlinx.coroutines.CancellationException) throw error
            emit("Image could not be added: ${error.message}")
        }
    }

    fun attachCameraPhoto(file: File, pageIndex: Int = 0) = viewModelScope.launch {
        try {
            // Camera images need the same EXIF correction, bounded decoding and placement as
            // gallery images; keeping the original JPEG bypassed all three.
            importPageImage(Uri.fromFile(file), pageIndex)
        } finally { file.delete() }
    }

    fun updatePageImage(image: AttachmentEntity) = viewModelScope.launch {
        if (image.noteId != noteId || image.pageIndex == null) return@launch
        repo.updateAttachment(image)
    }

    fun removeAttachment(attachment: AttachmentEntity) = viewModelScope.launch {
        repo.deleteAttachment(attachment)
    }

    // ---- voice notes -------------------------------------------------------

    fun startRecording() {
        if (recorder.isRecording) return
        if (!recorder.start()) {
            emit("Recording could not start")
            return
        }
        _state.value = _state.value.copy(recording = true, recordingElapsedMs = 0)
        recordingTicker = viewModelScope.launch {
            while (recorder.isRecording) {
                delay(200)
                _state.value = _state.value.copy(recordingElapsedMs = _state.value.recordingElapsedMs + 200)
            }
        }
    }

    fun stopRecording() = viewModelScope.launch {
        recordingTicker?.cancel()
        val result = recorder.stop()
        _state.value = _state.value.copy(recording = false, recordingElapsedMs = 0)
        if (result == null) {
            emit("Nothing was recorded")
            return@launch
        }
        repo.addAttachment(
            AttachmentEntity(
                noteId = noteId,
                path = result.file.absolutePath,
                type = AttachmentType.AUDIO,
                durationMs = result.durationMs,
            )
        )
    }

    fun cancelRecording() {
        recordingTicker?.cancel()
        recorder.cancel()
        _state.value = _state.value.copy(recording = false, recordingElapsedMs = 0)
    }

    // ---- export ------------------------------------------------------------

    fun export(format: ExportFormat, darkStyle: Boolean, onReady: (File, String) -> Unit) =
        viewModelScope.launch {
            saveNow()
            val note = repo.getNote(noteId) ?: return@launch
            runCatching { exporter.export(note, format, darkStyle) }
                .onSuccess { onReady(it, format.mime) }
                .onFailure { emit("Export failed: ${it.message}") }
        }

    fun shareIntentFor(file: File, mime: String) = exporter.shareIntent(file, mime)

    fun shareAsText(onReady: (String) -> Unit) = viewModelScope.launch {
        saveNow()
        val note = repo.getNote(noteId) ?: return@launch
        onReady(buildString {
            if (note.title.isNotBlank()) appendLine(note.title).appendLine()
            append(repo.plainTextOf(note))
        })
    }

    // ---- lifecycle ---------------------------------------------------------

    fun duplicate(onDone: (Long) -> Unit) = viewModelScope.launch {
        saveNow()
        repo.duplicate(noteId)?.let(onDone)
    }

    fun trash(onDone: () -> Unit) = viewModelScope.launch {
        ReminderScheduler.cancel(getApplication<Application>(), noteId)
        repo.trash(listOf(noteId))
        onDone()
    }

    /** Drops an untouched note so backing out of "new note" does not litter the list. */
    suspend fun discardIfEmpty() {
        saveNow()
        val s = _state.value
        val empty = s.title.isBlank() && s.body.isBlank() &&
            s.checklist.none { it.text.isNotBlank() } &&
            strokes.isEmpty() && s.attachments.isEmpty()
        if (s.loaded && empty && !pagesExplicitlyCreated) repo.deleteForever(listOf(noteId))
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun emit(text: String) {
        _state.value = _state.value.copy(message = text)
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            saveNow()
        }
    }

    fun flushPendingSave() {
        saveJob?.cancel()
        ServiceLocator.persistenceScope().launch {
            runCatching { saveNow() }.onFailure {
                android.util.Log.e("NotesSave", "Could not save note $noteId", it)
            }
        }
    }

    suspend fun saveNow() = withContext(NonCancellable + Dispatchers.Main.immediate) {
        saveMutex.withLock {
            val s = _state.value
            if (!s.loaded) return@withLock
            val snapshot = strokes
            val snapshotPageCount = pageCount
            val snapshotExplicitPages = pagesExplicitlyCreated
            val existing = repo.getNote(noteId) ?: return@withLock

            // Encoding compresses every input batch, so only pay for it when the ink actually moved,
            // and never on the main thread.
            if (inkDirty) {
                encodedInk = withContext(Dispatchers.Default) { InkCodec.encode(snapshot) }
                inkDirty = strokes !== snapshot
            }

            repo.save(
                existing.copy(
                    title = s.title,
                    body = s.body,
                    type = s.type,
                    checklistJson = repo.encodeChecklist(s.checklist),
                    drawingJson = encodedInk,
                    paperStyle = s.paperStyle,
                    colorArgb = s.colorArgb,
                    lineColorArgb = s.lineColorArgb,
                    lineSpacingDp = s.lineSpacingDp,
                    pageFormat = s.page.encode(),
                    pageCount = snapshotPageCount.coerceAtLeast(1),
                    pagesExplicitlyCreated = snapshotExplicitPages,
                    pinned = s.pinned,
                    archived = s.archived,
                    locked = s.locked,
                )
            )
        }
    }

    override fun onCleared() {
        flushPendingSave()
        recordingTicker?.cancel()
        if (recorder.isRecording) recorder.cancel()
        super.onCleared()
    }

    private companion object {
        const val MAX_UNDO_DEPTH = 60
    }
}
