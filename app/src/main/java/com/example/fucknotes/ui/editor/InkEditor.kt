package com.example.fucknotes.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.ink.strokes.Stroke
import com.example.fucknotes.data.db.AttachmentEntity
import com.example.fucknotes.data.ink.InkStroke
import com.example.fucknotes.data.model.FingerInputMode
import com.example.fucknotes.data.model.InkTool
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.ui.common.FloatingPanel
import com.example.fucknotes.ui.common.ToolButton
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
 * The corners are fixed so nothing ever overlaps:
 *  - top-left: the note bar (back, pages, undo, redo, add page, more) — [EditorChrome.FULL] only;
 *  - top-right: the pen bar, or in reading mode a single unlock button;
 *  - bottom-left: the zoom readout and pinch lock — hidden while reading;
 *  - bottom-right: the page counter with previous/next — always, for a notebook.
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
    onFingerInputMode: (FingerInputMode) -> Unit,
    onToggleZoomLock: () -> Unit,
    chrome: EditorChrome,
    onCycleChrome: () -> Unit,
    onAppendPage: () -> Int,
    onBack: () -> Unit,
    onPages: () -> Unit,
    attachments: List<AttachmentEntity>,
    playingPath: String?,
    onPlayAttachment: (String) -> Unit,
    onRemoveAttachment: (AttachmentEntity) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = MaterialTheme.brand
    val notebook = state.type == NoteType.NOTEBOOK

    Box(modifier.fillMaxSize().background(brand.desk)) {
        InkSurface(
            strokes = strokes,
            attachments = attachments,
            pageCount = pageCount,
            camera = camera,
            tool = state.tool,
            colorArgb = state.activePen.colorArgb,
            widthDp = state.activePen.widthDp,
            enabled = state.inkActive,
            fingerInputMode = state.fingerInputMode,
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
            pageColor = state.colorArgb.paperColorOr(PaperWhite),
            darkReader = state.darkReader,
            reading = chrome == EditorChrome.READING,
            modifier = Modifier
                .fillMaxSize()
                .then(if (state.zoomLocked) Modifier.systemGestureExclusion() else Modifier),
        )

        val quickBar: @Composable () -> Unit = {
            EditorQuickBar(
                canUndo = canUndo, canRedo = canRedo, notebook = notebook,
                onBack = onBack, onPages = onPages, onUndo = onUndo, onRedo = onRedo,
                onAddPage = { onAppendPage() }, onMore = onMore,
            )
        }
        val penBar: @Composable (Modifier) -> Unit = { barModifier ->
            InkToolbar(
                pens = state.pens, selectedPen = state.selectedPen, tool = state.tool,
                fingerInputMode = state.fingerInputMode, onFingerInputMode = onFingerInputMode,
                chrome = chrome, onSelectPen = onSelectPen, onEditPen = onEditPen,
                onSelectTool = onSelectTool, onCycleChrome = onCycleChrome, modifier = barModifier,
            )
        }

        // Each chrome state animates in and out, so the lock reads as a change of mode rather
        // than a flicker of controls.
        AnimatedVisibility(
            visible = chrome == EditorChrome.FULL,
            enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { -it / 3 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 3 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(8.dp)) {
                if (maxWidth < 900.dp) {
                    // Too narrow for both bars side by side: stack them, note bar first.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        quickBar()
                        penBar(Modifier.align(Alignment.End))
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        quickBar()
                        Spacer(Modifier.weight(1f))
                        penBar(Modifier)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = chrome == EditorChrome.COMPACT,
            enter = fadeIn(tween(160)) + slideInVertically(tween(200)) { -it / 3 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 3 },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            penBar(Modifier.padding(8.dp))
        }

        AnimatedVisibility(
            visible = chrome == EditorChrome.READING,
            enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.8f),
            exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.8f),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            ReadingUnlock(onClick = onCycleChrome, modifier = Modifier.padding(12.dp))
        }

        AnimatedVisibility(
            visible = chrome != EditorChrome.READING,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            ZoomReadout(
                camera = camera,
                locked = state.zoomLocked,
                onToggleLock = onToggleZoomLock,
                modifier = Modifier.padding(16.dp),
            )
        }

        if (notebook) {
            PageCounter(
                camera = camera,
                state = state,
                pageCount = pageCount,
                onAppendPage = onAppendPage,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            )
        }

        // Ink notes used to accept a picked image but offered no attachment surface, which made
        // the successful import look like a no-op. Keep the strip on the page so pictures and
        // recordings are visible immediately and can be removed without leaving the notebook.
        AnimatedVisibility(
            visible = chrome == EditorChrome.FULL,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            AttachmentStrip(
                attachments = attachments.filter { it.pageIndex == null },
                playingPath = playingPath,
                onPlay = onPlayAttachment,
                onRemove = onRemoveAttachment,
                modifier = Modifier.padding(start = 96.dp, end = 96.dp, bottom = 64.dp),
            )
        }
    }
}

/**
 * The only control left on a page being read.
 *
 * It is the same lock that cleared the page, so the way back is where the way in was: one tap
 * restores every control.
 */
@Composable
private fun ReadingUnlock(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingPanel(modifier, shape = CircleShape, elevation = 6.dp) {
        ToolButton(
            icon = Icons.Outlined.Lock,
            label = EditorChrome.READING.lockLabel,
            selected = true,
            onClick = onClick,
            size = 44.dp,
        )
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
    // A freehand drag can carry the camera past the last created sheet. Keep the controls bound to
    // the real notebook range so the counter and previous/next actions never point at a phantom
    // page.
    val boundedCurrent = current.coerceIn(0, (total - 1).coerceAtLeast(0))
    PageIndicator(
        current = boundedCurrent + 1,
        total = total,
        onPrevious = { camera.goToPage(boundedCurrent - 1, state.page) },
        onNext = {
            val next = boundedCurrent + 1
            if (next >= total) camera.goToPage(onAppendPage(), state.page)
            else camera.goToPage(next, state.page)
        },
        modifier = modifier,
    )
}
