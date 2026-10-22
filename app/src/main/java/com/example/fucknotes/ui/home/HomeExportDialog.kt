package com.example.fucknotes.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import com.example.fucknotes.ui.common.ImmersiveDialogWindow

enum class HomeExportMode(val label: String) {
    COMBINED("Save one combined PDF"),
    ZIP("Save a ZIP of individual PDFs"),
    SEPARATE("Save individual PDFs to a folder"),
}

data class HomeExportTarget(val title: String, val folderId: Long? = null, val noteIds: List<Long> = emptyList())
data class HomeExportRequest(val target: HomeExportTarget, val mode: HomeExportMode, val dark: Boolean = false)

@Composable
fun HomeExportDialog(
    title: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    initialDark: Boolean = false,
    onPick: (HomeExportMode, dark: Boolean) -> Unit,
) {
    var dark by remember { mutableStateOf(initialDark) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export $title") },
        text = {
            ImmersiveDialogWindow()
            Column {
                Text("Includes every page, image and annotation. Collection exports also include subfolders.")
                Row(
                    Modifier.fillMaxWidth().clickable { dark = !dark }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = dark, onCheckedChange = { dark = it })
                    Text("Dark pages, as dark mode shows them")
                }
                HomeExportMode.entries.forEach { mode ->
                    TextButton(onClick = { onPick(mode, dark) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(mode.label, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
