package com.example.fucknotes.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * What replaces the action row while notes are selected.
 *
 * The three actions used most often stay on the bar; the rest are one menu behind it, because a bar
 * of eleven icons is slower to read than a list of eleven words.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionBar(
    count: Int,
    archived: Boolean,
    onClose: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onArchive: () -> Unit,
    onLock: () -> Unit,
    onUnlock: () -> Unit,
    onColor: () -> Unit,
    onMove: () -> Unit,
    onDuplicate: () -> Unit,
    onMerge: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
) {
    var more by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Clear the selection")
            }
        },
        actions = {
            IconButton(onClick = onPin) { Icon(Icons.Outlined.PushPin, contentDescription = "Pin") }
            IconButton(onClick = onColor) { Icon(Icons.Outlined.Palette, contentDescription = "Colour") }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Move to trash") }
            Box {
                IconButton(onClick = { more = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                }
                DropdownMenu(more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(
                        text = { Text("Select all") },
                        leadingIcon = { Icon(Icons.Filled.Check, null) },
                        onClick = { more = false; onSelectAll() },
                    )
                    DropdownMenuItem(
                        text = { Text("Unpin") },
                        leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                        onClick = { more = false; onUnpin() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (archived) "Take out of the archive" else "Archive") },
                        leadingIcon = {
                            Icon(if (archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, null)
                        },
                        onClick = { more = false; onArchive() },
                    )
                    DropdownMenuItem(
                        text = { Text("Move to a folder") },
                        leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                        onClick = { more = false; onMove() },
                    )
                    DropdownMenuItem(
                        text = { Text("Lock") },
                        leadingIcon = { Icon(Icons.Outlined.Lock, null) },
                        onClick = { more = false; onLock() },
                    )
                    DropdownMenuItem(
                        text = { Text("Unlock") },
                        leadingIcon = { Icon(Icons.Outlined.LockOpen, null) },
                        onClick = { more = false; onUnlock() },
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                        onClick = { more = false; onDuplicate() },
                    )
                    DropdownMenuItem(
                        text = { Text("Merge into one note") },
                        leadingIcon = { Icon(Icons.Outlined.Merge, null) },
                        onClick = { more = false; onMerge() },
                    )
                }
            }
        },
    )
}
