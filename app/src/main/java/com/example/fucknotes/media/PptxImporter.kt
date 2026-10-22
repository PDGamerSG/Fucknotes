package com.example.fucknotes.media

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.fucknotes.data.repo.NotesRepository
import com.example.fucknotes.media.pptx.PptxDeck
import com.example.fucknotes.media.pptx.PptxRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.ceil

/**
 * Imports a PowerPoint deck by converting it to a PDF first.
 *
 * Each slide is painted onto a PDF page of the slide's own size, so the result is the same kind
 * of document a PDF import handles: the pages become image backgrounds with editable ink above
 * them, the first slide becomes the shelf preview, and "Export as PDF" gives the deck back.
 *
 * Only `.pptx` (Office Open XML) is supported; the old binary `.ppt` format has no reader here.
 */
class PptxImporter(private val context: Context, private val repo: NotesRepository) {

    suspend fun import(source: Uri, folderId: Long?, onProgress: (String) -> Unit = {}): Long =
        withContext(Dispatchers.IO) {
            val name = MediaStore.displayName(context, source)
            val base = name.replace(Regex("(?i)\\.pptx?$"), "").ifBlank { "Presentation" }
            val work = File(context.cacheDir, "pptx-${UUID.randomUUID()}").apply { mkdirs() }
            val deckFile = File(work, "deck.pptx")
            // Named after the deck so the notebook takes the deck's title through the PDF path.
            val pdfFile = File(work, "${ExportNamesSafe.base(base)}.pdf")
            try {
                context.contentResolver.openInputStream(source)?.use { input ->
                    deckFile.outputStream().use { input.copyTo(it) }
                } ?: error("The presentation could not be opened")
                requireOoxml(deckFile)

                onProgress("Opening presentation…")
                PptxDeck.open(deckFile).use { deck ->
                    val renderer = PptxRenderer(deck)
                    val document = PdfDocument()
                    try {
                        val width = ceil(deck.slideWidthEmu / PptxRenderer.EMU_PER_POINT).toInt().coerceAtLeast(72)
                        val height = ceil(deck.slideHeightEmu / PptxRenderer.EMU_PER_POINT).toInt().coerceAtLeast(72)
                        deck.slides.forEachIndexed { index, slide ->
                            currentCoroutineContext().ensureActive()
                            onProgress("Converting slide ${index + 1} of ${deck.slides.size}…")
                            val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
                            try {
                                renderer.render(slide, page.canvas)
                            } finally { document.finishPage(page) }
                        }
                        pdfFile.outputStream().use { document.writeTo(it) }
                    } finally {
                        document.close()
                        renderer.recycle()
                    }
                }
                PdfImporter(context, repo).import(Uri.fromFile(pdfFile), folderId) { done, total ->
                    onProgress("Importing slide $done of $total…")
                }
            } finally {
                work.deleteRecursively()
            }
        }

    /** A `.pptx` is a zip; the legacy `.ppt` is an OLE compound file and cannot be read here. */
    private fun requireOoxml(file: File) {
        val head = file.inputStream().use { input -> ByteArray(4).also { input.read(it) } }
        val isZip = head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
        val isOle = head[0] == 0xD0.toByte() && head[1] == 0xCF.toByte() && head[2] == 0x11.toByte() && head[3] == 0xE0.toByte()
        require(!isOle) { "This is an old .ppt file. Save it as .pptx in PowerPoint, then import it." }
        require(isZip) { "This file is not a PowerPoint presentation" }
    }
}

/** File-name safety for the converted PDF, without pulling the export package into media. */
private object ExportNamesSafe {
    fun base(title: String): String = com.example.fucknotes.export.ExportNames.safeBase(title)
}
