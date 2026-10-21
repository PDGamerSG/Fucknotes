package com.example.fucknotes.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.db.NoteEntity
import com.example.fucknotes.data.model.NoteType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One note tile. Used by both the grid and the list layout. */
@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntity,
    preview: String,
    tagNames: List<String>,
    selected: Boolean,
    selectionMode: Boolean,
    hasImages: Boolean,
    hasAudio: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (note.colorArgb != 0) Color(note.colorArgb) else MaterialTheme.colorScheme.surfaceVariant
    val onTint = if (note.colorArgb != 0) Color(0xFF1B1B1F) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = tint, contentColor = onTint),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note.title.isNotBlank()) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        text = "Untitled",
                        style = MaterialTheme.typography.titleMedium,
                        color = onTint.copy(alpha = 0.55f),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (selectionMode && selected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            when {
                preview.isNotBlank() -> Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    color = onTint.copy(alpha = 0.85f),
                )
            }

            if (tagNames.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tagNames.take(3).forEach { tag ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(onTint.copy(alpha = 0.10f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("#$tag", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = relativeStamp(note.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = onTint.copy(alpha = 0.6f),
                    modifier = Modifier.weight(1f),
                )
                badge(note.type)?.let { Icon(it, contentDescription = null, modifier = Modifier.size(14.dp)) }
                if (hasImages) Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                if (hasAudio) Icon(Icons.Outlined.Mic, contentDescription = null, modifier = Modifier.size(14.dp))

            }
        }
    }
}

private fun badge(type: NoteType): ImageVector? = when (type) {
    NoteType.CHECKLIST -> Icons.Outlined.CheckBox
    NoteType.DRAWING -> Icons.Outlined.Brush
    NoteType.NOTEBOOK -> Icons.Outlined.MenuBook
    NoteType.TEXT -> null
}

/** "14:32" today, "Mon" this week, otherwise a short date. */
fun relativeStamp(millis: Long): String {
    val now = System.currentTimeMillis()
    val delta = now - millis
    val locale = Locale.getDefault()
    return when {
        delta < 60_000 -> "just now"
        delta < 24 * 60 * 60_000 && sameDay(now, millis) -> SimpleDateFormat("HH:mm", locale).format(Date(millis))
        delta < 7 * 24 * 60 * 60_000L -> SimpleDateFormat("EEE HH:mm", locale).format(Date(millis))
        else -> SimpleDateFormat("d MMM yyyy", locale).format(Date(millis))
    }
}

private fun sameDay(a: Long, b: Long): Boolean {
    val format = SimpleDateFormat("yyyyDDD", Locale.US)
    return format.format(Date(a)) == format.format(Date(b))
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.height(8.dp))
    }
}
