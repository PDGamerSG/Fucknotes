package com.example.fucknotes.data.ink

import android.util.LruCache

/**
 * Decoded strokes for the last few notes opened.
 *
 * A stroke is a tessellated mesh; building thousands of them is the slow part of opening a
 * notebook, and the tab bar makes going back and forth between notes the usual way of working.
 * Keeping the decoded list for a handful of notes makes a second opening instant. An entry is
 * trusted only while the stored JSON is the one it was decoded from.
 */
object InkCache {
    class Entry(val json: String, val strokes: List<InkStroke>)

    private val entries = LruCache<Long, Entry>(4)

    /** The strokes for [noteId] if they were decoded from exactly [json]. */
    fun get(noteId: Long, json: String): List<InkStroke>? {
        val entry = entries.get(noteId) ?: return null
        return entry.strokes.takeIf { entry.json === json || entry.json == json }
    }

    fun put(noteId: Long, json: String, strokes: List<InkStroke>) {
        entries.put(noteId, Entry(json, strokes))
    }

    fun evict(noteId: Long) {
        entries.remove(noteId)
    }
}
