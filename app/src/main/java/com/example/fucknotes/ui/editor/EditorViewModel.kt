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
import com.example.fucknotes.data.db.NoteEntity
import com.example.fucknotes.data.db.TagEntity
import androidx.ink.strokes.Stroke
import com.example.fucknotes.data.ink.DEFAULT_RULE_SPACING_DP
import com.example.fucknotes.data.ink.InkCodec
import com.example.fucknotes.data.ink.InkStroke
import com.example.fucknotes.data.ink.NotebookLayout
import com.example.fucknotes.data.ink.PageSpec
import com.example.fucknotes.data.model.AttachmentType
import com.example.fucknotes.data.model.ChecklistItem
import com.example.fucknotes.data.model.DefaultPenTray
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.data.model.PenPreset
import com.example.fucknotes.export.ExportFormat
import com.example.fucknotes.export.NoteExporter
import com.example.fucknotes.media.AudioRecorder
import com.example.fucknotes.media.MediaStore
import com.example.fucknotes.reminder.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    /** Mirrors the app-wide setting; false means the page only takes stylus input. */
    val fingerDrawing: Boolean = true,
    /** Mirrors the app-wide setting; true pins the page scale against stray pinches. */
    val zoomLocked: Boolean = false,
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
                    fingerDrawing = s.fingerDrawing,
                    zoomLocked = s.zoomLocked,
                    pens = s.penTray,
                    selectedPen = index,
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
                pinned = note.pinned,
                locked = note.locked,
                archived = note.archived,
                reminderAt = note.reminderAt,
                updatedAt = note.updatedAt,
                inkMode = note.type.isInk,
            )
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
        val next = if (_state.value.tool == tool && !tool.isBrush) InkTool.PEN else tool
        _state.value = _state.value.copy(
            tool = if (next.isBrush) _state.value.activePen.tool else next,
        )
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

    /** Turns finger drawing on or off for every note; a finger still pans while it is off. */
    fun toggleFingerDrawing() {
        viewModelScope.launch { settings.setFingerDrawing(!_state.value.fingerDrawing) }
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
        applyStrokes(strokes + finished.map { InkStroke(it, tool) })
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
        val spec = _state.value.page
        var last = 0
        strokes.forEach { inkStroke ->
            val box = inkStroke.stroke.shape.computeBoundingBox() ?: return@forEach
            last = maxOf(last, NotebookLayout.pageIndexAt(box.yMax, spec))
        }
        pageCount = maxOf(last + 1, reachedPages)
    }

    /** The furthest page the user has scrolled to this session. */
    private var reachedPages = 1

    /** Claims one more sheet at the end of the column, and reports its index. */
    fun appendPage(): Int {
        reachedPages = pageCount + 1
        pageCount = reachedPages
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

    fun togglePinned() {
        _state.value = _state.value.copy(pinned = !_state.value.pinned)
        scheduleSave()
    }

    fun setArchived(archived: Boolean) {
        _state.value = _state.value.copy(archived = archived)
        scheduleSave()
    }

    fun setLocked(locked: Boolean) {
        _state.value = _state.value.copy(locked = locked)
        scheduleSave()
    }

    fun setReminder(atMillis: Long?) = viewModelScope.launch {
        _state.value = _state.value.copy(reminderAt = atMillis)
        repo.setReminder(noteId, atMillis)
        val context = getApplication<Application>()
        if (atMillis == null) {
            ReminderScheduler.cancel(context, noteId)
            emit("Reminder removed")
        } else {
            ReminderScheduler.schedule(context, noteId, _state.value.title.ifBlank { "Note reminder" }, atMillis)
            emit("Reminder set")
        }
    }

    // ---- tags --------------------------------------------------------------

    fun addTag(name: String) = viewModelScope.launch { repo.addTagToNote(noteId, name) }

    fun removeTag(tagId: Long) = viewModelScope.launch { repo.removeTagFromNote(noteId, tagId) }

    // ---- attachments -------------------------------------------------------

    fun attachImage(uri: Uri) = viewModelScope.launch {
        val file = MediaStore.importImage(getApplication<Application>(), uri)
        if (file == null) {
            emit("That image could not be read")
            return@launch
        }
        repo.addAttachment(AttachmentEntity(noteId = noteId, path = file.absolutePath, type = AttachmentType.IMAGE))
    }

    fun attachCameraPhoto(file: File) = viewModelScope.launch {
        if (!file.exists() || file.length() == 0L) {
            file.delete()
            return@launch
        }
        val stored = MediaStore.newFile(getApplication<Application>(), "jpg")
        file.copyTo(stored, overwrite = true)
        file.delete()
        repo.addAttachment(AttachmentEntity(noteId = noteId, path = stored.absolutePath, type = AttachmentType.IMAGE))
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
    fun discardIfEmpty() = viewModelScope.launch {
        saveNow()
        val s = _state.value
        val empty = s.title.isBlank() && s.body.isBlank() &&
            s.checklist.none { it.text.isNotBlank() } &&
            strokes.isEmpty() && s.attachments.isEmpty()
        if (empty) repo.deleteForever(listOf(noteId))
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

    suspend fun saveNow() {
        val s = _state.value
        if (!s.loaded) return
        val existing = repo.getNote(noteId) ?: return

        // Encoding compresses every input batch, so only pay for it when the ink actually moved,
        // and never on the main thread.
        if (inkDirty) {
            encodedInk = withContext(Dispatchers.Default) { InkCodec.encode(strokes) }
            inkDirty = false
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
                pinned = s.pinned,
                archived = s.archived,
                locked = s.locked,
            )
        )
    }

    override fun onCleared() {
        recordingTicker?.cancel()
        if (recorder.isRecording) recorder.cancel()
        super.onCleared()
    }

    private companion object {
        const val MAX_UNDO_DEPTH = 60
    }
}
