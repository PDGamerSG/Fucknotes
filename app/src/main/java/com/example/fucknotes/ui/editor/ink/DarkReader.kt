package com.example.fucknotes.ui.editor.ink

import androidx.ink.strokes.Stroke

/** Inverts stored page content while leaving the note's saved bytes and export unchanged. */
internal fun darkReaderArgb(argb: Int): Int {
    val alpha = argb ushr 24
    val red = 255 - ((argb ushr 16) and 0xFF)
    val green = 255 - ((argb ushr 8) and 0xFF)
    val blue = 255 - (argb and 0xFF)
    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}

internal fun Stroke.forDarkReader(): Stroke =
    Stroke(
        brush = brush.copyWithColorIntArgb(darkReaderArgb(brush.colorIntArgb)),
        inputs = inputs,
    )
