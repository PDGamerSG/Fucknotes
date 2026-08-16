package com.example.fucknotes.ui.editor

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.export.ExportFormat
import com.example.fucknotes.ui.components.relativeStamp
import java.util.Calendar

/** Everything the editor's "more" button opens, and the dialogs those items lead to. */
@Composable
fun EditorOverflowMenu(
    expanded: Boolean,
    type: NoteType,
    locked: Boolean,
    pinned: Boolean,
    hasReminder: Boolean,
    onDismiss: () -> Unit,
    onConvert: (NoteType) -> Unit,
    onPin: () -> Unit,
    onColor: () -> Unit,
    onPaper: () -> Unit,
    onTags: () -> Unit,
    onReminder: () -> Unit,
    onClearReminder: () -> Unit,
    onLock: () -> Unit,
    onExport: () -> Unit,
    onExportPdf: () -> Unit,
    onShareText: () -> Unit,
    onDuplicate: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
) {
    DropdownMenu(expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (pinned) "Unpin" else "Pin to the top") },
            leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
            onClick = onPin,
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
            text = { Text(if (hasReminder) "Change the reminder" else "Set a reminder") },
            leadingIcon = { Icon(Icons.Outlined.Alarm, null) },
            onClick = onReminder,
        )
        if (hasReminder) {
            DropdownMenuItem(text = { Text("Remove the reminder") }, onClick = onClearReminder)
        }
        DropdownMenuItem(
            text = { Text(if (locked) "Remove the lock" else "Lock this note") },
            leadingIcon = { Icon(if (locked) Icons.Outlined.LockOpen else Icons.Outlined.Lock, null) },
            onClick = onLock,
        )

        HorizontalDivider()

        if (type != NoteType.CHECKLIST) {
            DropdownMenuItem(
                text = { Text("Turn into a checklist") },
                leadingIcon = { Icon(Icons.Outlined.CheckBox, null) },
                onClick = { onConvert(NoteType.CHECKLIST) },
            )
        }
        if (type != NoteType.TEXT) {
            DropdownMenuItem(
                text = { Text("Turn into a text note") },
                leadingIcon = { Icon(Icons.Outlined.Description, null) },
                onClick = { onConvert(NoteType.TEXT) },
            )
        }
        // Canvas and notebook hold the same ink, so switching is a change of view, not of content.
        if (type != NoteType.DRAWING) {
            DropdownMenuItem(
                text = { Text("Turn into a canvas") },
                leadingIcon = { Icon(Icons.Outlined.Brush, null) },
                onClick = { onConvert(NoteType.DRAWING) },
            )
        }
        if (type != NoteType.NOTEBOOK) {
            DropdownMenuItem(
                text = { Text("Turn into a notebook") },
                leadingIcon = { Icon(Icons.Outlined.MenuBook, null) },
                onClick = { onConvert(NoteType.NOTEBOOK) },
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
            text = { Text("Note info") },
            leadingIcon = { Icon(Icons.Outlined.Info, null) },
            onClick = onInfo,
        )

        HorizontalDivider()

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

@Composable
fun NoteInfoDialog(state: EditorUiState, strokeCount: Int, pageCount: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (state.type.isInk) {
                    Text("Pages: $pageCount")
                    Text("Strokes: $strokeCount")
                    Text("Paper: ${state.paperStyle.name.lowercase()} on ${state.page.format.label}")
                } else {
                    Text("Words: ${state.wordCount}")
                    Text("Characters: ${state.charCount}")
                }
                Text("Attachments: ${state.attachments.size}")
                Text("Last edited: ${relativeStamp(state.updatedAt)}")
                state.reminderAt?.let { Text("Reminder: ${relativeStamp(it)}") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** The gate in front of a locked note. Nothing of the note is composed until it opens. */
@Composable
fun LockedNoteGate(onUnlock: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text("This note is locked", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Unlock with your fingerprint, face or device passcode.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = {
            val activity = context as? androidx.fragment.app.FragmentActivity
            if (activity == null) {
                error = "Unlock is unavailable here."
                return@TextButton
            }
            com.example.fucknotes.lock.BiometricGate.prompt(
                activity = activity,
                title = "Open locked note",
                subtitle = "Confirm it is you",
                onSuccess = onUnlock,
                onFailure = { error = it },
            )
        }) { Text("Unlock") }
        TextButton(onClick = onBack) { Text("Go back") }
    }
}

/** Chained date then time picker, returning the combined epoch millis. */
fun pickDateTime(context: Context, onPicked: (Long) -> Unit) {
    val now = Calendar.getInstance()
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val target = Calendar.getInstance().apply {
                        set(year, month, day, hour, minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onPicked(target.timeInMillis)
                },
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                true,
            ).show()
        },
        now.get(Calendar.YEAR),
        now.get(Calendar.MONTH),
        now.get(Calendar.DAY_OF_MONTH),
    ).apply { datePicker.minDate = System.currentTimeMillis() - 1000 }.show()
}
