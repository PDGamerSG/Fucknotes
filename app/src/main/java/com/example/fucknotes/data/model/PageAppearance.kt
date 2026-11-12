package com.example.fucknotes.data.model

/**
 * Whether a note's pages are shown in light or night colours.
 *
 * A new notebook is light. Dark mode is one switch in the editor's ⋯ menu, it turns the pages
 * black, and an export comes out the way the note is set. Notes made before the choice existed,
 * and documents that arrive by import, carry [FOLLOW] and take the app-wide "Dark mode for pages"
 * setting.
 */
enum class PageAppearance {
    FOLLOW,
    LIGHT,
    DARK;

    /** Night colours or not, given the app-wide setting this note may be following. */
    fun isDark(appDarkReader: Boolean): Boolean = when (this) {
        FOLLOW -> appDarkReader
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun of(dark: Boolean): PageAppearance = if (dark) DARK else LIGHT

        /**
         * The appearance a stored name stands for. Dark mode is black now, so a note or an
         * exported file that still says "PITCH_BLACK" is simply dark; any other unknown name
         * follows the app, like a note that never chose.
         */
        fun fromStored(name: String): PageAppearance = when (name) {
            "PITCH_BLACK" -> DARK
            else -> runCatching { valueOf(name) }.getOrDefault(FOLLOW)
        }
    }
}
