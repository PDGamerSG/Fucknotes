package com.example.fucknotes.drive

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.fucknotes.data.db.AppDatabase
import com.example.fucknotes.data.db.DriveFileEntity
import com.example.fucknotes.data.db.DriveFolderEntity
import com.example.fucknotes.data.db.FolderEntity
import com.example.fucknotes.data.repo.NotesRepository
import com.example.fucknotes.data.repo.SettingsRepository
import com.example.fucknotes.export.EditablePdf
import com.example.fucknotes.export.NoteExporter
import com.example.fucknotes.media.NoteRestorer
import com.example.fucknotes.media.PdfImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

/**
 * Keeps a folder in the user's Google Drive that mirrors the shelf.
 *
 * `Fucknotes/` holds one subfolder per collection — the folder tree as it is in the rail, plus
 * "Unfiled" and "Archive" — and in each, one PDF per note. Every PDF is an editable export: it
 * shows the pages in any reader, and it carries the note inside, so pulling it back into the app
 * (here, on another device, after a reinstall) gives the note back with its ink to erase and
 * change rather than a flat picture.
 *
 * Sync is one-way by default, shelf to Drive, and incremental: a note is uploaded again only
 * when it changed since its last upload. "Restore from Drive" is the way back: it brings in
 * every note in the folder that is not already on this shelf.
 */
