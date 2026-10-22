package com.example.fucknotes.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fucknotes.drive.DriveSync
import com.example.fucknotes.ui.theme.Radii
import java.text.DateFormat
import java.util.Date

/**
 * The Drive control at the top of the shelf.
 *
 * Logged out it is one button, "Log in with Drive". Logged in it is a pill that says how the
 * folder stands — syncing, synced, or in trouble — and opens the short menu for everything else.
 */
@Composable
fun DriveAction(
    email: String,
    status: DriveSync.Status,
    lastSyncAt: Long,
    lastError: String,
    autoSync: Boolean,
    onLogin: () -> Unit,
    onSyncNow: () -> Unit,
    onRestore: () -> Unit,
    onOpenFolder: () -> Unit,
    onAutoSync: (Boolean) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radii.chip)
    if (email.isBlank()) {
        Row(
            modifier
                .clip(shape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .clickable(onClick = onLogin)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .semantics { contentDescription = "Log in with Drive" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CloudQueue, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Log in with Drive", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    var menu by remember { mutableStateOf(false) }
    val running = status.phase == DriveSync.Phase.RUNNING
    val trouble = status.phase == DriveSync.Phase.ERROR || (lastError.isNotBlank() && !running)
    val line = when {
        running -> status.message.ifBlank { "Syncing…" }
        trouble -> "Drive: ${status.message.ifBlank { lastError }}"
        lastSyncAt > 0 -> "Synced ${relative(lastSyncAt)}"
        else -> "Drive connected"
    }
    val tint = when {
        trouble -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Box(modifier) {
        Row(
            Modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .clickable { menu = true }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { contentDescription = "Google Drive, $line" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(if (trouble) Icons.Outlined.CloudOff else Icons.Outlined.CloudDone, null, Modifier.size(18.dp), tint = tint)
            Spacer(Modifier.width(8.dp))
            Text(
                line, style = MaterialTheme.typography.labelLarge, color = tint,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp),
            )
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Google Drive", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(email, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Every collection is a folder in Drive, every note a PDF that opens back into the app.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 280.dp).padding(top = 4.dp),
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Sync now") },
                leadingIcon = { Icon(Icons.Outlined.Sync, null) },
                enabled = !running,
                onClick = { menu = false; onSyncNow() },
            )
            DropdownMenuItem(
                text = { Text("Restore notes from Drive") },
                leadingIcon = { Icon(Icons.Outlined.CloudDownload, null) },
                enabled = !running,
                onClick = { menu = false; onRestore() },
            )
            DropdownMenuItem(
                text = { Text("Open folder in Drive") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                onClick = { menu = false; onOpenFolder() },
            )
            DropdownMenuItem(
                text = { Text("Auto sync") },
                trailingIcon = { Switch(checked = autoSync, onCheckedChange = { onAutoSync(it) }) },
                onClick = { onAutoSync(!autoSync) },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Log out") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
                onClick = { menu = false; onLogout() },
            )
        }
    }
}

/** "just now", "5 min ago", "2 h ago", then the date. */
fun relative(at: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = (now - at) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))
    }
}
