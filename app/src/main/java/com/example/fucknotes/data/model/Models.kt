package com.example.fucknotes.data.model

import kotlinx.serialization.Serializable

/** What kind of content a note holds. A note keeps one primary mode but may carry attachments. */
enum class NoteType {
    TEXT,
    CHECKLIST,

    /**
     * Canvas mode: one endless sheet the camera roams over.
     *
     * The constant keeps its original name because it is the string Room has already written into
     * every existing row; the user-facing name is "Canvas".
     */
    DRAWING,

    /** Notebook mode: the same ink, laid out as screen-shaped pages stacked down a column. */
    NOTEBOOK,
    ;

    /** True for the two ink modes, which share the writing surface and differ only in layout. */
    val isInk: Boolean get() = this == DRAWING || this == NOTEBOOK
}

/** Paper background drawn behind the editor and the drawing canvas. */
enum class PaperStyle { PLAIN, LINED, GRID, DOTTED }

/**
 * Tools on the ink toolbar.
 *
 * The drawing tools map onto Ink API stock brush families; [ERASER] and [PAN] are handled by the
 * canvas itself rather than by a brush.
 */
enum class InkTool {
    /** Pressure- and speed-sensitive nib, the default for handwriting. */
    PEN,

    /** Flat-width felt tip. */
    MARKER,

    /** Translucent chisel tip that does not darken where it overlaps itself. */
    HIGHLIGHTER,

    /** Dashed line, useful for guides and annotations. */
    DASHED,

    /** Removes whole strokes it passes over. */
    ERASER,

    /** Single-finger pan instead of drawing. */
    PAN,
    ;

    val isBrush: Boolean get() = this == PEN || this == MARKER || this == HIGHLIGHTER || this == DASHED
}

/** What a finger does on a writing surface; the stylus always remains available. */
enum class FingerInputMode(val label: String, val description: String) {
    HAND("Hand / move page", "A finger pans the page"),
    DRAW("Write with fingers", "A finger uses the selected pen"),
    DISABLED("Disabled", "Finger touches do nothing"),
}

/** Physical buttons exposed by Android for active styluses, including the Xiaomi Pen. */
enum class PenButton {
    PRIMARY,
    SECONDARY,
    TERTIARY,
}

/** Actions that can be assigned to a Xiaomi Pen button. */
enum class PenButtonAction(val label: String) {
    NONE("Do nothing"),
    TOGGLE_ERASER("Toggle eraser"),
    UNDO("Undo"),
    REDO("Redo"),
    PEN("Pen"),
    MARKER("Marker"),
    HIGHLIGHTER("Highlighter"),
    PAN("Pan page"),
}

/**
 * One slot in the pen tray.
 *
 * The reference app shows a fixed row of pre-configured pens; keeping colour and width on the slot
 * means switching pens restores the exact nib the user last set for it.
 */
@Serializable
data class PenPreset(
    val tool: InkTool,
    val colorArgb: Int,
    /** Nib width in dp, which is also the brush size in world units. */
    val widthDp: Float,
)

/** Default tray, left to right, mirroring the source app's pen row. */
val DefaultPenTray: List<PenPreset> = listOf(
    PenPreset(InkTool.PEN, 0xFF171B23.toInt(), 2.5f),
    PenPreset(InkTool.PEN, 0xFF7B818A.toInt(), 2.5f),
    PenPreset(InkTool.PEN, 0xFF2386DC.toInt(), 2.5f),
    PenPreset(InkTool.PEN, 0xFFE84158.toInt(), 2.5f),
    PenPreset(InkTool.PEN, 0xFF299B62.toInt(), 2.5f),
    PenPreset(InkTool.PEN, 0xFFF18432.toInt(), 2.5f),
    PenPreset(InkTool.HIGHLIGHTER, 0xFFFFDF43.toInt(), 18f),
    PenPreset(InkTool.HIGHLIGHTER, 0xFF9ADD70.toInt(), 18f),
    PenPreset(InkTool.MARKER, 0xFF9C5B31.toInt(), 6f),
    PenPreset(InkTool.DASHED, 0xFFC75AA4.toInt(), 3f),
)

/** Professional, high-contrast inks and softer annotation colors. */
val InkColors: List<Int> = listOf(
    0xFF171B23.toInt(), 0xFF7B818A.toInt(), 0xFFD1D5DB.toInt(), 0xFFFFFFFF.toInt(),
    0xFF2386DC.toInt(), 0xFFE84158.toInt(), 0xFF299B62.toInt(), 0xFFFFDF43.toInt(),
    0xFFF18432.toInt(), 0xFFC75AA4.toInt(), 0xFF9C5B31.toInt(), 0xFFFFBC7E.toInt(),
    0xFF9ADD70.toInt(), 0xFF44BF42.toInt(), 0xFF7954A1.toInt(), 0xFF279DA8.toInt(),
)


enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class SortOrder { UPDATED_DESC, UPDATED_ASC, CREATED_DESC, CREATED_ASC, TITLE_ASC, TITLE_DESC }

enum class LayoutMode { GRID, LIST }

/** A single row of a checklist note. */
@Serializable
data class ChecklistItem(
    val id: String,
    val text: String,
    val checked: Boolean = false,
)

enum class AttachmentType { IMAGE, AUDIO }

/** Preset accent colours offered in Settings, mirroring the source app's theme picker. */
enum class AccentPreset(val label: String, val seedArgb: Int) {
    INDIGO("Indigo", 0xFF5B5BD6.toInt()),
    MACARON("Macaron in Pastel", 0xFFE59BB0.toInt()),
    MATCHA("Iced Matcha Latte", 0xFF7BAE7F.toInt()),
    MORANDI("Morandi", 0xFF9A8C7A.toInt()),
    SUNLIT("Sunlit Green Whisper", 0xFFA3C48B.toInt()),
    OCEAN("Ocean", 0xFF3E7CB1.toInt()),
    AMBER("Amber", 0xFFD98E32.toInt()),
}

/** Colours a note card can be tinted with. */
val NoteColors: List<Int> = listOf(
    0x00000000, // default / no tint
    0xFFFFF1B8.toInt(),
    0xFFFFD8CC.toInt(),
    0xFFD6F5D6.toInt(),
    0xFFCDE7FF.toInt(),
    0xFFE8DAFF.toInt(),
    0xFFFFE0EE.toInt(),
    0xFFE0E0E0.toInt(),
)
