package com.example.fucknotes.data.model

import kotlinx.serialization.Serializable

/** What kind of content a note holds. A note keeps one primary mode but may carry attachments. */
enum class NoteType { TEXT, CHECKLIST, DRAWING }

/** Paper background drawn behind the editor and the drawing canvas. */
enum class PaperStyle { PLAIN, LINED, GRID, DOTTED }

/** Drawing tools available on the canvas. */
enum class DrawTool { PEN, HIGHLIGHTER, ERASER }

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

/** One captured pen movement. Pressure is parallel to [points] so stylus width can vary per segment. */
@Serializable
data class Stroke(
    val points: List<StrokePoint>,
    val colorArgb: Int,
    val width: Float,
    val tool: DrawTool,
)

@Serializable
data class StrokePoint(val x: Float, val y: Float, val pressure: Float = 1f)

/** Serializable envelope so a whole drawing round-trips through a single DB column. */
@Serializable
data class Drawing(val strokes: List<Stroke> = emptyList())

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
