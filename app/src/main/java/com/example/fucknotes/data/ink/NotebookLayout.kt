package com.example.fucknotes.data.ink

import kotlin.math.floor

/**
 * Geometry of a notebook page, shared by the editor and the exporter.
 *
 * Ink world units are points, so a page is a fixed size in the world and the camera scales it to
 * the device — a note written on a phone opens as the same page on a tablet, and prints at size.
 * Every measurement is taken from the note's own [PageSpec], so two notebooks of different sizes
 * can be open at once without either of them consulting a global.
 */
object NotebookLayout {

    /**
     * Space between sheets.
     *
     * Zero: the sheets butt up against each other so the column scrolls as one continuous surface
     * and a line of writing never falls into a dead band. The page boundary is still readable —
     * each sheet is outlined, so the shared edge draws as a single hairline rule.
     */
    const val PAGE_GAP_DP = 0f

    /** Distance from one page top to the next. */
    fun strideOf(spec: PageSpec): Float = spec.heightDp + PAGE_GAP_DP

    fun topOf(index: Int, spec: PageSpec): Float = index * strideOf(spec)

    /** Which page a world y falls on. Negative above the first page. */
    fun pageIndexAt(worldY: Float, spec: PageSpec): Int = floor(worldY / strideOf(spec)).toInt()

    /** How many pages a column has to hold to contain [maxWorldY]. Never fewer than one. */
    fun pageCountFor(maxWorldY: Float, spec: PageSpec): Int =
        (pageIndexAt(maxWorldY, spec) + 1).coerceAtLeast(1)
}
