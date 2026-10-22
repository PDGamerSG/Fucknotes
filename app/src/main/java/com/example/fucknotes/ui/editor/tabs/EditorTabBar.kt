package com.example.fucknotes.ui.editor.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fucknotes.ui.common.ToolButton
import com.example.fucknotes.ui.theme.brand

/**
 * The open notes, along the top.
 *
 * A notebook app is used with two or three notes at once — a lecture, the notes being copied from,
 * the scratch page — and going back to the shelf between them costs the place on the page. The bar
 * shows every note that has been opened and not closed, the way a browser does: the shelf is one
 * tap away on the plus, and a tab stays until its cross is tapped.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorTabBar(
    tabs: List<OpenTabs.Tab>,
    activeNoteId: Long,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onOpenAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var overflow by remember { mutableStateOf(false) }
    val brand = MaterialTheme.brand

    Column(modifier.fillMaxWidth().background(brand.desk)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // The tabs and the plus scroll together, as in a browser: the plus always follows the
            // last tab, and the row takes the whole bar so tabs only scroll once they overflow.
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                tabs.forEach { tab ->
                    EditorTab(
                        tab = tab,
                        active = tab.noteId == activeNoteId,
                        onSelect = { onSelect(tab.noteId) },
                        onClose = { onClose(tab.noteId) },
                    )
                }
                Spacer(Modifier.width(2.dp))
                Box(Modifier.height(42.dp), contentAlignment = Alignment.Center) {
                    ToolButton(
                        icon = Icons.Outlined.Add,
                        label = "Open another note",
                        onClick = onOpenAnother,
                        size = 34.dp,
                        iconSize = 19.dp,
                    )
                }
            }

            if (tabs.size > 1) Box(Modifier.height(42.dp), contentAlignment = Alignment.Center) {
                ToolButton(
                    icon = Icons.Filled.ExpandMore,
                    label = "All open notes",
                    onClick = { overflow = true },
                    size = 34.dp,
                    iconSize = 19.dp,
                )
                DropdownMenu(overflow, onDismissRequest = { overflow = false }) {
                    tabs.forEach { tab ->
                        DropdownMenuItem(
                            text = { Text(tab.title.ifBlank { "Untitled" }) },
                            onClick = { overflow = false; onSelect(tab.noteId) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Close ${tab.title.ifBlank { "Untitled" }}",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { overflow = false; onClose(tab.noteId) },
                                )
                            },
                        )
                    }
                }
            }
        }
        // The active tab is cut from the same material as the bars below it, so the hairline
        // reads as the edge of the desk rather than a line through the tab.
        Box(Modifier.fillMaxWidth().height(1.dp).background(brand.chromeBorder))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorTab(
    tab: OpenTabs.Tab,
    active: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val brand = MaterialTheme.brand
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
    val title = tab.title.ifBlank { "Untitled" }
    val requester = remember { BringIntoViewRequester() }
    // A tab opened off the end of a full bar scrolls itself into view.
    LaunchedEffect(active) { if (active) requester.bringIntoView() }

    Row(
        Modifier
            .bringIntoViewRequester(requester)
            .height(36.dp)
            .widthIn(min = 128.dp, max = 220.dp)
            .clip(shape)
            .background(if (active) brand.chrome else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onSelect)
            .semantics { selected = active; contentDescription = "$title tab" }
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Close $title",
                modifier = Modifier.size(14.dp),
                tint = scheme.onSurfaceVariant.copy(alpha = if (active) 0.9f else 0.55f),
            )
        }
    }
}
