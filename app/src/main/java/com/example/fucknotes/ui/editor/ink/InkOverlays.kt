package com.example.fucknotes.ui.editor.ink

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.fucknotes.ui.common.StatusChip
import com.example.fucknotes.ui.theme.Radii

/**
 * The zoom readout.
 *
 * It doubles as the zoom lock, because "150%" and "keep it at 150%" are the same thought — and a
 * writing app is the one place where a scale that drifts under a resting palm is a real loss.
 */
@Composable
fun ZoomChip(
    zoom: Float,
    locked: Boolean,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatusChip(
        text = "${(zoom * 100).toInt()}%",
        modifier = modifier,
        onClick = onToggleLock,
        trailing = {
            Icon(
                if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                contentDescription = if (locked) "Zoom is locked" else "Lock the zoom",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.surface,
            )
        },
    )
}

/** Where you are in the column, and the two steps either side of it. */
@Composable
fun PageIndicator(
    current: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatusChip(
        text = "$current/$total",
        modifier = modifier,
        leading = {
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = "Previous page",
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(Radii.pill))
                    .clickable(enabled = current > 1, onClick = onPrevious),
                tint = MaterialTheme.colorScheme.surface,
            )
        },
        trailing = {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "Next page",
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(Radii.pill))
                    .clickable(onClick = onNext),
                tint = MaterialTheme.colorScheme.surface,
            )
        },
    )
}
