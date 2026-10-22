package com.example.fucknotes.data.ink

import android.util.Base64
import androidx.ink.brush.Brush
import androidx.ink.brush.ExperimentalInkCustomBrushApi
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.StockBrushes
import androidx.ink.storage.decode
import androidx.ink.storage.encode
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInputBatch
import com.example.fucknotes.data.model.InkTool
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.WeakHashMap

/**
 * An Ink API stroke plus the tool that drew it.
 *
 * The tool is carried alongside rather than recovered from [Stroke.brush] because a [BrushFamily]
 * is an opaque native handle — tagging at creation time is exact, cheap and survives a round trip
 * through storage.
 */
data class InkStroke(val stroke: Stroke, val tool: InkTool)

/**
 * Persists Ink API strokes into the single `drawingJson` column on a note.
 *
 * A stroke is fully described by its input batch plus the brush that drew it, so only those are
 * stored; the tessellated mesh is rebuilt by the Ink runtime on load. Input batches are encoded by
 * the library into a compressed binary blob, Base64-wrapped so the whole page still travels inside
 * ordinary JSON — and therefore inside the backup zip unchanged.
 */
object InkCodec {

    const val FORMAT_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Re-encoding every stroke on each autosave gets wasteful once a page runs long, so encoded
     * bytes are memoised per stroke instance. Weak keys let strokes dropped by undo be collected.
     */
    private val encodedCache: MutableMap<Stroke, String> =
        Collections.synchronizedMap(WeakHashMap())

    private val brushTools: MutableMap<Brush, InkTool> = Collections.synchronizedMap(WeakHashMap())

    fun toolForStroke(stroke: Stroke, fallback: InkTool): InkTool = brushTools[stroke.brush] ?: fallback

    @Serializable
    private data class Page(
        val version: Int = FORMAT_VERSION,
        val strokes: List<StrokeDto> = emptyList(),
    )

    @Serializable
    private data class StrokeDto(
        /** Base64 of the library-encoded [StrokeInputBatch]. */
        val inputs: String,
        val tool: String,
        val colorArgb: Int,
        val size: Float,
        val epsilon: Float,
    )

    fun encode(strokes: List<InkStroke>): String {
        if (strokes.isEmpty()) return ""
        val dtos = strokes.map { inkStroke ->
            val brush = inkStroke.stroke.brush
            StrokeDto(
                inputs = encodedCache.getOrPut(inkStroke.stroke) {
                    val bytes = ByteArrayOutputStream().use { out ->
                        inkStroke.stroke.inputs.encode(out)
                        out.toByteArray()
                    }
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                },
                tool = inkStroke.tool.name,
                colorArgb = brush.colorIntArgb,
                size = brush.size,
                epsilon = brush.epsilon,
            )
        }
        return json.encodeToString(Page(strokes = dtos))
    }

    fun decode(raw: String): List<InkStroke> {
        if (raw.isBlank()) return emptyList()
        val page = runCatching { json.decodeFromString<Page>(raw) }.getOrNull() ?: return emptyList()
        return page.strokes.mapNotNull { dto ->
            runCatching {
                val tool = runCatching { InkTool.valueOf(dto.tool) }.getOrDefault(InkTool.PEN)
                val inputs = ByteArrayInputStream(Base64.decode(dto.inputs, Base64.NO_WRAP))
                    .use { StrokeInputBatch.decode(it) }
                val brush = Brush.createWithColorIntArgb(
                    family = familyForTool(tool),
                    colorIntArgb = colorForTool(tool, dto.colorArgb),
                    size = dto.size,
                    epsilon = dto.epsilon,
                )
                InkStroke(Stroke(brush = brush, inputs = inputs), tool)
            }.getOrNull()
        }
    }

    /**
     * Builds the brush for a tool.
     *
     * [worldToScreenScale] is the combined density-and-zoom factor. Ink expresses `epsilon` in
     * stroke units, so the on-screen distance it stands for changes with zoom; scaling it here
     * keeps curve smoothing consistent whether the page sits at 1:1 or magnified.
     */
    fun brushFor(tool: InkTool, colorArgb: Int, widthDp: Float, worldToScreenScale: Float): Brush =
        Brush.createWithColorIntArgb(
            family = familyForTool(tool),
            colorIntArgb = colorForTool(tool, colorArgb),
            size = widthDp.coerceAtLeast(0.5f),
            epsilon = (0.1f / worldToScreenScale.coerceAtLeast(0.05f)).coerceIn(0.001f, 1f),
        ).also { brushTools[it] = tool }

    /**
     * Clamp rather than multiply alpha: repeated saves must never fade a highlight.  A quarter
     * opacity keeps black text readable underneath the chisel tip on both paper and imported PDFs.
     */
    internal fun colorForTool(tool: InkTool, argb: Int): Int =
        if (tool == InkTool.HIGHLIGHTER) {
            (argb and 0x00FFFFFF) or (minOf(argb ushr 24, HIGHLIGHTER_ALPHA) shl 24)
        } else argb

    private const val HIGHLIGHTER_ALPHA = 64

    @OptIn(ExperimentalInkCustomBrushApi::class)
    private fun familyForTool(tool: InkTool): BrushFamily = when (tool) {
        // The stock pressure pen uses the raw input model.  On a large, high-density tablet that
        // can make tiny digitiser samples visible as a wobble, especially at slow handwriting
        // speed.  Reusing its coat with Ink's spring input model keeps pressure and tilt behaviour
        // while smoothing the path before it is tessellated.  The same family is used on decode so
        // saved strokes retain the exact rendering behaviour of newly written strokes.
        InkTool.PEN -> StockBrushes.pressurePen().copy(inputModel = BrushFamily.SPRING_MODEL)
        InkTool.MARKER -> StockBrushes.marker()
        InkTool.HIGHLIGHTER -> StockBrushes.highlighter()
        InkTool.DASHED -> StockBrushes.dashedLine()
        // Not brush tools; fall back to the pen so callers never get a null family.
        InkTool.ERASER, InkTool.PAN -> StockBrushes.pressurePen()
    }
}
