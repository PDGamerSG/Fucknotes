package com.example.fucknotes.export

import java.util.Locale

object ExportNames {
    fun safeBase(title: String): String = title.trim()
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
        .trim(' ', '.').take(120).ifBlank { "Untitled" }

    /** Disambiguate within this export, never based on stale cache files. */
    fun unique(name: String, used: MutableSet<String>, extension: String = ""): String {
        val base = safeBase(name)
        var candidate = "$base$extension"
        var n = 2
        while (!used.add(candidate.lowercase(Locale.ROOT))) candidate = "$base (${n++})$extension"
        return candidate
    }
}
