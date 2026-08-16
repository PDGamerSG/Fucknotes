package com.example.fucknotes.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush as PaintBrush
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.db.FolderEntity
import com.example.fucknotes.ui.common.RailArtwork
import com.example.fucknotes.ui.common.ToolButton
import com.example.fucknotes.ui.theme.brand

/** How wide the rail sits when it is permanent. Narrow enough to leave a tablet its whole sheet. */
val RailWidth = 280.dp

/**
 * The folder rail.
 *
 * One tinted column holding everything that answers "which notes am I looking at" — scopes,
 * folders, tags, trash — so the sheet beside it can be nothing but notes. It is the same composable
 * whether it is pinned open on a tablet or slid in as a drawer on a phone; only [onCollapse]
 * differs, and a null there simply drops the button that would have closed it.
 */
@Composable
fun HomeRail(
    state: HomeUiState,
    expandedFolders: Set<Long>,
    onToggleFolder: (Long) -> Unit,
    onScope: (Scope) -> Unit,
    onNewFolder: (parentId: Long?) -> Unit,
    onRenameFolder: (FolderEntity) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onDeleteTag: (Long) -> Unit,
    onStationery: () -> Unit,
    onTrash: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onCollapse: (() -> Unit)? = null,
) {
    val brand = MaterialTheme.brand
    Box(
        modifier
            .width(RailWidth)
            .fillMaxHeight()
            .background(PaintBrush.verticalGradient(listOf(brand.railTop, brand.railBottom)))
    ) {
        // The horizon sits behind the rows and gives the list a bottom, so a short folder list does
        // not end in a stretch of empty wash.
        RailArtwork(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(220.dp)
        )

        Column(Modifier.fillMaxHeight()) {
            RailHeader(onCollapse = onCollapse, onNewFolder = { onNewFolder(null) })

            RailRow(
                label = "Stationery",
                icon = Icons.Outlined.Brush,
                onClick = onStationery,
            )

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                RailSectionLabel("Folder")

                RailRow(
                    label = "All notes",
                    icon = Icons.AutoMirrored.Outlined.Notes,
                    selected = state.scope == Scope.All,
                    count = state.countByFolder.values.sum(),
                    onClick = { onScope(Scope.All) },
                )
                RailRow(
                    label = "Unfiled",
                    icon = Icons.Outlined.Inbox,
                    selected = state.scope == Scope.Unfiled,
                    count = state.countByFolder[null] ?: 0,
                    onClick = { onScope(Scope.Unfiled) },
                )

                FolderTree(
                    folders = state.folders,
                    parentId = null,
                    depth = 0,
                    scope = state.scope,
                    countByFolder = state.countByFolder,
                    expandedIds = expandedFolders,
                    onToggleExpand = onToggleFolder,
                    onScope = onScope,
                    onNewSubfolder = onNewFolder,
                    onRename = onRenameFolder,
                    onDelete = onDeleteFolder,
                )

                RailRow(
                    label = "Archive",
                    icon = Icons.Outlined.Archive,
                    selected = state.scope == Scope.Archive,
                    onClick = { onScope(Scope.Archive) },
                )

                if (state.tags.isNotEmpty()) {
                    RailSectionLabel("Tags")
                    state.tags.forEach { tag ->
                        var menu by remember(tag.id) { mutableStateOf(false) }
                        Box {
                            RailRow(
                                label = "#${tag.name}",
                                icon = Icons.AutoMirrored.Outlined.Label,
                                selected = (state.scope as? Scope.Tag)?.id == tag.id,
                                onClick = { onScope(Scope.Tag(tag.id, tag.name)) },
                                onLongClick = { menu = true },
                            )
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Delete tag") },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                    onClick = { menu = false; onDeleteTag(tag.id) },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            RailRow(label = "Trash", icon = Icons.Outlined.Delete, onClick = onTrash)

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolButton(
                    icon = Icons.Outlined.Settings,
                    label = "Settings",
                    onClick = onSettings,
                    size = 40.dp,
                )
            }
        }
    }
}

/** Rail header: collapse on the left, new folder on the right — the two things the rail itself does. */
@Composable
private fun RailHeader(onCollapse: (() -> Unit)?, onNewFolder: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onCollapse != null) {
            ToolButton(
                icon = Icons.AutoMirrored.Outlined.MenuOpen,
                label = "Hide folders",
                onClick = onCollapse,
            )
        } else {
            Spacer(Modifier.size(40.dp))
        }
        Spacer(Modifier.weight(1f))
        Box {
            ToolButton(
                icon = Icons.Outlined.CreateNewFolder,
                label = "New folder",
                onClick = onNewFolder,
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(7.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}
