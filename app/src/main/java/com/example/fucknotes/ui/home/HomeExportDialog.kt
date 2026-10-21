package com.example.fucknotes.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class HomeExportMode(val label: String) {
    COMBINED("Save one combined PDF"),
    ZIP("Save a ZIP of individual PDFs"),
    SEPARATE("Save individual PDFs to a folder"),
}

data class HomeExportTarget(val title: String, val folderId: Long? = null, val noteIds: List<Long> = emptyList())
data class HomeExportRequest(val target: HomeExportTarget, val mode: HomeExportMode)

@Composable
fun HomeExportDialog(title: String, busy: Boolean, onDismiss: () -> Unit, onPick: (HomeExportMode) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export $title") },
        text = {
            Column {
                Text("Includes every page, image and annotation. Collection exports also include subfolders.")
                HomeExportMode.entries.forEach { mode ->
                    TextButton(onClick = { onPick(mode) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(mode.label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
