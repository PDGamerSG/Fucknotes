package com.example.fucknotes.data.model

import com.example.fucknotes.data.ink.DEFAULT_RULE_SPACING_DP
import com.example.fucknotes.data.ink.PageSpec

/**
 * What the New Notebook sheet collects before a notebook exists.
 *
 * Paper is decided once, at creation, the way it is when you buy a notebook — so the sheet's
 * controls all write here, and the repository turns one of these into the first page.
 */
data class NotebookDraft(
    val title: String = "",
    val type: NoteType = NoteType.NOTEBOOK,
    val paperStyle: PaperStyle = PaperStyle.DOTTED,
    val paperColorArgb: Int = PaperColors.first(),
    val lineColorArgb: Int = LineColors.first(),
    val spacingDp: Int = DEFAULT_RULE_SPACING_DP.toInt(),
    val page: PageSpec = PageSpec.Default,
)

/** Paper tones offered at creation: white, cream and a dark sheet for night writing. */
val PaperColors: List<Int> = listOf(
    0xFFFFFFFF.toInt(),
    0xFFFBF3DE.toInt(),
    0xFFEDF3EC.toInt(),
    0xFF1F1F24.toInt(),
)

/** Rule tones: the default grey, a soft blue, and a red for margins. */
val LineColors: List<Int> = listOf(
    0xFFD3D6DE.toInt(),
    0xFFA9C8F0.toInt(),
    0xFFE58A8A.toInt(),
    0xFF6C4CE0.toInt(),
)
