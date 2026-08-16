package com.example.fucknotes.data.ink

/**
 * A paper size, in points.
 *
 * Points are the unit a PDF page is measured in and the unit ink world coordinates use, so a page
 * written here exports one-to-one instead of being scaled to fit.
 */
enum class PageFormat(val label: String, val shortSideDp: Float, val longSideDp: Float) {
    A4("A4", 595f, 842f),
    A5("A5", 420f, 595f),
    B5("B5", 499f, 709f),
    LETTER("Letter", 612f, 792f),
    ;

    companion object {
        fun byName(raw: String?): PageFormat =
            entries.firstOrNull { it.name == raw } ?: A4
    }
}

/** A [PageFormat] plus the direction it is turned. */
data class PageSpec(
    val format: PageFormat = PageFormat.A4,
    val landscape: Boolean = false,
) {
    val widthDp: Float get() = if (landscape) format.longSideDp else format.shortSideDp
    val heightDp: Float get() = if (landscape) format.shortSideDp else format.longSideDp

    /** Stored on the note as one string, so a size change is a single column write. */
    fun encode(): String = if (landscape) "${format.name}_L" else format.name

    companion object {
        val Default = PageSpec()

        fun decode(raw: String?): PageSpec {
            if (raw.isNullOrBlank()) return Default
            val landscape = raw.endsWith("_L")
            return PageSpec(PageFormat.byName(raw.removeSuffix("_L")), landscape)
        }
    }
}
