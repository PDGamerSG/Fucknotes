package com.example.fucknotes.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fucknotes.data.db.NoteEntity
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.ui.common.PaperPreview
import com.example.fucknotes.ui.components.relativeStamp
import com.example.fucknotes.ui.theme.Radii
import com.example.fucknotes.ui.theme.brand
import coil.compose.rememberAsyncImagePainter

/**
 * A note as it appears on the shelf: the page itself, then its name underneath.
 *
 * The thumbnail is the real paper the note was made with — same template, same rule colour, same
 * spacing — so a shelf of notebooks is told apart by what they look like rather than by a row of
 * identical cards with different words on them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteTile(
    note: NoteEntity,
    preview: String,
    selected: Boolean,
    selectionMode: Boolean,
    hasImages: Boolean,
    hasAudio: Boolean,
    firstPageImagePath: String? = null,
    darkReader: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brand = MaterialTheme.brand
    Column(
        modifier
            .clip(RoundedCornerShape(Radii.card))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.74f)
                .shadow(3.dp, RoundedCornerShape(10.dp), ambientColor = Color(0x14000000))
        ) {
            NotePagePreview(
                note = note,
                preview = preview,
                firstPageImagePath = firstPageImagePath,
                darkReader = darkReader,
                modifier = Modifier.fillMaxSize(),
            )

            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                )
            }

            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                typeBadge(note.type)?.let { TileBadge(it, note.type.name.lowercase()) }
                if (selectionMode && selected) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            note.title.ifBlank { "Untitled" },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = brand.heading,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                relativeStamp(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hasImages) MetaIcon(Icons.Outlined.Image, "Has images")
            if (hasAudio) MetaIcon(Icons.Outlined.Mic, "Has a recording")
        }
    }
}

/**
 * The page, in miniature.
 *
 * A locked note shows its paper and a padlock rather than its words — the point of the lock is that
 * the shelf gives nothing away.
 */
@Composable
fun NotePagePreview(
    note: NoteEntity,
    preview: String,
    firstPageImagePath: String? = null,
    darkReader: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val brand = MaterialTheme.brand
    Box(modifier) {
        PaperPreview(
            style = note.paperStyle,
            modifier = Modifier.fillMaxSize(),
            paperColor = if (darkReader) Color(0xFF202632)
            else if (note.colorArgb != 0) Color(note.colorArgb) else brand.tile,
            lineColor = if (note.lineColorArgb != 0) Color(note.lineColorArgb) else brand.tileBorder,
            spacingDp = note.lineSpacingDp.takeIf { it > 0 }?.toFloat() ?: 24f,
            zoom = 0.22f,
            corner = 10.dp,
        )

        if (!firstPageImagePath.isNullOrBlank()) {
            Image(
                painter = rememberAsyncImagePainter(firstPageImagePath),
                contentDescription = "First page preview",
                contentScale = ContentScale.Crop,
                colorFilter = if (darkReader) ColorFilter.colorMatrix(
                    ColorMatrix(floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,
                        0f, -1f, 0f, 0f, 255f,
                        0f, 0f, -1f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f,
                    ))
                ) else null,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (preview.isNotBlank()) {
            Text(
                preview,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 7.sp, lineHeight = 11.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                maxLines = 14,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}

@Composable
private fun TileBadge(icon: ImageVector, label: String) {
    Box(
        Modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetaIcon(icon: ImageVector, label: String) {
    Icon(
        icon,
        contentDescription = label,
        modifier = Modifier.size(12.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun typeBadge(type: NoteType): ImageVector? = when (type) {
    NoteType.CHECKLIST -> Icons.Outlined.CheckBox
    NoteType.DRAWING -> Icons.Outlined.Brush
    NoteType.NOTEBOOK -> Icons.Outlined.MenuBook
    NoteType.TEXT -> null
}
