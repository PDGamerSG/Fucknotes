package com.example.fucknotes.ui.editor.tabs

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.example.fucknotes.data.repo.NotesRepository
import com.example.fucknotes.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The notes the user currently has open.
 *
 * Tabs belong to the session rather than to any one editor: opening a second note has to leave the
 * first one in the bar, going back to the shelf must not close anything, and closing a tab has to
 * be able to say which note to show instead. That makes this app-scoped snapshot state rather than
 * a view-model field — a view model that dies with its note cannot own the list its note is only
 * one entry in.
 *
 * Only titles and ids are held. Content stays in the database, so a tab is cheap and a stale one is
 * harmless: reopening simply loads the note again. The ids are mirrored into settings by [bind], so
 * the bar comes back after the app is restarted — a tab is a place kept, not a window.
 */
object OpenTabs {

    @Immutable
    data class Tab(val noteId: Long, val title: String)

    var tabs: List<Tab> by mutableStateOf(emptyList())
        private set

    /** Adds a tab, or moves the existing one to the front of the user's attention. */
    fun open(noteId: Long, title: String = "") {
        if (tabs.any { it.noteId == noteId }) {
            rename(noteId, title)
            return
        }
        tabs = tabs + Tab(noteId, title)
        trim()
    }

    /** Keeps the bar's label in step with the note's own title as it is typed. */
    fun rename(noteId: Long, title: String) {
        if (title.isBlank()) return
        tabs = tabs.map { if (it.noteId == noteId) it.copy(title = title) else it }
    }

    /**
     * Closes a tab and reports which note should take its place — the one to its left, or its right
     * when it was first. Null means nothing is left open.
     */
    fun close(noteId: Long): Long? {
        val index = tabs.indexOfFirst { it.noteId == noteId }
        if (index < 0) return tabs.firstOrNull()?.noteId
        val remaining = tabs.filterNot { it.noteId == noteId }
        tabs = remaining
        if (remaining.isEmpty()) return null
        return remaining[(index - 1).coerceIn(remaining.indices)].noteId
    }

    /** Drops a tab without choosing a successor — for a note that no longer exists. */
    fun forget(noteId: Long) {
        tabs = tabs.filterNot { it.noteId == noteId }
    }

    fun clear() {
        tabs = emptyList()
    }

    /**
     * Restores the bar from settings, then keeps settings in step with it.
     *
     * Notes that were trashed or deleted while the app was closed are skipped, so a restored bar
     * never points at a page that cannot open. Anything opened before the restore finished (a PDF
     * handed to the app, say) is kept after the restored tabs.
     */
    fun bind(repo: NotesRepository, settings: SettingsRepository, scope: CoroutineScope) {
        scope.launch {
            val restored = settings.openTabIds.first().mapNotNull { id ->
                repo.getNote(id)?.takeIf { it.deletedAt == null }?.let { Tab(it.id, it.title) }
            }
            val fresh = tabs.filterNot { tab -> restored.any { it.noteId == tab.noteId } }
            tabs = restored + fresh
            trim()
            snapshotFlow { tabs.map { it.noteId } }
                .distinctUntilChanged()
                .collect { settings.setOpenTabIds(it) }
        }
    }

    /** A bar of tabs is a working set, not a history; the oldest one drops off at the limit. */
    private fun trim() {
        if (tabs.size > MAX_TABS) tabs = tabs.takeLast(MAX_TABS)
    }

    const val MAX_TABS = 12
}
