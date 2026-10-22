package com.example.fucknotes.ui.editor

/**
 * How much of the editor's chrome floats over the page.
 *
 * One lock button cycles through the three states, so the page can be cleared without a settings
 * trip: tap once to keep only the pen bar, tap again to read with nothing but the page counter, tap
 * a third time to bring every control back. The stylus keeps writing in every state.
 */
enum class EditorChrome {
    /** Every control: the note bar, the pen bar, the zoom readout and the attachment strip. */
    FULL,

    /** Only the pen bar — colours, eraser, finger mode and the lock itself. */
    COMPACT,

    /** Nothing but the page, its counter, and a small unlock button in the corner. */
    READING,
    ;

    /** The state the lock button moves to from this one. */
    fun next(): EditorChrome = when (this) {
        FULL -> COMPACT
        COMPACT -> READING
        READING -> FULL
    }

    /** What the lock button does from this state, for the accessibility label. */
    val lockLabel: String
        get() = when (this) {
            FULL -> "Lock controls: keep only the pen bar"
            COMPACT -> "Lock controls: reading mode"
            READING -> "Unlock controls"
        }
}
