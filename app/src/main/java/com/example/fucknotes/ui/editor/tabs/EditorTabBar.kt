package com.example.fucknotes.ui.editor.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fucknotes.ui.theme.brand

/**
 * The open notes, along the top.
 *
 * A notebook app is used with two or three notes at once — a lecture, the notes being copied from,
 * the scratch page — and going back to the shelf between them costs the place on the page. The bar
 * only appears once a second note is open, so a single note keeps its whole window.
 */
@Composable
fun EditorTabBar(
    tabs: List<OpenTabs.Tab>,
    activeNoteId: Long,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.size < 2) return
    var overflow by remember { mutableStateOf(false) }
    val brand = MaterialTheme.brand

    Row(
        modifier
            .fillMaxWidth()
            .background(brand.desk)
            .height(38.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            tabs.forEach { tab ->
                EditorTab(
                    tab = tab,
                    active = tab.noteId == activeNoteId,
                    onSelect = { onSelect(tab.noteId) },
                    onClose = { onClose(tab.noteId) },
                )
            }
        }

        Box {
            Box(
                Modifier
                    .size(38.dp)
                    .clickable { overflow = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = "All open notes",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(overflow, onDismissRequest = { overflow = false }) {
                tabs.forEach { tab ->
                    DropdownMenuItem(
                        text = { Text(tab.title.ifBlank { "Untitled" }) },
                        onClick = { overflow = false; onSelect(tab.noteId) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close ${tab.title}",
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
}

@Composable
private fun EditorTab(
    tab: OpenTabs.Tab,
    active: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    val brand = MaterialTheme.brand
    val shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp)
    Row(
        Modifier
            .height(38.dp)
            .widthIn(min = 140.dp, max = 240.dp)
            .clip(shape)
            .background(if (active) brand.chrome else Color.Transparent)
            .clickable(onClick = onSelect)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Close,
            contentDescription = "Close ${tab.title.ifBlank { "note" }}",
            modifier = Modifier
                .size(15.dp)
                .clickable(onClick = onClose),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (active) 0.9f else 0.5f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            tab.title.ifBlank { "Untitled" },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
