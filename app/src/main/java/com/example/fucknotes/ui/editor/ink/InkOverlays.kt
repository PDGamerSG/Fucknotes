package com.example.fucknotes.ui.editor.ink

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.fucknotes.ui.common.FloatingPanel
import com.example.fucknotes.ui.common.StatusChip
import com.example.fucknotes.ui.common.ToolButton

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

/**
 * Where you are in the column, and the two steps either side of it.
 *
 * Built from the same floating material as the toolbars, with real 40 dp targets on the arrows:
 * this sits in the bottom corner under a resting hand, and a 16 dp chevron there was a miss more
 * often than a hit.
 */
@Composable
fun PageIndicator(
    current: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingPanel(modifier, elevation = 6.dp) {
        Row(
            Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(
                icon = Icons.Filled.ChevronLeft,
                label = "Previous page",
                enabled = current > 1,
                onClick = onPrevious,
            )
            Text(
                "$current / $total",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .semantics { contentDescription = "Page $current of $total" },
            )
            ToolButton(
                icon = Icons.Filled.ChevronRight,
                label = if (current >= total) "Add a page" else "Next page",
                onClick = onNext,
            )
        }
    }
}
