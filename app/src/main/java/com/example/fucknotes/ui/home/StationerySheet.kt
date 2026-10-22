package com.example.fucknotes.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.model.PenPreset
import com.example.fucknotes.ui.common.ImmersiveDialogWindow
import com.example.fucknotes.ui.editor.ink.PenEditorPopup
import com.example.fucknotes.ui.editor.ink.PenGlyph
import com.example.fucknotes.ui.editor.ink.penLabel

/**
 * The pen case.
 *
 * The tray is app-wide rather than per note — a nib width belongs to the pen the user set up, not
 * to the page they set it on — so it can be arranged here, away from a page, and every note opens
 * with the pens as they were left. Tapping a pen opens its colour and nib.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationerySheet(
    pens: List<PenPreset>,
    onEditPen: (index: Int, colorArgb: Int, widthDp: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by remember { mutableIntStateOf(-1) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        ImmersiveDialogWindow()
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            Text("Stationery", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "These pens are shared by every note.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pens.forEachIndexed { index, pen ->
                    Box {
                        PenGlyph(
                            pen = pen,
                            active = editing == index,
                            onClick = { editing = if (editing == index) -1 else index },
                            height = 58,
                        )
                        if (editing == index) {
                            PenEditorPopup(
                                pen = pen,
                                onDismiss = { editing = -1 },
                                onChange = { color, width -> onEditPen(index, color, width) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                pens.getOrNull(editing)?.let(::penLabel) ?: "Tap a pen to change its colour and nib.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
