package com.example.fucknotes.ui.editor

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import com.example.fucknotes.export.ExportFormat
import com.example.fucknotes.ui.common.ImmersiveDialogWindow

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

/** Light or dark page, then export. The choice starts from how the note is being read. */
@Composable
fun PdfExportDialog(initialDark: Boolean, onDismiss: () -> Unit, onExport: (dark: Boolean) -> Unit) {
    var dark by remember { mutableStateOf(initialDark) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export as PDF") },
        text = {
            ImmersiveDialogWindow()
            Column {
                listOf(false to "Light page", true to "Dark page").forEach { (isDark, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { dark = isDark }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = dark == isDark, onClick = { dark = isDark })
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(label)
                            Text(
                                if (isDark) "Night slate, night ink, as dark mode shows the page"
                                else "The paper and ink colours the note was written in",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onExport(dark) }) { Text("Export") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ExportDialog(onDismiss: () -> Unit, onExport: (ExportFormat, Boolean) -> Unit, initialDark: Boolean = false) {
    var dark by remember { mutableStateOf(initialDark) }
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
                            .clickable { onExport(format, dark) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(format.label, Modifier.weight(1f))
                    }
                }
                HorizontalDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { dark = !dark }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = dark, onCheckedChange = { dark = it })
                    Text("Use a dark page")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
