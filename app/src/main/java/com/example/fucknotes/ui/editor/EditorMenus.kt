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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fucknotes.export.ExportFormat

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
) {
    DropdownMenu(expanded, onDismissRequest = onDismiss) {
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
            DropdownMenuItem(text = { Text("Images on this page") }, onClick = it)
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

@Composable
fun ExportDialog(onDismiss: () -> Unit, onExport: (ExportFormat, Boolean) -> Unit) {
    var dark by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export") },
        text = {
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
