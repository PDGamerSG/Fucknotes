package com.example.fucknotes.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fucknotes.ui.theme.Radii
import com.example.fucknotes.ui.theme.brand

/**
 * One row of the folder rail.
 *
 * Selection is a raised white pill rather than a tint, which is what lets the rail keep a coloured
 * wash behind it: a tinted highlight on a tinted ground reads as a smudge, a lifted card reads as
 * "you are here".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RailRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    count: Int? = null,
    depth: Int = 0,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val brand = MaterialTheme.brand
    val shape = RoundedCornerShape(Radii.row)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .padding(start = (depth * 14).dp)
            .then(
                if (selected) {
                    Modifier.shadow(6.dp, shape, ambientColor = Color(0x14000000), spotColor = Color(0x1F000000))
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(if (selected) brand.railSelected else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),

        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (expandable && onToggleExpand != null) {
            IconButton(onClick = onToggleExpand, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.ChevronRight, if (expanded) "Collapse $label" else "Expand $label",
                    Modifier.size(20.dp).rotate(if (expanded) 90f else 0f), tint = brand.railText)
            }
        }
        when {
            leading != null -> leading()
            icon != null -> Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else brand.railText.copy(alpha = 0.75f),
            )
        }

        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else brand.railText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (count != null && count > 0) {
            CountBadge(count = count, highlighted = selected)
        }

        onMore?.let {
            IconButton(onClick = it, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Filled.MoreVert, "Options for $label", tint = brand.railText)
            }
        }

    }
}

/** The tally at the end of a rail row: a plain number, or a filled dot on the open folder. */
@Composable
private fun CountBadge(count: Int, highlighted: Boolean) {
    if (!highlighted) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.brand.railMuted,
        )
        return
    }
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** A section heading inside the rail — "Folder", "Tags". */
@Composable
fun RailSectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.brand.heading,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Vertical breathing room, named so call sites read as layout rather than as arithmetic. */
@Composable
fun RailGap(height: Int = 8) {
    Spacer(Modifier.height(height.dp).width(1.dp))
}
