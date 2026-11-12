package com.example.fucknotes.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.ink.PageSpec
import com.example.fucknotes.data.ink.InkStroke
import com.example.fucknotes.data.ink.NotebookLayout
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.ui.common.ImmersiveDialogWindow
import com.example.fucknotes.ui.common.PaperPreview
import com.example.fucknotes.ui.common.paperColorOr
import com.example.fucknotes.ui.theme.brand
import androidx.compose.runtime.remember
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import com.example.fucknotes.ui.editor.ink.NightPalette
import com.example.fucknotes.ui.editor.ink.forDarkReader

/**
 * Every page of the notebook, to jump between.
 *
 * Each thumbnail includes the ink that belongs to that page. The same Ink renderer is used as the
 * editor, with a page-local transform, so the preview is an actual miniature of the sheet rather
 * than a blank paper placeholder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesSheet(
    pageCount: Int,
    currentPage: Int,
    style: PaperStyle,
    paperColorArgb: Int,
    lineColorArgb: Int,
    spacingDp: Float,
    page: PageSpec,
    strokes: List<InkStroke>,
    attachments: List<com.example.fucknotes.data.db.AttachmentEntity>,
    onGoToPage: (Int) -> Unit,
    onDismiss: () -> Unit,
    /** Thumbnails follow the page: night colours when dark mode is on. */
    darkReader: Boolean = false,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ImmersiveDialogWindow()
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Pages", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "$pageCount ${if (pageCount == 1) "page" else "pages"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                items((0 until pageCount).toList()) { index ->
                    PageThumb(
                        index = index,
                        current = index == currentPage,
                        style = style,
                        paperColorArgb = paperColorArgb,
                        lineColorArgb = lineColorArgb,
                        spacingDp = spacingDp,
                        page = page,
                        strokes = strokes,
                        attachments = attachments.filter { it.pageIndex == index },
                        onClick = { onGoToPage(index) },
                        darkReader = darkReader,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageThumb(
    index: Int,
    current: Boolean,
    style: PaperStyle,
    paperColorArgb: Int,
    lineColorArgb: Int,
    spacingDp: Float,
    page: PageSpec,
    strokes: List<InkStroke>,
    attachments: List<com.example.fucknotes.data.db.AttachmentEntity>,
    onClick: () -> Unit,
    darkReader: Boolean = false,
) {
    val renderer = remember { CanvasStrokeRenderer.create() }
    val images = com.example.fucknotes.ui.editor.ink.rememberPageImages(attachments, 512)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(page.widthDp / page.heightDp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .then(
                    if (current) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    } else {
                        Modifier
                    }
                )
        ) {
            PaperPreview(
                style = style,
                modifier = Modifier.fillMaxSize(),
                paperColor = if (darkReader) NightPalette.paper else paperColorArgb.paperColorOr(Color.White),
                lineColor = if (darkReader) NightPalette.rule else lineColorArgb.paperColorOr(MaterialTheme.brand.tileBorder),
                spacingDp = spacingDp,
                zoom = 0.16f,
                corner = 8.dp,
            )
            Canvas(Modifier.fillMaxSize()) {
                val scale = size.width / page.widthDp.coerceAtLeast(1f)
                val pageTop = NotebookLayout.topOf(index, page)
                val pageBottom = pageTop + page.heightDp
                val transform = android.graphics.Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(0f, -pageTop * scale)
                }
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.clipRect(0f, 0f, size.width, size.height)
                    native.concat(transform)
                    attachments.forEach { image ->
                        images[image.id]?.let { bitmap ->
                            com.example.fucknotes.ui.editor.ink.drawPageImage(native, image, bitmap, page, darkReader = darkReader)
                        }
                    }
                    strokes.forEach { inkStroke ->
                        val box = inkStroke.stroke.shape.computeBoundingBox() ?: return@forEach
                        if (box.xMax >= 0f && box.xMin <= page.widthDp &&
                            box.yMax >= pageTop && box.yMin <= pageBottom
                        ) {
                            runCatching { renderer.draw(native, if (darkReader) inkStroke.forDarkReader() else inkStroke.stroke, transform) }
                        }
                    }
                    native.restore()
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${index + 1}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
            color = if (current) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
