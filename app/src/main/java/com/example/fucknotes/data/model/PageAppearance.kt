package com.example.fucknotes.data.model

/**
 * Whether a note's pages are shown in light or night colours.
 *
 * A notebook chooses at creation, the way it chooses its paper, and can change its mind from the
 * editor's ⋯ menu or the Paper sheet. Notes made before this existed, and documents that arrive by
 * import, carry [FOLLOW] and take the app-wide "Dark mode for pages" setting.
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
    }
}
