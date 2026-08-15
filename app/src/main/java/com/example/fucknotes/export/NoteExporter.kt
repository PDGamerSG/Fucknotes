package com.example.fucknotes.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.fucknotes.data.db.AttachmentEntity
import com.example.fucknotes.data.db.NoteEntity
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import com.example.fucknotes.data.ink.InkStroke
import com.example.fucknotes.data.model.AttachmentType
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.data.repo.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(val label: String, val extension: String, val mime: String) {
    PDF("PDF", "pdf", "application/pdf"),
    IMAGE("Image (PNG)", "png", "image/png"),
    TEXT("Plain text", "txt", "text/plain"),
    MARKDOWN("Markdown", "md", "text/markdown"),
}

/** Renders notes to shareable files. Everything lands in `cacheDir/exports` behind a FileProvider. */
class NoteExporter(private val context: Context, private val repo: NotesRepository) {

    /** Shared across every export; creating one per page would reload the native renderer. */
    private val inkRenderer by lazy { CanvasStrokeRenderer.create() }

    private companion object {
        /**
         * Ink world units are dp. One dp is 150/160 px on a 150 dpi page, so 1:1 is ~0.94 — but
         * a page of handwriting is usually wider than the sheet, so fitting normally scales down
         * and this only caps the enlargement of a small sketch.
         */
        const val EXPORT_MAX_SCALE = 150f / 160f
        const val PAGE_WIDTH = 1240   // A4 at ~150 dpi
        const val PAGE_HEIGHT = 1754
        const val MARGIN = 90f
        const val TITLE_SIZE = 44f
        const val BODY_SIZE = 28f
    }

    suspend fun export(note: NoteEntity, format: ExportFormat, darkStyle: Boolean = false): File =
        withContext(Dispatchers.IO) {
            val attachments = attachmentsOf(note.id)
            when (format) {
                ExportFormat.PDF -> exportPdf(listOf(note), attachments, darkStyle)
                ExportFormat.IMAGE -> exportImage(note, attachments, darkStyle)
                ExportFormat.TEXT -> exportText(listOf(note), markdown = false)
                ExportFormat.MARKDOWN -> exportText(listOf(note), markdown = true)
            }
        }

    /** Bulk export: every note goes into one document. */
    suspend fun exportAll(notes: List<NoteEntity>, format: ExportFormat, darkStyle: Boolean = false): File =
        withContext(Dispatchers.IO) {
            when (format) {
                ExportFormat.PDF -> exportPdf(notes, emptyList(), darkStyle)
                ExportFormat.IMAGE -> exportImage(notes.first(), attachmentsOf(notes.first().id), darkStyle)
                ExportFormat.TEXT -> exportText(notes, markdown = false)
                ExportFormat.MARKDOWN -> exportText(notes, markdown = true)
            }
        }

    private suspend fun attachmentsOf(noteId: Long): List<AttachmentEntity> =
        runCatching { repo.observeAttachments(noteId).first() }.getOrDefault(emptyList())

    // ---- text --------------------------------------------------------------

    private fun exportText(notes: List<NoteEntity>, markdown: Boolean): File {
        val text = notes.joinToString("\n\n---\n\n") { note -> renderText(note, markdown) }
        val file = outFile(notes.first(), if (markdown) "md" else "txt")
        file.writeText(text)
        return file
    }

    private fun renderText(note: NoteEntity, markdown: Boolean): String = buildString {
        val title = note.title.ifBlank { "Untitled" }
        if (markdown) appendLine("# $title") else appendLine(title)
        appendLine()
        when (note.type) {
            NoteType.CHECKLIST -> repo.checklistOf(note).forEach {
                appendLine(if (markdown) "- [${if (it.checked) "x" else " "}] ${it.text}" else "${if (it.checked) "[x]" else "[ ]"} ${it.text}")
            }

            NoteType.DRAWING, NoteType.NOTEBOOK -> {
                appendLine(if (markdown) "_(handwritten note — export as PDF or image to keep the ink)_" else "(handwritten note)")
                if (note.body.isNotBlank()) appendLine(note.body)
            }

            NoteType.TEXT -> appendLine(note.body)
        }
        appendLine()
        appendLine("Updated ${stamp(note.updatedAt)}")
    }

