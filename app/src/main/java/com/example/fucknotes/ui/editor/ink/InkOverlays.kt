package com.example.fucknotes.ui.editor.ink

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.fucknotes.ui.common.FloatingPanel
import com.example.fucknotes.ui.common.ToolButton

/**
 * The zoom readout.
 *
 * It doubles as the zoom lock, because "150%" and "keep it at 150%" are the same thought — and a
 * writing app is the one place where a scale that drifts under a resting palm is a real loss.
 *
 * Cut from the same floating material as the toolbars and the page counter, so it reads as part
 * of the chrome rather than a stray grey pill; the lock tints to the accent while it is on.
 */
@Composable
fun ZoomChip(
    zoom: Float,
    locked: Boolean,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    FloatingPanel(modifier, elevation = 6.dp) {
        Row(
            Modifier
                .clickable(role = Role.Button, onClick = onToggleLock)
                .semantics { contentDescription = if (locked) "Zoom ${(zoom * 100).toInt()}%, locked" else "Zoom ${(zoom * 100).toInt()}%, tap to lock" }
                .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${(zoom * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (locked) scheme.primary else scheme.onSurfaceVariant,
            )
        }
    }
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
            Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
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
