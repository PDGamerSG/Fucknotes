package com.example.fucknotes.data.repo

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.fucknotes.data.model.AccentPreset
import com.example.fucknotes.data.model.DefaultPenTray
import com.example.fucknotes.data.model.FingerInputMode
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.LayoutMode
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.data.model.PenPreset
import com.example.fucknotes.data.model.PenButtonAction
import com.example.fucknotes.data.model.SortOrder
import com.example.fucknotes.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.security.MessageDigest

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Keep five pen choices and two highlighter choices available after loading an older tray. */
private fun normalizePenTray(stored: List<PenPreset>?): List<PenPreset> {
    val tray = stored?.takeIf { it.isNotEmpty() } ?: DefaultPenTray
    val missingPens = (5 - tray.count { it.tool == InkTool.PEN }).coerceAtLeast(0)
    val missingHighlighters = (2 - tray.count { it.tool == InkTool.HIGHLIGHTER }).coerceAtLeast(0)
    return tray +
        DefaultPenTray.filter { it.tool == InkTool.PEN }.take(missingPens) +
        DefaultPenTray.filter { it.tool == InkTool.HIGHLIGHTER }.take(missingHighlighters)
}

/** App-wide preferences: appearance, list behaviour and the note-lock passcode. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val accent = stringPreferencesKey("accent")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val darkReader = booleanPreferencesKey("dark_reader")
        val layout = stringPreferencesKey("layout")
        val sort = stringPreferencesKey("sort")
        val defaultPaper = stringPreferencesKey("default_paper")
        val fontScale = intPreferencesKey("font_scale_percent")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val confirmDelete = booleanPreferencesKey("confirm_delete")
        val autoBackup = booleanPreferencesKey("auto_backup")
        val lastBackupAt = stringPreferencesKey("last_backup_at")
        val passcodeHash = stringPreferencesKey("passcode_hash")
        val biometricUnlock = booleanPreferencesKey("biometric_unlock")
        val fingerDrawing = booleanPreferencesKey("finger_drawing")
        val fingerInputMode = stringPreferencesKey("finger_input_mode")
        val zoomLocked = booleanPreferencesKey("zoom_locked")
        val penTray = stringPreferencesKey("pen_tray")
        val selectedPen = intPreferencesKey("selected_pen")
        val penPrimaryAction = stringPreferencesKey("pen_primary_action")
        val penSecondaryAction = stringPreferencesKey("pen_secondary_action")
        val penTertiaryAction = stringPreferencesKey("pen_tertiary_action")
        val openTabs = stringPreferencesKey("open_tabs")
        val installId = stringPreferencesKey("install_id")
        val driveEmail = stringPreferencesKey("drive_email")
        val driveName = stringPreferencesKey("drive_name")
        val driveRootId = stringPreferencesKey("drive_root_id")
        val driveAutoSync = booleanPreferencesKey("drive_auto_sync")
        val driveLastSyncAt = longPreferencesKey("drive_last_sync_at")
        val driveLastError = stringPreferencesKey("drive_last_error")
        val editablePdfExports = booleanPreferencesKey("editable_pdf_exports")
    }

    private val json = Json { ignoreUnknownKeys = true }

    data class Settings(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val accent: AccentPreset = AccentPreset.INDIGO,
        /**
         * Off by default: the app has its own violet identity, and a wallpaper-derived palette
         * repaints the rail, the artwork and the paper previews in colours the design was not made
         * from. It stays available for anyone who prefers their system's colours.
         */
        val dynamicColor: Boolean = false,
        /** Repaints note pages in a dark, low-glare palette while leaving the stored note unchanged. */
        val darkReader: Boolean = false,
        val layout: LayoutMode = LayoutMode.GRID,
        val sort: SortOrder = SortOrder.MANUAL,
        val defaultPaper: PaperStyle = PaperStyle.PLAIN,
        val fontScalePercent: Int = 100,
        val keepScreenOn: Boolean = false,
        val confirmDelete: Boolean = true,
        val autoBackup: Boolean = false,
        val lastBackupAt: String = "",
        val hasPasscode: Boolean = false,
        val biometricUnlock: Boolean = true,
        /** App-wide finger behavior, independent of the selected stylus tool. */
        val fingerInputMode: FingerInputMode = FingerInputMode.DRAW,
        /** Whether a pinch may change the page scale. Scrolling works either way. */
        val zoomLocked: Boolean = false,
        /**
         * The pen case, kept app-wide rather than per note.
         *
         * A nib width and colour is a property of the pen the user set up, not of the page they
         * happened to set it on; storing it here is what makes a 12 dp marker still be 12 dp the
         * next time the app opens.
         */
        val penTray: List<PenPreset> = DefaultPenTray,
        /** Which tray slot is in hand, so a note opens with the pen last written with. */
        val selectedPen: Int = 0,
        val penPrimaryAction: PenButtonAction = PenButtonAction.TOGGLE_ERASER,
        val penSecondaryAction: PenButtonAction = PenButtonAction.UNDO,
        val penTertiaryAction: PenButtonAction = PenButtonAction.REDO,
        /** The Google account whose Drive holds the notes folder; blank when not logged in. */
        val driveEmail: String = "",
        val driveName: String = "",
        /** Drive id of the app's top folder, once it exists. */
        val driveRootId: String = "",
        val driveAutoSync: Boolean = true,
        val driveLastSyncAt: Long = 0,
        val driveLastError: String = "",
        /** PDF exports carry the note's own data, so they can be opened back into an editable note. */
        val editablePdfExports: Boolean = true,
    ) {
        val driveSignedIn: Boolean get() = driveEmail.isNotBlank()
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    /**
     * The editor's open tabs, oldest first, kept apart from [Settings] so a tab change does not
     * re-emit every screen that only watches the theme.
     */
    val openTabIds: Flow<List<Long>> = context.dataStore.data.map { prefs ->
        prefs[Keys.openTabs].orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }
    }

    suspend fun setOpenTabIds(ids: List<Long>) = edit { it[Keys.openTabs] = ids.joinToString(",") }

    private fun Preferences.toSettings() = Settings(
        themeMode = enumOr(this[Keys.themeMode], ThemeMode.SYSTEM),
        accent = enumOr(this[Keys.accent], AccentPreset.INDIGO),
        dynamicColor = this[Keys.dynamicColor] ?: false,
        darkReader = this[Keys.darkReader] ?: false,
        layout = enumOr(this[Keys.layout], LayoutMode.GRID),
        sort = enumOr(this[Keys.sort], SortOrder.MANUAL),
        defaultPaper = enumOr(this[Keys.defaultPaper], PaperStyle.PLAIN),
        fontScalePercent = this[Keys.fontScale] ?: 100,
        keepScreenOn = this[Keys.keepScreenOn] ?: false,
        confirmDelete = this[Keys.confirmDelete] ?: true,
        autoBackup = this[Keys.autoBackup] ?: false,
        lastBackupAt = this[Keys.lastBackupAt].orEmpty(),
        hasPasscode = !this[Keys.passcodeHash].isNullOrBlank(),
        biometricUnlock = this[Keys.biometricUnlock] ?: true,
        fingerInputMode = enumOr(
            this[Keys.fingerInputMode],
            if (this[Keys.fingerDrawing] == false) FingerInputMode.HAND else FingerInputMode.DRAW,
        ),
        zoomLocked = this[Keys.zoomLocked] ?: false,
        // A tray written by an older build — or half-written — falls back whole rather than
        // leaving the user with an empty pen case.
        penTray = normalizePenTray(
            this[Keys.penTray]?.let { runCatching { json.decodeFromString<List<PenPreset>>(it) }.getOrNull() },
        ),
        selectedPen = this[Keys.selectedPen] ?: 0,
        penPrimaryAction = enumOr(this[Keys.penPrimaryAction], PenButtonAction.TOGGLE_ERASER),
        penSecondaryAction = enumOr(this[Keys.penSecondaryAction], PenButtonAction.UNDO),
        penTertiaryAction = enumOr(this[Keys.penTertiaryAction], PenButtonAction.REDO),
        driveEmail = this[Keys.driveEmail].orEmpty(),
        driveName = this[Keys.driveName].orEmpty(),
        driveRootId = this[Keys.driveRootId].orEmpty(),
        driveAutoSync = this[Keys.driveAutoSync] ?: true,
        driveLastSyncAt = this[Keys.driveLastSyncAt] ?: 0L,
        driveLastError = this[Keys.driveLastError].orEmpty(),
        editablePdfExports = this[Keys.editablePdfExports] ?: true,
    )

    private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    suspend fun setThemeMode(v: ThemeMode) = edit { it[Keys.themeMode] = v.name }
    suspend fun setAccent(v: AccentPreset) = edit { it[Keys.accent] = v.name }
    suspend fun setDynamicColor(v: Boolean) = edit { it[Keys.dynamicColor] = v }
    suspend fun setDarkReader(v: Boolean) = edit { it[Keys.darkReader] = v }
    suspend fun setLayout(v: LayoutMode) = edit { it[Keys.layout] = v.name }
    suspend fun setSort(v: SortOrder) = edit { it[Keys.sort] = v.name }
    suspend fun setDefaultPaper(v: PaperStyle) = edit { it[Keys.defaultPaper] = v.name }
    suspend fun setFontScale(percent: Int) = edit { it[Keys.fontScale] = percent.coerceIn(80, 160) }
    suspend fun setKeepScreenOn(v: Boolean) = edit { it[Keys.keepScreenOn] = v }
    suspend fun setConfirmDelete(v: Boolean) = edit { it[Keys.confirmDelete] = v }
    suspend fun setAutoBackup(v: Boolean) = edit { it[Keys.autoBackup] = v }
    suspend fun setLastBackupAt(stamp: String) = edit { it[Keys.lastBackupAt] = stamp }
    suspend fun setBiometricUnlock(v: Boolean) = edit { it[Keys.biometricUnlock] = v }
    suspend fun setFingerInputMode(v: FingerInputMode) = edit { it[Keys.fingerInputMode] = v.name }
    suspend fun setZoomLocked(v: Boolean) = edit { it[Keys.zoomLocked] = v }
    suspend fun setPenTray(pens: List<PenPreset>) = edit { it[Keys.penTray] = json.encodeToString(pens) }
    suspend fun setSelectedPen(index: Int) = edit { it[Keys.selectedPen] = index }
    suspend fun setPenPrimaryAction(v: PenButtonAction) = edit { it[Keys.penPrimaryAction] = v.name }
    suspend fun setPenSecondaryAction(v: PenButtonAction) = edit { it[Keys.penSecondaryAction] = v.name }
    suspend fun setPenTertiaryAction(v: PenButtonAction) = edit { it[Keys.penTertiaryAction] = v.name }
    suspend fun setDriveAccount(email: String, name: String) = edit { it[Keys.driveEmail] = email; it[Keys.driveName] = name }
    suspend fun setDriveRootId(id: String) = edit { it[Keys.driveRootId] = id }
    suspend fun setDriveAutoSync(v: Boolean) = edit { it[Keys.driveAutoSync] = v }
    suspend fun setDriveLastSync(at: Long, error: String = "") = edit { it[Keys.driveLastSyncAt] = at; it[Keys.driveLastError] = error }
    suspend fun setDriveLastError(error: String) = edit { it[Keys.driveLastError] = error }
    suspend fun setEditablePdfExports(v: Boolean) = edit { it[Keys.editablePdfExports] = v }
    suspend fun clearDriveAccount() = edit {
        it.remove(Keys.driveEmail); it.remove(Keys.driveName); it.remove(Keys.driveRootId)
        it.remove(Keys.driveLastSyncAt); it.remove(Keys.driveLastError)
    }

    /**
     * A random id for this install, minted once. Editable PDFs carry it next to the note id, so a
     * file coming back to the same install finds its own note instead of making a copy.
     */
    suspend fun installId(): String {
        context.dataStore.data.first()[Keys.installId]?.takeIf { it.isNotBlank() }?.let { return it }
        val fresh = java.util.UUID.randomUUID().toString()
        edit { it[Keys.installId] = fresh }
        return fresh
    }

    // ---- note lock ---------------------------------------------------------

    /**
     * Stores a salted SHA-256 digest of the passcode. This gates access to notes flagged as
     * locked; it is not disk encryption and does not protect the database file itself.
     */
    suspend fun setPasscode(code: String?) = edit {
        if (code.isNullOrBlank()) it.remove(Keys.passcodeHash) else it[Keys.passcodeHash] = hash(code)
    }

    suspend fun checkPasscode(code: String): Boolean {
        val stored = context.dataStore.data.first()[Keys.passcodeHash] ?: return false
        return stored == hash(code)
    }

    private fun hash(code: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(("fucknotes::$code").toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
