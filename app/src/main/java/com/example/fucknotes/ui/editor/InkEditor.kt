package com.example.fucknotes.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
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
import com.example.fucknotes.ui.editor.ink.LassoState
import com.example.fucknotes.ui.editor.ink.Lasso
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.ink.strokes.Stroke
import com.example.fucknotes.data.db.AttachmentEntity
import com.example.fucknotes.data.ink.InkStroke
import com.example.fucknotes.data.ink.InkShape
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
import com.example.fucknotes.ui.editor.ink.controlAlpha
import com.example.fucknotes.ui.theme.brand
import kotlinx.coroutines.delay

/** Narrower than this and the two bars cannot share a row even with the pen bar folded. */
private val STACKED_BARS_BELOW = 560.dp

/**
 * A writing page and the chrome that floats over it.
 *
 * Nothing is docked: a canvas or notebook note is the page, and a docked bar would take a strip of
 * it on every screen the app runs on. The bars sit in the corners instead, so the only fixed cost
 * is the corner a hand rests in anyway.
 *
 * While the chrome is locked or the page is being read, the surface also opts out of the system's
 * edge gestures: a swipe from the edge is then just a swipe on the page, and cannot leave the note
 * or summon the bars. With every control out, the edges belong to the system again so the back
 * gesture works.
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
    /** Circled strokes dragged: indices and the world-unit shift. */
    onMoveStrokes: (Set<Int>, Float, Float) -> Unit = { _, _, _ -> },
    /** Circled strokes copied with an offset; returns the copies' indices. */
    onDuplicateStrokes: (Set<Int>, Float, Float) -> Set<Int> = { _, _, _ -> emptySet() },
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
    onLassoColor: (Set<Int>) -> Unit = {},
    quickHighlighterVisible: Boolean = false,
    onToggleQuickHighlighter: () -> Unit = {},
    onSelectShape: (InkShape) -> Unit = {},
    onShapeFinished: (Stroke, InkShape) -> Unit = { _, _ -> },
    onSketchyShapes: (Boolean) -> Unit = {},
) {
    val brand = MaterialTheme.brand
    val notebook = state.type == NoteType.NOTEBOOK
    val lasso = remember { LassoState() }
    var controlsAwake by remember { mutableStateOf(true) }
    var controlsWakeToken by remember { mutableStateOf(0) }

    fun wakeControls() {
        controlsAwake = true
        controlsWakeToken++
    }

    LaunchedEffect(controlsWakeToken) {
        delay(2600)
        controlsAwake = false
    }
    val controlsVisible = controlsAwake || lasso.active
    val controlsAlpha by animateFloatAsState(
        targetValue = controlAlpha(controlsVisible),
        animationSpec = tween(220),
        label = "page controls opacity",
    )

    Box(modifier.fillMaxSize().background(brand.desk)) {
        InkSurface(
            lasso = lasso,
            onMoveStrokes = onMoveStrokes,
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
            shape = state.shape,
            onShapeFinished = onShapeFinished,
            sketchyShapes = state.sketchyShapes,
            reading = chrome == EditorChrome.READING,
            onUserInteraction = ::wakeControls,
            modifier = Modifier
                .fillMaxSize()
                .then(if (chrome != EditorChrome.FULL) Modifier.systemGestureExclusion() else Modifier),
        )

        val quickBar: @Composable () -> Unit = {
            EditorQuickBar(
                canUndo = canUndo, canRedo = canRedo, notebook = notebook,
                onBack = onBack, onPages = onPages,
                onUndo = { lasso.clear(); onUndo() }, onRedo = { lasso.clear(); onRedo() },
                onAddPage = { onAppendPage() }, onMore = onMore,
            )
        }
        val penBar: @Composable (Modifier) -> Unit = { barModifier ->
            InkToolbar(
                pens = state.pens, selectedPen = state.selectedPen, tool = state.tool,
                fingerInputMode = state.fingerInputMode, onFingerInputMode = onFingerInputMode,
                chrome = chrome, onSelectPen = onSelectPen, onEditPen = onEditPen,
                onSelectTool = onSelectTool, onCycleChrome = onCycleChrome, modifier = barModifier,
                darkReader = state.darkReader,
                shape = state.shape, onSelectShape = onSelectShape,
                canUndo = canUndo, onUndo = { lasso.clear(); onUndo() },
                hidden = state.hiddenToolbarItems,
                sketchyShapes = state.sketchyShapes, onSketchyShapes = onSketchyShapes,
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
                if (maxWidth < STACKED_BARS_BELOW) {
                    // A phone: not even a folded pen bar fits beside the note bar, so they stack.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        quickBar()
                        penBar(Modifier.align(Alignment.End))
                    }
                } else {
                    // Two bars on one row, upright or lying down: the note bar at its own width
                    // on the left, the pen bar in whatever is left, against the right edge. The
                    // pen bar folds its tools to fit that room rather than taking a second row.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        quickBar()
                        Box(
                            Modifier.weight(1f).padding(start = 8.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) { penBar(Modifier) }
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

        if (quickHighlighterVisible && chrome != EditorChrome.READING) {
            FloatingPanel(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 72.dp),
                elevation = 6.dp,
            ) {
                ToolButton(
                    icon = Icons.Outlined.Brush,
                    label = if (state.tool == InkTool.HIGHLIGHTER) "Turn off highlighter" else "Turn on highlighter",
                    selected = state.tool == InkTool.HIGHLIGHTER,
                    onClick = { wakeControls(); onToggleQuickHighlighter() },
                    size = 48.dp,
                )
            }
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
                onToggleLock = { wakeControls(); onToggleZoomLock() },
                modifier = Modifier.padding(16.dp).alpha(controlsAlpha),
            )
        }

        // The circled part's own little bar: delete it, copy it, or let it go.
        if (lasso.active && chrome != EditorChrome.READING) {
            LassoActions(
                lasso = lasso,
                strokes = strokes,
                camera = camera,
                onDelete = { onErase(lasso.selection); lasso.clear() },
                onDuplicate = { lasso.selection = onDuplicateStrokes(lasso.selection, 24f, 24f) },
                onColor = { onLassoColor(lasso.selection) },
                onDismiss = lasso::clear,
            )
        }

        if (notebook) {
            PageCounter(
                camera = camera,
                state = state,
                pageCount = pageCount,
                onAppendPage = onAppendPage,
                onInteraction = ::wakeControls,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).alpha(controlsAlpha),
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
                darkReader = state.darkReader,
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
            size = 56.dp,
            discSize = 40.dp,
            modifier = Modifier.padding(0.dp),
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
    onInteraction: () -> Unit,
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
        onPrevious = { onInteraction(); camera.goToPage(boundedCurrent - 1, state.page) },
        onNext = {
            onInteraction()
            val next = boundedCurrent + 1
            if (next >= total) camera.goToPage(onAppendPage(), state.page)
            else camera.goToPage(next, state.page)
        },
        modifier = modifier,
    )
}


/**
 * The actions for a circled part, floating just above it and following it as it is dragged.
 *
 * Reads the strokes and the camera while the selection exists, and only then: a pan or a new
 * stroke recomposes this little bar, not the editor around it.
 */
@Composable
internal fun LassoActions(
    lasso: LassoState,
    strokes: () -> List<InkStroke>,
    camera: InkCamera,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onColor: () -> Unit,
    onDismiss: () -> Unit,
) {
    val bounds = Lasso.selectionBounds(strokes(), lasso.selection)?.translate(lasso.drag) ?: return
    var panelSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }
        val gap = with(density) { 12.dp.toPx() }
        val edge = with(density) { 8.dp.toPx() }
        val centreX = (bounds.left + bounds.right) / 2 * camera.scale + camera.panX
        val topY = bounds.top * camera.scale + camera.panY
        val bottomY = bounds.bottom * camera.scale + camera.panY
        val x = (centreX - panelSize.width / 2).coerceIn(edge, (maxW - panelSize.width - edge).coerceAtLeast(edge))
        // Above the box when there is room, below it otherwise, never off the page.
        val above = topY - gap - panelSize.height
        val y = (if (above >= edge) above else bottomY + gap).coerceIn(edge, (maxH - panelSize.height - edge).coerceAtLeast(edge))
        FloatingPanel(
            Modifier
                .offset { IntOffset(x.toInt(), y.toInt()) }
                .onSizeChanged { panelSize = it },
            elevation = 8.dp,
        ) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ToolButton(icon = Icons.Outlined.ContentCopy, label = "Duplicate the circled strokes", onClick = onDuplicate)
                ToolButton(icon = Icons.Outlined.Delete, label = "Delete the circled strokes", onClick = onDelete)
                ToolButton(icon = Icons.Outlined.Palette, label = "Change colour of the circled strokes", onClick = onColor)
                ToolButton(icon = Icons.Outlined.Close, label = "Deselect", onClick = onDismiss)
            }
        }
    }
}
