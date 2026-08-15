package com.example.fucknotes.data.repo

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.fucknotes.data.model.AccentPreset
import com.example.fucknotes.data.model.LayoutMode
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.data.model.SortOrder
import com.example.fucknotes.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.dataStore by preferencesDataStore(name = "settings")

/** App-wide preferences: appearance, list behaviour and the note-lock passcode. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val accent = stringPreferencesKey("accent")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
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
        val zoomLocked = booleanPreferencesKey("zoom_locked")
    }

    data class Settings(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val accent: AccentPreset = AccentPreset.INDIGO,
        val dynamicColor: Boolean = true,
        val layout: LayoutMode = LayoutMode.GRID,
        val sort: SortOrder = SortOrder.UPDATED_DESC,
        val defaultPaper: PaperStyle = PaperStyle.PLAIN,
        val fontScalePercent: Int = 100,
        val keepScreenOn: Boolean = false,
        val confirmDelete: Boolean = true,
        val autoBackup: Boolean = false,
        val lastBackupAt: String = "",
        val hasPasscode: Boolean = false,
        val biometricUnlock: Boolean = true,
        /**
         * Whether a finger draws ink.
         *
         * Off means the page only takes stylus input, and a finger pans instead — the palm-proof
         * mode a pen user expects. On is the default because most devices have no stylus at all,
         * and a page that ignored every touch would look broken on those.
         */
        val fingerDrawing: Boolean = true,
        /** Whether a pinch may change the page scale. Scrolling works either way. */
        val zoomLocked: Boolean = false,
    )

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = Settings(
        themeMode = enumOr(this[Keys.themeMode], ThemeMode.SYSTEM),
        accent = enumOr(this[Keys.accent], AccentPreset.INDIGO),
        dynamicColor = this[Keys.dynamicColor] ?: true,
        layout = enumOr(this[Keys.layout], LayoutMode.GRID),
        sort = enumOr(this[Keys.sort], SortOrder.UPDATED_DESC),
        defaultPaper = enumOr(this[Keys.defaultPaper], PaperStyle.PLAIN),
        fontScalePercent = this[Keys.fontScale] ?: 100,
        keepScreenOn = this[Keys.keepScreenOn] ?: false,
        confirmDelete = this[Keys.confirmDelete] ?: true,
        autoBackup = this[Keys.autoBackup] ?: false,
        lastBackupAt = this[Keys.lastBackupAt].orEmpty(),
        hasPasscode = !this[Keys.passcodeHash].isNullOrBlank(),
        biometricUnlock = this[Keys.biometricUnlock] ?: true,
        fingerDrawing = this[Keys.fingerDrawing] ?: true,
        zoomLocked = this[Keys.zoomLocked] ?: false,
    )

    private inline fun <reified T : Enum<T>> enumOr(raw: String?, fallback: T): T =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    suspend fun setThemeMode(v: ThemeMode) = edit { it[Keys.themeMode] = v.name }
    suspend fun setAccent(v: AccentPreset) = edit { it[Keys.accent] = v.name }
    suspend fun setDynamicColor(v: Boolean) = edit { it[Keys.dynamicColor] = v }
    suspend fun setLayout(v: LayoutMode) = edit { it[Keys.layout] = v.name }
    suspend fun setSort(v: SortOrder) = edit { it[Keys.sort] = v.name }
    suspend fun setDefaultPaper(v: PaperStyle) = edit { it[Keys.defaultPaper] = v.name }
    suspend fun setFontScale(percent: Int) = edit { it[Keys.fontScale] = percent.coerceIn(80, 160) }
    suspend fun setKeepScreenOn(v: Boolean) = edit { it[Keys.keepScreenOn] = v }
    suspend fun setConfirmDelete(v: Boolean) = edit { it[Keys.confirmDelete] = v }
    suspend fun setAutoBackup(v: Boolean) = edit { it[Keys.autoBackup] = v }
    suspend fun setLastBackupAt(stamp: String) = edit { it[Keys.lastBackupAt] = stamp }
    suspend fun setBiometricUnlock(v: Boolean) = edit { it[Keys.biometricUnlock] = v }
    suspend fun setFingerDrawing(v: Boolean) = edit { it[Keys.fingerDrawing] = v }
    suspend fun setZoomLocked(v: Boolean) = edit { it[Keys.zoomLocked] = v }

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
