package com.example.fucknotes.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.fucknotes.data.db.FolderEntity

/**
 * The folder hierarchy, one level at a time.
 *
 * A folder with children carries a chevron and keeps its own open state, so a deep tree collapses
 * back to a short list — the rail is a place to navigate from, not an outline to read.
 *
 * Visible overflow buttons expose creation, export and rename actions for each collection/folder.
 */
@Composable
fun FolderTree(
    folders: List<FolderEntity>,
    parentId: Long?,
    depth: Int,
    scope: Scope,
    countByFolder: Map<Long?, Int>,
    expandedIds: Set<Long>,
    onToggleExpand: (Long) -> Unit,
    onScope: (Scope) -> Unit,
    onNewSubfolder: (Long) -> Unit,
    onRename: (FolderEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onExport: (FolderEntity) -> Unit,
) {
    folders.filter { it.parentId == parentId }.forEach { folder ->
        val children = folders.filter { it.parentId == folder.id }
        val open = folder.id in expandedIds
        val selected = (scope as? Scope.Folder)?.id == folder.id
        var menu by remember(folder.id) { mutableStateOf(false) }

        Box {
            RailRow(
                label = folder.name,
                icon = if (selected || open) Icons.Outlined.FolderOpen else Icons.Outlined.Folder,
                selected = selected,
                count = countByFolder[folder.id] ?: 0,
                depth = depth,
                expandable = children.isNotEmpty(),
                expanded = open,
                onToggleExpand = { onToggleExpand(folder.id) },
                onClick = {
                    if (children.isNotEmpty() && !open) onToggleExpand(folder.id)
                    onScope(Scope.Folder(folder.id, folder.name))
                },
                onLongClick = { menu = true },
                onMore = { menu = true },
            )
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Export PDFs") },
                    leadingIcon = { Icon(Icons.Outlined.FileDownload, null) },
                    onClick = { menu = false; onExport(folder) },
                )
                if (depth + 1 < HomeViewModel.MAX_FOLDER_DEPTH) {
                    DropdownMenuItem(
                        text = { Text("New folder") },
                        leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) },
                        onClick = { menu = false; onNewSubfolder(folder.id) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                    onClick = { menu = false; onRename(folder) },
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                    onClick = { menu = false; onDelete(folder.id) },
                )
            }
        }

        if (open) {
            FolderTree(
                folders = folders,
                parentId = folder.id,
                depth = depth + 1,
                scope = scope,
                countByFolder = countByFolder,
                expandedIds = expandedIds,
                onToggleExpand = onToggleExpand,
                onScope = onScope,
                onNewSubfolder = onNewSubfolder,
                onRename = onRename,
                onDelete = onDelete,
                onExport = onExport,
            )
        }
    }
}