class DriveSync(
    private val context: Context,
    private val db: AppDatabase,
    private val repo: NotesRepository,
    private val settings: SettingsRepository,
    private val auth: DriveAuth,
) {
    enum class Phase { IDLE, RUNNING, ERROR }

    data class Status(
        val phase: Phase = Phase.IDLE,
        val message: String = "",
        val done: Int = 0,
        val total: Int = 0,
    )

    /** What a sign-in attempt came back with. */
    sealed class SignIn {
        data class Done(val email: String) : SignIn()
        data class Consent(val intent: PendingIntent) : SignIn()
        data class Failed(val message: String) : SignIn()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val api = DriveApi { force -> auth.token(force) }
    private val exporter by lazy { NoteExporter(context, repo) }
    private val restorer by lazy { NoteRestorer(context, repo, settings) }

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    private var pendingChange: Job? = null

    init {
        watchShelf()
    }

    // ---- account ----------------------------------------------------------------------------------

    /** Starts the sign-in: silent when Google already agreed, otherwise a consent sheet to show. */
    suspend fun signIn(): SignIn = try {
        when (val access = auth.request()) {
            is DriveAuth.Access.NeedsConsent -> SignIn.Consent(access.intent)
            is DriveAuth.Access.Granted -> SignIn.Done(rememberAccount())
        }
    } catch (error: Exception) {
        SignIn.Failed(describe(error))
    }

    /** Finishes the sign-in after the consent sheet; null when the user backed out. */
    suspend fun finishSignIn(data: Intent?): SignIn {
        auth.fromConsent(data) ?: return SignIn.Failed("Google Drive access was not granted")
        return try { SignIn.Done(rememberAccount()) } catch (error: Exception) { SignIn.Failed(describe(error)) }
    }

    private suspend fun rememberAccount(): String {
        val user = api.about()
        settings.setDriveAccount(user.emailAddress.ifBlank { "Google account" }, user.displayName)
        syncInBackground()
        return user.emailAddress
    }

    suspend fun signOut() {
        pendingChange?.cancel()
        auth.forget()?.let { api.revoke(it) }
        settings.clearDriveAccount()
        db.driveDao().clearFiles()
        db.driveDao().clearFolders()
        _status.value = Status()
    }

    /** The app's folder in the Drive web app, once it exists. */
    suspend fun folderUrl(): String? =
        settings.settings.first().driveRootId.takeIf { it.isNotBlank() }?.let { "https://drive.google.com/drive/folders/$it" }

    // ---- triggers ---------------------------------------------------------------------------------

    fun syncInBackground() {
        scope.launch { runCatching { syncNow() } }
    }

    /** On app start: a sync when signed in, auto sync is on, and the last one is old enough. */
    fun syncIfDue() {
        scope.launch {
            val s = settings.settings.first()
            if (!s.driveSignedIn || !s.driveAutoSync) return@launch
            if (System.currentTimeMillis() - s.driveLastSyncAt < MIN_AUTO_INTERVAL_MS) return@launch
            runCatching { syncNow() }
        }
    }

    /** Every note write lands here; a sync follows once the shelf has been quiet for a moment. */
    @OptIn(FlowPreview::class)
    private fun watchShelf() {
        db.driveDao().observeNoteStamp()
            .distinctUntilChanged()
            .drop(1)
            .debounce(CHANGE_DEBOUNCE_MS)
            .onEach {
                val s = settings.settings.first()
                if (s.driveSignedIn && s.driveAutoSync) runCatching { syncNow() }
            }
            .launchIn(scope)
    }

    // ---- the sync -----------------------------------------------------------------------------------

    /** Uploads what changed. Throws [DriveAuth.ConsentRequiredException] when Google wants the user. */
    suspend fun syncNow() {
        if (!settings.settings.first().driveSignedIn) return
        if (!mutex.tryLock()) return
        try {
            _status.value = Status(Phase.RUNNING, "Checking Drive…")
            val folderIds = ensureFolders()
            val notes = db.noteDao().allNotes()
            val local = notes.map {
                SyncPlanner.LocalNote(it.id, it.title, it.updatedAt, it.folderId, it.archived, it.deletedAt != null)
            }
            val mappings = db.driveDao().allFiles().map {
                SyncPlanner.Mapping(it.noteId, it.fileId, it.folderKey, it.name, it.syncedUpdatedAt)
            }
            val actions = SyncPlanner.plan(local, mappings)
            val byId = notes.associateBy { it.id }
            val mappingById = db.driveDao().allFiles().associateBy { it.noteId }.toMutableMap()
            actions.forEachIndexed { index, action ->
                val note = byId[action.noteId]
                val label = note?.title?.ifBlank { "Untitled" } ?: "note"
                _status.value = Status(Phase.RUNNING, describe(action, label), index, actions.size)
                when (action) {
                    is SyncPlanner.Action.Upload -> {
                        val entity = note ?: return@forEachIndexed
                        val pdf = exporter.exportEditablePdf(entity)
                        try {
                            val parent = folderIds.getValue(action.folderKey)
                            val properties = mapOf(
                                PROP_KIND to KIND_NOTE, PROP_NOTE_ID to entity.id.toString(),
                                PROP_INSTALL to settings.installId(), PROP_UPDATED to entity.updatedAt.toString(),
                            )
                            // A file the user removed from Drive is simply made again.
                            val existing = action.existingFileId?.takeIf { api.get(it)?.trashed == false }
                            val uploaded = api.upload(pdf, DriveApi.MIME_PDF, action.name, parent, properties, existing)
                            val previous = mappingById[entity.id]
                            if (existing != null && previous != null && previous.folderKey != action.folderKey) {
                                api.update(uploaded.id, addParent = parent, removeParent = folderIds[previous.folderKey])
                            }
                            val record = DriveFileEntity(entity.id, uploaded.id, action.folderKey, action.name, entity.updatedAt, System.currentTimeMillis())
                            db.driveDao().upsertFile(record)
                            mappingById[entity.id] = record
                        } finally { pdf.parentFile?.deleteRecursively() }
                    }
                    is SyncPlanner.Action.Move -> {
                        val previous = mappingById[action.noteId] ?: return@forEachIndexed
                        if (previous.folderKey == action.toKey) return@forEachIndexed // the upload above already moved it
                        api.update(action.fileId, addParent = folderIds.getValue(action.toKey), removeParent = folderIds[action.fromKey])
                        val record = previous.copy(folderKey = action.toKey)
                        db.driveDao().upsertFile(record)
                        mappingById[action.noteId] = record
                    }
                    is SyncPlanner.Action.Rename -> {
                        val previous = mappingById[action.noteId] ?: return@forEachIndexed
                        api.update(action.fileId, name = action.name)
                        db.driveDao().upsertFile(previous.copy(name = action.name))
                    }
                    is SyncPlanner.Action.Trash -> {
                        runCatching { api.update(action.fileId, trashed = true) }
                        db.driveDao().deleteFile(action.noteId)
                        mappingById.remove(action.noteId)
                    }
                }
            }
            settings.setDriveLastSync(System.currentTimeMillis())
            _status.value = Status(Phase.IDLE, "", actions.size, actions.size)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            val message = describe(error)
            settings.setDriveLastError(message)
            _status.value = Status(Phase.ERROR, message)
            throw error
        } finally { mutex.unlock() }
    }

    /**
     * Brings every note in the Drive folder that is not on this shelf back as an editable note,
     * filed under the same collection names. Returns how many arrived.
     */
    suspend fun restoreFromDrive(): Int {
        if (!settings.settings.first().driveSignedIn) return 0
        mutex.withLock {
            try {
                _status.value = Status(Phase.RUNNING, "Looking in Drive…")
                val rootId = ensureRoot()
                var count = 0
                suspend fun walk(folderId: String, path: List<String>) {
                    api.children(folderId).forEach { child ->
                        when {
                            child.isFolder -> walk(child.id, path + child.name)
                            child.mimeType == DriveApi.MIME_PDF -> if (bringIn(child, path)) count++
                        }
                    }
                }
                walk(rootId, emptyList())
                _status.value = Status(Phase.IDLE)
                return count
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _status.value = Status(Phase.ERROR, describe(error))
                throw error
            }
        }
    }

    private suspend fun bringIn(file: DriveApi.DriveFile, path: List<String>): Boolean {
        if (db.driveDao().fileByDriveId(file.id) != null) return false
        _status.value = Status(Phase.RUNNING, "Restoring ${file.name}…")
        val archived = path.firstOrNull() == SyncPlanner.NAME_ARCHIVE
        val localPath = when (path.firstOrNull()) {
            SyncPlanner.NAME_UNFILED, SyncPlanner.NAME_ARCHIVE -> path.drop(1)
            else -> path
        }
        val folderId = repo.folderForPath(localPath)
        val download = File(context.cacheDir, "drive-${UUID.randomUUID()}.pdf")
        try {
            api.download(file.id, download)
            val parts = EditablePdf.read(download)
            val noteId = if (NoteRestorer.payloadOf(parts) != null) {
                restorer.restore(parts, folderId, archived).noteId
            } else {
                // A PDF someone dropped into the folder by hand: pages as backgrounds, ink on top.
                PdfImporter(context, repo).import(android.net.Uri.fromFile(download), folderId)
            }
            val note = repo.getNote(noteId) ?: return false
            db.driveDao().upsertFile(
                DriveFileEntity(noteId, file.id, SyncPlanner.folderKey(note.folderId, note.archived), file.name, note.updatedAt, System.currentTimeMillis())
            )
            return true
        } finally { download.delete() }
    }

    // ---- folders ------------------------------------------------------------------------------------

    private suspend fun ensureRoot(): String {
        val stored = settings.settings.first().driveRootId
        if (stored.isNotBlank()) {
            api.get(stored)?.takeIf { !it.trashed }?.let { return it.id }
        }
        val found = api.list("'root' in parents and name = '${DriveApi.q(ROOT_NAME)}' and mimeType = '${DriveApi.MIME_FOLDER}' and trashed = false")
            .firstOrNull()
        val root = found ?: api.createFolder(ROOT_NAME, null, mapOf(PROP_KIND to KIND_ROOT))
        settings.setDriveRootId(root.id)
        return root.id
    }

    /** Drive folder id per sync key, making or mending folders on the way. */
    private suspend fun ensureFolders(): Map<String, String> {
        val ids = mutableMapOf<String, String>()
        ids[SyncPlanner.KEY_ROOT] = ensureRoot()
        val known = db.driveDao().allFolders().associateBy { it.folderKey }.toMutableMap()

        suspend fun ensure(key: String, name: String, parentKey: String) {
            val parentId = ids.getValue(parentKey)
            val record = known[key]
            val existing = record?.let { api.get(it.fileId) }?.takeIf { it.isFolder && !it.trashed }
            val folder = when {
                existing == null -> api.findChild(parentId, name, DriveApi.MIME_FOLDER)
                    ?: api.createFolder(name, parentId, mapOf(PROP_KIND to KIND_FOLDER, PROP_KEY to key))
                existing.name != name || parentId !in existing.parents -> api.update(
                    existing.id, name = name.takeIf { it != existing.name },
                    addParent = parentId.takeIf { it !in existing.parents },
                    removeParent = existing.parents.firstOrNull { it != parentId }?.takeIf { parentId !in existing.parents },
                )
                else -> existing
            }
            ids[key] = folder.id
            val fresh = DriveFolderEntity(key, folder.id, name, parentKey)
            if (fresh != record) { db.driveDao().upsertFolder(fresh); known[key] = fresh }
        }

        ensure(SyncPlanner.KEY_UNFILED, SyncPlanner.NAME_UNFILED, SyncPlanner.KEY_ROOT)
        ensure(SyncPlanner.KEY_ARCHIVE, SyncPlanner.NAME_ARCHIVE, SyncPlanner.KEY_ROOT)
        orderedFolders(db.folderDao().allFolders()).forEach { folder ->
            val parentKey = folder.parentId?.let { "folder:$it" }?.takeIf { it in ids } ?: SyncPlanner.KEY_ROOT
            ensure("folder:${folder.id}", folder.name.ifBlank { "Folder ${folder.id}" }, parentKey)
        }
        // Folders deleted on the shelf keep their Drive folder; the notes inside have moved on.
        known.keys.filter { it.startsWith("folder:") && it !in ids }.forEach { db.driveDao().deleteFolder(it) }
        return ids
    }

    private fun describe(action: SyncPlanner.Action, title: String): String = when (action) {
        is SyncPlanner.Action.Upload -> "Uploading $title…"
        is SyncPlanner.Action.Move -> "Moving $title…"
        is SyncPlanner.Action.Rename -> "Renaming $title…"
        is SyncPlanner.Action.Trash -> "Trashing $title…"
    }

    private fun describe(error: Throwable): String = when (error) {
        is DriveAuth.ConsentRequiredException -> "Google Drive needs you to log in again"
        is DriveApi.DriveHttpException -> when (error.status) {
            401, 403 -> "Google Drive refused the request (${error.status}). Log in again."
            404 -> "Something is missing on Drive; it will be made again on the next sync."
            else -> error.message ?: "Drive error ${error.status}"
        }
        is java.net.UnknownHostException, is java.net.SocketTimeoutException, is java.net.ConnectException ->
            "No connection to Google Drive"
        else -> error.message ?: error.javaClass.simpleName
    }

    companion object {
        const val ROOT_NAME = "Fucknotes"
        const val PROP_KIND = "fucknotes"
        const val PROP_KEY = "fucknotesKey"
        const val PROP_NOTE_ID = "fucknotesNoteId"
        const val PROP_INSTALL = "fucknotesInstall"
        const val PROP_UPDATED = "fucknotesUpdatedAt"
        const val KIND_ROOT = "root"
        const val KIND_FOLDER = "folder"
        const val KIND_NOTE = "note"
        const val MIN_AUTO_INTERVAL_MS = 15 * 60_000L
        const val CHANGE_DEBOUNCE_MS = 30_000L

        /** Parents before children, so a folder's Drive parent exists when it is made. */
        fun orderedFolders(folders: List<FolderEntity>): List<FolderEntity> {
            val byParent = folders.groupBy { it.parentId }
            val result = mutableListOf<FolderEntity>()
            val seen = mutableSetOf<Long>()
            fun visit(parentId: Long?) {
                byParent[parentId].orEmpty().sortedBy { it.id }.forEach { folder ->
                    if (seen.add(folder.id)) { result += folder; visit(folder.id) }
                }
            }
            visit(null)
            // Anything whose parent chain is broken still gets a place, at the top.
            folders.filter { it.id !in seen }.sortedBy { it.id }.forEach { result += it; seen += it.id }
            return result
        }
    }
}
