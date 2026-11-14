package com.example.fucknotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.HighlightAlt
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.fucknotes.export.ExportFormat
import com.example.fucknotes.ui.common.ImmersiveDialogWindow
import com.example.fucknotes.ui.editor.ink.ToolbarLayout
import com.example.fucknotes.ui.editor.ink.ToolbarTool

/** Everything the editor's "more" button opens, and the dialogs those items lead to. */
@Composable
fun EditorOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onColor: () -> Unit,
    onPaper: () -> Unit,
    onTags: () -> Unit,
    onAddImage: () -> Unit,
    onPageImages: (() -> Unit)?,
    onExport: () -> Unit,
    onExportPdf: () -> Unit,
    onShareText: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    darkReader: Boolean = false,
    onToggleDarkReader: () -> Unit = {},
    onRename: () -> Unit = {},
    onHighlighter: (() -> Unit)? = null,
    /** Ink notes only: choose what the pen bar carries. */
    onToolbarItems: (() -> Unit)? = null,
) {
    DropdownMenu(expanded, onDismissRequest = onDismiss) {
        // Night colours for every page, image and stroke; the same switch as in Settings, here
        // because it is reached for at night, in the middle of reading.
        DropdownMenuItem(
            text = { Text("Dark mode") },
            leadingIcon = { Icon(if (darkReader) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, null) },
            trailingIcon = { Switch(checked = darkReader, onCheckedChange = { onToggleDarkReader() }) },
            onClick = onToggleDarkReader,
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Rename") },
            leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
            onClick = onRename,
        )
        DropdownMenuItem(
            text = { Text("Paper") },
            leadingIcon = { Icon(Icons.Outlined.FormatPaint, null) },
            onClick = onPaper,
        )
        onToolbarItems?.let { action ->
            DropdownMenuItem(
                text = { Text("Toolbar items") },
                leadingIcon = { Icon(Icons.Outlined.Tune, null) },
                onClick = action,
            )
        }
        onHighlighter?.let { action ->
            DropdownMenuItem(
                text = { Text("Quick highlighter") },
                leadingIcon = { Icon(Icons.Outlined.Brush, null) },
                onClick = action,
            )
        }
        DropdownMenuItem(
            text = { Text("Note colour") },
            leadingIcon = { Icon(Icons.Outlined.Palette, null) },
            onClick = onColor,
        )
        DropdownMenuItem(
            text = { Text("Tags") },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null) },
            onClick = onTags,
        )
        DropdownMenuItem(
            text = { Text("Import image") },
            leadingIcon = { Icon(Icons.Outlined.Image, null) },
            onClick = onAddImage,
        )
        onPageImages?.let {
            DropdownMenuItem(
                text = { Text("Images on this page") },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                onClick = it,
            )
        }
        HorizontalDivider()

        // PDF gets its own row above the format picker: it is the one export a handwritten note
        // actually wants — a notebook comes out a sheet per page, at size — and burying it behind a
        // dialog made the common case the slowest one.
        DropdownMenuItem(
            text = { Text("Export as PDF") },
            leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) },
            onClick = onExportPdf,
        )
        DropdownMenuItem(
            text = { Text("Export as…") },
            leadingIcon = { Icon(Icons.Outlined.UploadFile, null) },
            onClick = onExport,
        )
        DropdownMenuItem(
            text = { Text("Share as text") },
            leadingIcon = { Icon(Icons.Outlined.Share, null) },
            onClick = onShareText,
        )
        DropdownMenuItem(
            text = { Text("Duplicate") },
            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
            onClick = onDuplicate,
        )
        DropdownMenuItem(
            text = { Text("Move to trash") },
            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
            onClick = onDelete,
        )
    }
}

/** Pick a format. The pages come out the way the note is set: black in dark mode, light otherwise. */
@Composable
fun ExportDialog(onDismiss: () -> Unit, onExport: (ExportFormat) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export") },
        text = {
            ImmersiveDialogWindow()
            Column {
                ExportFormat.entries.forEach { format ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onExport(format) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(format.label, Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}


/**
 * What the pen bar carries.
 *
 * Every tool and every pen slot has a switch; off puts it away, on brings it back, and the bar
 * behind the dialog changes as they are flipped. The lock, the note bar and this menu are not
 * listed — they are how the rest is reached — and the last pen cannot be switched off.
 */
@Composable
fun ToolbarItemsDialog(
    pens: List<com.example.fucknotes.data.model.PenPreset>,
    hidden: Set<String>,
    darkReader: Boolean,
    onToggle: (key: String, hide: Boolean) -> Unit,
    onShowAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Toolbar items") },
        text = {
            ImmersiveDialogWindow()
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val row: @Composable (String, String, Boolean, @Composable () -> Unit) -> Unit = { key, label, canHide, lead ->
                    val shown = key !in hidden
                    // What is already away can always come back; only putting away can be refused.
                    val enabled = !shown || canHide
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) { onToggle(key, shown) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { lead() }
                        Text(label, Modifier.weight(1f).padding(start = 12.dp))
                        Switch(checked = shown, enabled = enabled, onCheckedChange = { onToggle(key, !it) })
                    }
                }
                ToolbarTool.entries.forEach { tool ->
                    row(tool.key, tool.label, true) {
                        Icon(
                            when (tool) {
                                ToolbarTool.ERASER -> com.example.fucknotes.ui.editor.ink.EraserIcon
                                ToolbarTool.SELECT -> Icons.Outlined.HighlightAlt
                                ToolbarTool.FINGER -> Icons.Outlined.PanTool
                                ToolbarTool.SHAPES -> Icons.Outlined.Category
                                ToolbarTool.RED_HIGHLIGHTER -> Icons.Outlined.Highlight
                            },
                            contentDescription = null,
                            tint = if (tool == ToolbarTool.RED_HIGHLIGHTER) Color(0xFFE84158)
                            else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                val swatch: @Composable (Int) -> Unit = { slot ->
                    val pen = pens[slot]
                    val argb = if (darkReader) com.example.fucknotes.ui.editor.ink.darkReaderArgb(pen.colorArgb, pen.tool) else pen.colorArgb
                    Box(
                        Modifier
                            .size(22.dp)
                            .background(Color(argb), androidx.compose.foundation.shape.CircleShape)
                            .border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.CircleShape),
                    )
                }
                ToolbarLayout.penSlots(pens).forEachIndexed { number, slot ->
                    val key = ToolbarLayout.penKey(slot)
                    row(key, "Pen ${number + 1}", ToolbarLayout.canHide(key, pens, hidden)) { swatch(slot) }
                }
                ToolbarLayout.highlighterSlots(pens).forEachIndexed { number, slot ->
                    row(ToolbarLayout.penKey(slot), "Highlighter ${number + 1}", true) { swatch(slot) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onShowAll, enabled = hidden.isNotEmpty()) { Text("Show all") } },
    )
}

/** A new name for the note. The current name starts selected, so typing replaces it. */
@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(current, androidx.compose.ui.text.TextRange(0, current.length)))
    }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val confirm = { val name = value.text.trim(); if (name.isNotEmpty()) onConfirm(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            ImmersiveDialogWindow()
            androidx.compose.material3.OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Name") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
        },
        confirmButton = { TextButton(onClick = confirm, enabled = value.text.isNotBlank()) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
