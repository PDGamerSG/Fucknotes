package com.example.fucknotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.ink.strokes.Stroke
import com.example.fucknotes.data.ink.InkStroke
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.ui.common.paperColorOr
import com.example.fucknotes.ui.editor.ink.DefaultRuleColor
import com.example.fucknotes.ui.editor.ink.EditorQuickBar
import com.example.fucknotes.ui.editor.ink.InkCamera
import com.example.fucknotes.ui.editor.ink.InkSurface
import com.example.fucknotes.ui.editor.ink.InkToolbar
import com.example.fucknotes.ui.editor.ink.PageIndicator
import com.example.fucknotes.ui.editor.ink.PaperWhite
import com.example.fucknotes.ui.editor.ink.ZoomChip
import com.example.fucknotes.ui.theme.brand

/**
 * A writing page and the chrome that floats over it.
 *
 * Nothing is docked: a canvas or notebook note is the page, and a docked bar would take a strip of
 * it on every screen the app runs on. The bars sit in the corners instead, so the only fixed cost
 * is the corner a hand rests in anyway.
 *
 * The camera's position is read inside [ZoomReadout] and [PageCounter] rather than here, which
 * keeps a pan gesture from recomposing this function — re-supplying the ink layer's parameters
 * mid-stroke is what makes wet ink flicker.
 */
@Composable
fun InkEditor(
    state: EditorUiState,
    strokes: () -> List<InkStroke>,
    camera: InkCamera,
    canUndo: Boolean,
    canRedo: Boolean,
    /**
     * Read as a lambda so the count is only observed inside the counter chip. Taken by value, a
     * stroke that reaches a new page would recompose this whole function — and re-supply the ink
     * layer's parameters at the exact moment a stroke is being handed over.
     */
    pageCount: () -> Int,
    onStrokesFinished: (List<Stroke>) -> Unit,
    onErase: (Set<Int>) -> Unit,
    onToggleEraser: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSelectPen: (Int) -> Unit,
    onEditPen: (index: Int, colorArgb: Int, widthDp: Float) -> Unit,
    onSelectTool: (InkTool) -> Unit,
    onToggleFingerDrawing: () -> Unit,
    onToggleZoomLock: () -> Unit,
    onAppendPage: () -> Int,
    onBack: () -> Unit,
    onPages: () -> Unit,
    onPaper: () -> Unit,
    onAddImage: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = MaterialTheme.brand
    val notebook = state.type == NoteType.NOTEBOOK

    Box(modifier.fillMaxSize().background(brand.desk)) {
        InkSurface(
            strokes = strokes,
            camera = camera,
            tool = state.tool,
            colorArgb = state.activePen.colorArgb,
            widthDp = state.activePen.widthDp,
            enabled = state.inkActive,
            fingerDrawing = state.fingerDrawing,
            zoomLocked = state.zoomLocked,
            notebook = notebook,
            paperStyle = state.paperStyle,
            pageSpec = state.page,
            ruleColor = state.lineColorArgb.paperColorOr(DefaultRuleColor),
            ruleSpacingDp = state.ruleSpacingDp,
            deskColor = brand.desk,
            onStrokesFinished = onStrokesFinished,
            onErase = onErase,
            onToggleEraser = onToggleEraser,
            modifier = Modifier.fillMaxSize(),
            pageColor = state.colorArgb.paperColorOr(PaperWhite),
        )

        // Both bars share one row rather than being pinned to opposite corners: on a phone the pen
        // tray is wider than the space left over, and a corner-pinned tray would simply cover the
        // quick bar. Here the tray takes what is left and scrolls inside it.
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            EditorQuickBar(
                canUndo = canUndo,
                canRedo = canRedo,
                notebook = notebook,
                onBack = onBack,
                onPages = onPages,
                onUndo = onUndo,
                onRedo = onRedo,
                onAddPage = { camera.goToPage(onAppendPage(), state.page) },
                onResetView = camera::reset,
                onMore = onMore,
            )

            Spacer(Modifier.weight(1f))

            InkToolbar(
                pens = state.pens,
                selectedPen = state.selectedPen,
                tool = state.tool,
                fingerDrawing = state.fingerDrawing,
                zoomLocked = state.zoomLocked,
                onSelectPen = onSelectPen,
                onEditPen = onEditPen,
                onSelectTool = onSelectTool,
                onToggleFingerDrawing = onToggleFingerDrawing,
                onToggleZoomLock = onToggleZoomLock,
                onAddImage = onAddImage,
                onPaperStyle = onPaper,
            )
        }

        ZoomReadout(
            camera = camera,
            locked = state.zoomLocked,
            onToggleLock = onToggleZoomLock,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        )

        if (notebook) {
            PageCounter(
                camera = camera,
                state = state,
                pageCount = pageCount,
                onAppendPage = onAppendPage,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }
}

/** Reads the camera's scale, and only this chip repaints when it changes. */
@Composable
private fun ZoomReadout(
    camera: InkCamera,
    locked: Boolean,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ZoomChip(zoom = camera.zoom, locked = locked, onToggleLock = onToggleLock, modifier = modifier)
}

/**
 * Reads the camera's position, and only this chip repaints while the page scrolls.
 *
 * Stepping past the last page claims a new one rather than refusing: a notebook grows from the end,
 * and the arrow is the natural place to ask for another sheet.
 */
@Composable
private fun PageCounter(
    camera: InkCamera,
    state: EditorUiState,
    pageCount: () -> Int,
    onAppendPage: () -> Int,
    modifier: Modifier = Modifier,
) {
    val current = camera.pageAtCentre(state.page)
    val total = pageCount()
    PageIndicator(
        current = (current + 1).coerceAtMost(total),
        total = total,
        onPrevious = { camera.goToPage(current - 1, state.page) },
        onNext = {
            val next = current + 1
            if (next >= total) camera.goToPage(onAppendPage(), state.page)
            else camera.goToPage(next, state.page)
        },
        modifier = modifier,
    )
}
