package com.example.fucknotes.data.repo

/**
 * Arithmetic for the shelf's sparse positions.
 *
 * Kept free of Android so it can be tested on the JVM: the repository decides *what* to move, this
 * decides *where* it lands.
 */
object NotePositions {
    /** Gap left between notes when they are renumbered; wide enough for many moves between them. */
    const val STEP: Long = 1L shl 20

    /**
     * The position for a note dropped between [before] and [after], or null when the neighbours
     * are too close together and the shelf has to be renumbered first.
     *
     * With no neighbour on one side the note steps past the other; with none on either side (a
     * shelf of one) it sits at zero.
     */
    fun between(before: Long?, after: Long?): Long? = when {
        before == null && after == null -> 0L
        before == null -> after!! - STEP
        after == null -> before + STEP
        after - before >= 2 -> before + (after - before) / 2
        else -> null
    }
}
