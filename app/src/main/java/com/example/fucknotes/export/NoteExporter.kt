package com.example.fucknotes.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import com.example.fucknotes.ui.editor.ink.forDarkReader
import com.example.fucknotes.ui.editor.ink.NightPalette
import androidx.compose.ui.graphics.toArgb
import android.graphics.ColorMatrixColorFilter
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.fucknotes.ServiceLocator
import com.example.fucknotes.data.db.AttachmentEntity
import com.example.fucknotes.data.db.NoteEntity
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import com.example.fucknotes.data.ink.InkStroke
import com.example.fucknotes.media.MediaStore
import com.example.fucknotes.data.ink.NotebookLayout
import com.example.fucknotes.data.ink.DEFAULT_RULE_SPACING_DP
import com.example.fucknotes.data.ink.PageSpec
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

    private val editableNotes by lazy { EditableNotes(context, repo, ServiceLocator.settings(context)) }

    private companion object {
        /**
         * Ink world units are dp. One dp is 150/160 px on a 150 dpi page, so 1:1 is ~0.94 — but
         * a page of handwriting is usually wider than the sheet, so fitting normally scales down
         * and this only caps the enlargement of a small sketch.
         */
        const val EXPORT_MAX_SCALE = 150f / 160f
        const val PAGE_WIDTH = 1240   // A4 at ~150 dpi
        const val PAGE_HEIGHT = 1754

        /**
         * Points to output pixels for a notebook sheet.
         *
         * Derived from the A4 page above so an A4 notebook exports exactly as it always has, while
         * a note written on another size comes out at the same resolution rather than stretched to
         * A4's pixel count.
         */
        const val PDF_PIXELS_PER_POINT = PAGE_WIDTH / 595f

        /** A stray stroke far down the column must not turn into thousands of blank sheets. */
        const val MAX_NOTEBOOK_PAGES = 500
        const val MARGIN = 90f
        const val TITLE_SIZE = 44f
        const val BODY_SIZE = 28f

        /** Paper marks on a notebook sheet, in points: what the screen shows at 100 %. */
        const val RULE_WIDTH_PT = 0.9f
        const val DOT_RADIUS_PT = 1.3f
        const val DOT_ALPHA = 200
    }

    suspend fun export(
        note: NoteEntity,
        format: ExportFormat,
        darkStyle: Boolean = false,
        /** PDF only: attach the note's own data so the file opens back into an editable note. */
        editable: Boolean = false,
    ): File =
        withContext(Dispatchers.IO) {
            val attachments = attachmentsOf(note.id)
            when (format) {
                ExportFormat.PDF -> exportPdf(mapOf(note.id to attachments), listOf(note), darkStyle).also { file ->
                    if (editable) editableNotes.embedInto(file, note, attachments)
                }
                ExportFormat.IMAGE -> exportImage(note, attachments, darkStyle)
                ExportFormat.TEXT -> exportText(listOf(note), markdown = false)
                ExportFormat.MARKDOWN -> exportText(listOf(note), markdown = true)
            }
        }

    /** The note as a PDF that can come back as a note: what Drive holds and what "Export as PDF" shares. */
    suspend fun exportEditablePdf(note: NoteEntity): File = export(note, ExportFormat.PDF, editable = true)

    /** Bulk export: every note goes into one document. */
    suspend fun exportAll(notes: List<NoteEntity>, format: ExportFormat, darkStyle: Boolean = false): File =
        withContext(Dispatchers.IO) {
            require(notes.isNotEmpty()) { "There are no notes to export" }
            val attachments = notes.associate { it.id to attachmentsOf(it.id) }
            when (format) {
                ExportFormat.PDF -> exportPdf(attachments, notes, darkStyle)
                ExportFormat.IMAGE -> exportImage(notes.first(), attachmentsOf(notes.first().id), darkStyle)
                ExportFormat.TEXT -> exportText(notes, markdown = false)
                ExportFormat.MARKDOWN -> exportText(notes, markdown = true)
            }
        }

    private suspend fun attachmentsOf(noteId: Long): List<AttachmentEntity> =
        repo.observeAttachments(noteId).first()

    // ---- text --------------------------------------------------------------

    private fun exportText(notes: List<NoteEntity>, markdown: Boolean): File {
        val text = notes.joinToString("\n\n---\n\n") { note -> renderText(note, markdown) }
        val file = outFile(notes.first(), if (markdown) "md" else "txt", notes.fileBaseName())
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
        val spec = PageSpec.decode(note.pageFormat)
        val width = if (note.type == NoteType.NOTEBOOK) (spec.widthDp * PDF_PIXELS_PER_POINT).toInt() else PAGE_WIDTH
        val height = if (note.type == NoteType.NOTEBOOK) (spec.heightDp * PDF_PIXELS_PER_POINT).toInt() else PAGE_HEIGHT
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (note.type == NoteType.NOTEBOOK) {
            drawNotebookPage(canvas, note, repo.inkOf(note), 0, dark, spec, width, height, attachments)
        } else drawNotePage(canvas, note, attachments, dark, pageIndex = 0)
        val file = outFile(note, "png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    // ---- pdf ---------------------------------------------------------------

    private fun exportPdf(
        attachmentsByNote: Map<Long, List<AttachmentEntity>>,
        notes: List<NoteEntity>,
        dark: Boolean,
    ): File {
        val document = PdfDocument()
        try {
            var pageNumber = 1
            notes.forEach { note ->
                val ownAttachments = attachmentsByNote[note.id].orEmpty()

                // A notebook is already paginated, so each sheet becomes a PDF page at the size it was
                // written on rather than being shrunk to fit alongside a title block.
                if (note.type == NoteType.NOTEBOOK) {
                    val strokes = repo.inkOf(note)
                    val spec = PageSpec.decode(note.pageFormat)
                    val sheetWidth = kotlin.math.ceil(spec.widthDp).toInt()
                    val sheetHeight = kotlin.math.ceil(spec.heightDp).toInt()
                    repeat(notebookPageCount(note)) { index ->
                        val info = PdfDocument.PageInfo.Builder(sheetWidth, sheetHeight, pageNumber++).create()
                        val page = document.startPage(info)
                        try {
                            drawNotebookPage(
                                page.canvas,
                                note,
                                strokes,
                                index,
                                dark,
                                spec,
                                sheetWidth,
                                sheetHeight,
                                ownAttachments,
                            )
                        } finally { document.finishPage(page) }
                    }
                    pageNumber = drawAttachmentPages(document, note, ownAttachments, dark, pageNumber)
                    return@forEach
                }

                if (note.type == NoteType.TEXT || note.type == NoteType.CHECKLIST) {
                    pageNumber = drawTextNotePages(document, note, dark, pageNumber)
                    pageNumber = drawAttachmentPages(document, note, ownAttachments, dark, pageNumber)
                    return@forEach
                }

                val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create()
                val page = document.startPage(info)
                try {
                    drawNotePage(page.canvas, note, ownAttachments, dark, 0)
                } finally { document.finishPage(page) }
                pageNumber = drawAttachmentPages(document, note, ownAttachments, dark, pageNumber)
            }
            val file = outFile(notes.first(), "pdf", notes.fileBaseName())
            file.outputStream().use { document.writeTo(it) }
            return file
        } finally { document.close() }
    }

    /** Writes every line of a text/checklist note, carrying overflow onto additional PDF pages. */
    private fun drawTextNotePages(
        document: PdfDocument,
        note: NoteEntity,
        dark: Boolean,
        firstPageNumber: Int,
    ): Int {
        val background = if (dark) Color.parseColor("#12131A") else Color.WHITE
        val ink = if (dark) Color.parseColor("#E8E8EF") else Color.parseColor("#1B1B1F")
        val faint = if (dark) Color.parseColor("#2A2C36") else Color.parseColor("#E4E4EA")
        val bodyPaint = TextPaint().apply {
            isAntiAlias = true
            color = ink
            textSize = BODY_SIZE
        }
        val body = pdfBodyText(note)
        val layout = StaticLayout.Builder.obtain(
            body,
            0,
            body.length,
            bodyPaint,
            (PAGE_WIDTH - MARGIN * 2).toInt(),
        )
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(6f, 1f)
            .setIncludePad(false)
            .build()

        var pageNumber = firstPageNumber
        var line = 0
        var contentTop = 0f
        val strokes = repo.inkOf(note)
        val inkLeft = minOf(0f, strokes.minOfOrNull { it.stroke.shape.computeBoundingBox()?.xMin ?: 0f } ?: 0f)
        val inkRight = maxOf(1f, strokes.maxOfOrNull { it.stroke.shape.computeBoundingBox()?.xMax ?: 0f } ?: 1f)
        val inkScale = minOf(BODY_SIZE / 16f, (PAGE_WIDTH - MARGIN * 2) / (inkRight - inkLeft))
        val inkBottom = strokes.maxOfOrNull { (it.stroke.shape.computeBoundingBox()?.yMax ?: 0f) * inkScale } ?: 0f
        do {
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create()
            val page = document.startPage(info)
            try {
                page.canvas.drawColor(background)
                drawPaper(page.canvas, note.paperStyle, faint)
                val bodyTop = drawPdfHeader(page.canvas, note, dark)
                val availableHeight = (PAGE_HEIGHT - MARGIN - bodyTop).coerceAtLeast(1f)
                val firstLineTop = contentTop.toInt()
                var endLine = line
                while (endLine < layout.lineCount &&
                    layout.getLineBottom(endLine) - firstLineTop <= availableHeight
                ) {
                    endLine++
                }
                if (endLine == line && line < layout.lineCount) endLine++

                page.canvas.save()
                page.canvas.clipRect(
                    MARGIN,
                    bodyTop,
                    PAGE_WIDTH - MARGIN,
                    PAGE_HEIGHT - MARGIN,
                )
                page.canvas.translate(MARGIN, bodyTop - firstLineTop)
                layout.draw(page.canvas)
                page.canvas.restore()

                page.canvas.save()
                page.canvas.clipRect(MARGIN, bodyTop, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - MARGIN)
                val transform = android.graphics.Matrix().apply {
                    setScale(inkScale, inkScale)
                    postTranslate(MARGIN - inkLeft * inkScale, bodyTop - contentTop)
                }
                page.canvas.concat(transform)
                strokes.forEach { inkRenderer.draw(page.canvas, it.stroke, transform) }
                page.canvas.restore()
                line = endLine
                contentTop = if (endLine < layout.lineCount) layout.getLineTop(endLine).toFloat()
                    else contentTop + availableHeight
            } finally { document.finishPage(page) }
        } while (line < layout.lineCount || contentTop < inkBottom)

        return pageNumber
    }

    /** Adds attachment pages so images are never silently dropped when the note body is full. */
    private fun drawAttachmentPages(
        document: PdfDocument,
        note: NoteEntity,
        attachments: List<AttachmentEntity>,
        dark: Boolean,
        firstPageNumber: Int,
    ): Int {
        var pageNumber = firstPageNumber
        val background = if (dark) Color.parseColor("#12131A") else Color.WHITE
        val faint = if (dark) Color.parseColor("#2A2C36") else Color.parseColor("#E4E4EA")
        attachments.filter { it.type == AttachmentType.IMAGE && it.pageIndex == null }.forEach { attachment ->
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber++).create()
            val page = document.startPage(info)
            try {
                page.canvas.drawColor(background)
                drawPaper(page.canvas, note.paperStyle, faint)
                val top = drawPdfHeader(page.canvas, note, dark) + 20f
                val bitmap = MediaStore.decodeImage(File(attachment.path))
                try {
                    val maxWidth = PAGE_WIDTH - MARGIN * 2
                    val maxHeight = PAGE_HEIGHT - top - MARGIN
                    val scale = minOf(maxWidth / bitmap.width, maxHeight / bitmap.height, 1f)
                    val target = android.graphics.RectF(
                        MARGIN,
                        top,
                        MARGIN + bitmap.width * scale,
                        top + bitmap.height * scale,
                    )
                    page.canvas.drawBitmap(bitmap, null, target, null)
                } finally { bitmap.recycle() }
            } finally { document.finishPage(page) }
        }
        return pageNumber
    }

    private fun pdfBodyText(note: NoteEntity): String = when (note.type) {
        NoteType.CHECKLIST -> repo.checklistOf(note).joinToString("\n") {
            "${if (it.checked) "[x]" else "[ ]"}  ${it.text}"
        }
        else -> note.body
    }

    /** Draws the title and updated timestamp and returns the first body baseline. */
    private fun drawPdfHeader(canvas: Canvas, note: NoteEntity, dark: Boolean): Float {
        val ink = if (dark) Color.parseColor("#E8E8EF") else Color.parseColor("#1B1B1F")
        var y = MARGIN
        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            color = ink
            textSize = TITLE_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        y = drawWrapped(canvas, note.title.ifBlank { "Untitled" }, titlePaint, y, maxLines = 3)
        y += 12f
        val metaPaint = TextPaint().apply {
            isAntiAlias = true
            color = if (dark) Color.parseColor("#8A8C99") else Color.parseColor("#6C6E7A")
            textSize = 22f
        }
        y = drawWrapped(canvas, stamp(note.updatedAt), metaPaint, y)
        return y + 28f
    }

    // ---- notebook pages ----------------------------------------------------

    /** Preserve saved pages, including counts recorded before explicit page creation was added. */
    private fun notebookPageCount(note: NoteEntity): Int {
        // New pages are explicit; never infer additional sheets from new stroke bounds.
        return note.pageCount.coerceIn(1, MAX_NOTEBOOK_PAGES)
    }

    /**
     * One notebook sheet, 1:1.
     *
     * The page is measured in points and the output is the same page at 150 dpi, so the whole
     * transform is a single uniform scale — ink comes out the size it was written, with nothing
     * fitted or cropped.
     */
    private fun drawNotebookPage(
        canvas: Canvas,
        note: NoteEntity,
        strokes: List<InkStroke>,
        pageIndex: Int,
        dark: Boolean,
        spec: PageSpec,
        sheetWidth: Int,
        sheetHeight: Int,
        attachments: List<AttachmentEntity> = emptyList(),
    ) {
        // A dark export is the page as dark mode shows it: the night slate, night rules, every
        // image through the night matrix and every stroke in its night colour. Drawing the stored
        // ink as is onto a dark sheet left black handwriting invisible.
        val background = when {
            dark -> NightPalette.paper.toArgb()
            note.colorArgb != 0 -> note.colorArgb
            else -> Color.WHITE
        }
        val faint = when {
            dark -> NightPalette.rule.toArgb()
            note.lineColorArgb != 0 -> note.lineColorArgb
            else -> Color.parseColor("#E4E4EA")
        }
        canvas.drawColor(background)

        val scale = sheetWidth / spec.widthDp
        val pageTop = NotebookLayout.topOf(pageIndex, spec)

        drawNotebookPaper(canvas, note, faint, scale, sheetWidth, sheetHeight)

        canvas.save()
        canvas.scale(scale, scale)
        attachments.filter { it.pageIndex == pageIndex }.forEach { image ->
            val bitmap = MediaStore.decodeImage(File(image.path))
            try {
                canvas.drawBitmap(bitmap, null, android.graphics.RectF(image.pageX, image.pageY,
                    image.pageX + image.pageWidth, image.pageY + image.pageHeight), imagePaint(dark))
            } finally { bitmap.recycle() }
        }
        canvas.restore()

        if (strokes.isNotEmpty()) {
            val transform = android.graphics.Matrix().apply {
                setTranslate(0f, -pageTop)
                postScale(scale, scale)
            }
            canvas.save()
            // Clipped to the sheet, so a stroke that runs past the bottom edge stays on its own page.
            canvas.clipRect(0f, 0f, sheetWidth.toFloat(), sheetHeight.toFloat())
            canvas.concat(transform)
            strokes.forEach { inkStroke ->
                inkRenderer.draw(canvas, if (dark) inkStroke.forDarkReader() else inkStroke.stroke, transform)
            }
            canvas.restore()
        }
    }

    /** Rules for a notebook sheet: full bleed, at the spacing the note was written on. */
    private fun drawNotebookPaper(
        canvas: Canvas,
        note: NoteEntity,
        color: Int,
        scale: Float,
        sheetWidth: Int,
        sheetHeight: Int,
    ) {
        val style = note.paperStyle
        if (style == PaperStyle.PLAIN) return
        // Sized as the page draws them: a rule is a hairline, a dot a little under 3 points
        // across and a touch translucent. Export dots were twice that and read as a grid of
        // beads over the writing.
        val paint = Paint().apply {
            isAntiAlias = true
            this.color = color
            strokeWidth = RULE_WIDTH_PT * scale
        }
        val dot = Paint(paint).apply { this.style = Paint.Style.FILL; alpha = DOT_ALPHA }
        val spacingDp = note.lineSpacingDp.takeIf { it > 0 }?.toFloat() ?: DEFAULT_RULE_SPACING_DP
        val step = spacingDp * scale
        if (step < 2f) return
        var y = step
        while (y < sheetHeight) {
            if (style == PaperStyle.LINED || style == PaperStyle.GRID) {
                canvas.drawLine(0f, y, sheetWidth.toFloat(), y, paint)
            }
            if (style == PaperStyle.DOTTED) {
                var x = step
                while (x < sheetWidth) {
                    canvas.drawCircle(x, y, DOT_RADIUS_PT * scale, dot)
                    x += step
                }
            }
            y += step
        }
        if (style == PaperStyle.GRID) {
            var x = step
            while (x < sheetWidth) {
                canvas.drawLine(x, 0f, x, sheetHeight.toFloat(), paint)
                x += step
            }
        }
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
        y = drawWrapped(canvas, note.title.ifBlank { "Untitled" }, titlePaint, y, maxLines = 3)
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
        drawInk(canvas, repo.inkOf(note), y + 24f, attachments, PageSpec.decode(note.pageFormat), dark)
    }

    private fun drawWrapped(canvas: Canvas, text: String, paint: TextPaint, top: Float, maxLines: Int = Int.MAX_VALUE): Float {
        if (text.isEmpty()) return top
        val width = (PAGE_WIDTH - MARGIN * 2).toInt()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
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
    private fun drawInk(canvas: Canvas, strokes: List<InkStroke>, bodyTop: Float,
        attachments: List<AttachmentEntity> = emptyList(), spec: PageSpec = PageSpec.Default, dark: Boolean = false) {
        val images = attachments.filter { it.pageIndex != null }
        if (strokes.isEmpty() && images.isEmpty()) return

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
        images.forEach { image ->
            val y = NotebookLayout.topOf(image.pageIndex ?: 0, spec) + image.pageY
            left = minOf(left, image.pageX); top = minOf(top, y)
            right = maxOf(right, image.pageX + image.pageWidth); bottom = maxOf(bottom, y + image.pageHeight)
        }
        if (left > right || top > bottom) return

        val available = PAGE_WIDTH - MARGIN * 2
        val availableHeight = (PAGE_HEIGHT - MARGIN - bodyTop).coerceAtLeast(1f)
        val scale = minOf(
            available / (right - left).coerceAtLeast(1f),
            availableHeight / (bottom - top).coerceAtLeast(1f),
            EXPORT_MAX_SCALE,
        )

        val transform = android.graphics.Matrix().apply {
            setTranslate(-left, -top)
            postScale(scale, scale)
            postTranslate(MARGIN, bodyTop)
        }
        // As on screen, the transform has to be on the canvas — the renderer only reads it to
        // choose a level of detail, it does not reposition the stroke.
        canvas.save()
        canvas.clipRect(MARGIN, bodyTop, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - MARGIN)
        canvas.concat(transform)
        images.forEach { image ->
            val bitmap = MediaStore.decodeImage(File(image.path))
            try {
                val y = NotebookLayout.topOf(image.pageIndex ?: 0, spec) + image.pageY
                canvas.drawBitmap(bitmap, null, android.graphics.RectF(image.pageX, y,
                    image.pageX + image.pageWidth, y + image.pageHeight), imagePaint(dark))
            } finally { bitmap.recycle() }
        }
        strokes.forEach { inkStroke ->
            inkRenderer.draw(canvas, if (dark) inkStroke.forDarkReader() else inkStroke.stroke, transform)
        }
        canvas.restore()
    }

    private fun drawImages(
        canvas: Canvas,
        attachments: List<AttachmentEntity>,
        top: Float,
        pageWidth: Int,
        pageHeight: Int,
    ) {
        var y = top + 24f
        attachments.filter { it.type == AttachmentType.IMAGE && it.pageIndex == null }.forEach { attachment ->
            if (y > pageHeight - MARGIN - 100) return
            val bitmap = BitmapFactory.decodeFile(attachment.path) ?: return@forEach
            val maxWidth = pageWidth - MARGIN * 2
            val scale = minOf(maxWidth / bitmap.width, (pageHeight - MARGIN - y) / bitmap.height, 1f)
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

    /** Bitmap paint for a page image: filtered, and through the night matrix on a dark export. */
    private fun imagePaint(dark: Boolean): Paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        if (dark) colorFilter = ColorMatrixColorFilter(NightPalette.imageMatrix)
    }

    // ---- sharing -----------------------------------------------------------

    private fun outFile(note: NoteEntity, extension: String, baseName: String? = null): File {
        val dir = File(context.cacheDir, "exports/${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val safe = ExportNames.safeBase(baseName ?: note.title)
        return File(dir, "$safe.$extension")
    }

    private fun List<NoteEntity>.fileBaseName(): String = when {
        size == 1 -> first().title.ifBlank { "note" }
        else -> "Notes ($size)"
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