    // ---- image -------------------------------------------------------------

    private fun exportImage(note: NoteEntity, attachments: List<AttachmentEntity>, dark: Boolean): File {
        val bitmap = Bitmap.createBitmap(PAGE_WIDTH, PAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawNotePage(canvas, note, attachments, dark, pageIndex = 0)
        val file = outFile(note, "png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    // ---- pdf ---------------------------------------------------------------

    private fun exportPdf(notes: List<NoteEntity>, attachments: List<AttachmentEntity>, dark: Boolean): File {
        val document = PdfDocument()
        notes.forEachIndexed { index, note ->
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
            val page = document.startPage(info)
            drawNotePage(page.canvas, note, if (notes.size == 1) attachments else emptyList(), dark, index)
            document.finishPage(page)
        }
        val file = outFile(notes.first(), "pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return file
    }

    // ---- shared page painting ---------------------------------------------

    private fun drawNotePage(
        canvas: Canvas,
        note: NoteEntity,
        attachments: List<AttachmentEntity>,
        dark: Boolean,
        pageIndex: Int,
    ) {
        val background = if (dark) Color.parseColor("#12131A") else Color.WHITE
        val ink = if (dark) Color.parseColor("#E8E8EF") else Color.parseColor("#1B1B1F")
        val faint = if (dark) Color.parseColor("#2A2C36") else Color.parseColor("#E4E4EA")

        canvas.drawColor(background)
        drawPaper(canvas, note.paperStyle, faint)

        var y = MARGIN

        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            color = ink
            textSize = TITLE_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        y = drawWrapped(canvas, note.title.ifBlank { "Untitled" }, titlePaint, y)
        y += 12f

        val metaPaint = TextPaint().apply {
            isAntiAlias = true
            color = if (dark) Color.parseColor("#8A8C99") else Color.parseColor("#6C6E7A")
            textSize = 22f
        }
        y = drawWrapped(canvas, stamp(note.updatedAt), metaPaint, y)
        y += 28f

        val bodyPaint = TextPaint().apply {
            isAntiAlias = true
            color = ink
            textSize = BODY_SIZE
        }

        when (note.type) {
            NoteType.CHECKLIST -> repo.checklistOf(note).forEach { item ->
                val marker = if (item.checked) "☑" else "☐"
                y = drawWrapped(canvas, "$marker  ${item.text}", bodyPaint, y)
                y += 10f
            }

            else -> if (note.body.isNotBlank()) y = drawWrapped(canvas, note.body, bodyPaint, y)
        }

        // Ink sits on top of the text, matching how the editor layers them.
        drawInk(canvas, repo.inkOf(note))

        if (pageIndex == 0) drawImages(canvas, attachments, y)
    }

    private fun drawWrapped(canvas: Canvas, text: String, paint: TextPaint, top: Float): Float {
        if (text.isEmpty()) return top
        val width = (PAGE_WIDTH - MARGIN * 2).toInt()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(6f, 1f)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(MARGIN, top)
        layout.draw(canvas)
        canvas.restore()
        return top + layout.height
    }

    private fun drawPaper(canvas: Canvas, style: PaperStyle, color: Int) {
        val paint = Paint().apply {
            isAntiAlias = true
            this.color = color
            strokeWidth = 1.5f
        }
        val step = 56f
        when (style) {
            PaperStyle.PLAIN -> Unit
            PaperStyle.LINED -> {
                var y = MARGIN
                while (y < PAGE_HEIGHT - MARGIN) {
                    canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paint)
                    y += step
                }
            }

            PaperStyle.GRID -> {
                var y = MARGIN
                while (y < PAGE_HEIGHT - MARGIN) {
                    canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, paint); y += step
                }
                var x = MARGIN
                while (x < PAGE_WIDTH - MARGIN) {
                    canvas.drawLine(x, MARGIN, x, PAGE_HEIGHT - MARGIN, paint); x += step
                }
            }

            PaperStyle.DOTTED -> {
                val dot = Paint(paint).also { it.style = Paint.Style.FILL }
                var y = MARGIN
                while (y < PAGE_HEIGHT - MARGIN) {
                    var x = MARGIN
                    while (x < PAGE_WIDTH - MARGIN) {
                        canvas.drawCircle(x, y, 2.5f, dot); x += step
                    }
                    y += step
                }
            }
        }
    }

    /**
     * Replays the page's ink through the same renderer the editor uses, so an exported page shows
     * the real strokes rather than a re-approximation of them.
     *
     * Ink lives on an unbounded page in dp, while the export is a fixed A4 sheet, so the drawn
     * area is measured and scaled to fit inside the margins. Scale is uniform and never enlarges
     * past 1:1, which keeps small sketches at their natural size instead of blowing them up.
     */
    private fun drawInk(canvas: Canvas, strokes: List<InkStroke>) {
        if (strokes.isEmpty()) return

        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        strokes.forEach { inkStroke ->
            val box = inkStroke.stroke.shape.computeBoundingBox() ?: return@forEach
            left = minOf(left, box.xMin)
            top = minOf(top, box.yMin)
            right = maxOf(right, box.xMax)
            bottom = maxOf(bottom, box.yMax)
        }
        if (left > right || top > bottom) return

        val available = PAGE_WIDTH - MARGIN * 2
        val availableHeight = PAGE_HEIGHT - MARGIN * 2
        val scale = minOf(
            available / (right - left).coerceAtLeast(1f),
            availableHeight / (bottom - top).coerceAtLeast(1f),
            EXPORT_MAX_SCALE,
        )

        val transform = android.graphics.Matrix().apply {
            setTranslate(-left, -top)
            postScale(scale, scale)
            postTranslate(MARGIN, MARGIN)
        }
        // As on screen, the transform has to be on the canvas — the renderer only reads it to
        // choose a level of detail, it does not reposition the stroke.
        canvas.save()
        canvas.concat(transform)
        strokes.forEach { inkStroke ->
            runCatching { inkRenderer.draw(canvas, inkStroke.stroke, transform) }
        }
        canvas.restore()
    }

    private fun drawImages(canvas: Canvas, attachments: List<AttachmentEntity>, top: Float) {
        var y = top + 24f
        attachments.filter { it.type == AttachmentType.IMAGE }.forEach { attachment ->
            if (y > PAGE_HEIGHT - MARGIN - 100) return
            val bitmap = BitmapFactory.decodeFile(attachment.path) ?: return@forEach
            val maxWidth = PAGE_WIDTH - MARGIN * 2
            val scale = minOf(maxWidth / bitmap.width, (PAGE_HEIGHT - MARGIN - y) / bitmap.height, 1f)
            val target = android.graphics.RectF(
                MARGIN, y,
                MARGIN + bitmap.width * scale,
                y + bitmap.height * scale,
            )
            canvas.drawBitmap(bitmap, null, target, null)
            y = target.bottom + 20f
            bitmap.recycle()
        }
    }

    // ---- sharing -----------------------------------------------------------

    private fun outFile(note: NoteEntity, extension: String): File {
        val dir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val safe = note.title.ifBlank { "note" }.replace(Regex("[^A-Za-z0-9 _-]"), "").take(40).trim()
        return File(dir, "${safe.ifBlank { "note" }}-${System.currentTimeMillis()}.$extension")
    }

    fun shareIntent(file: File, mime: String): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun stamp(millis: Long): String =
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
}
