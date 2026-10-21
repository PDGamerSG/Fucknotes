package com.example.fucknotes.ui.editor

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.ink.PageSpec
import com.example.fucknotes.data.model.LineColors
import com.example.fucknotes.data.model.PaperColors
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.ui.common.ColorSwatchRow
import com.example.fucknotes.ui.common.PageSizeRow
import com.example.fucknotes.ui.common.PaperSectionLabel
import com.example.fucknotes.ui.common.SpacingRow
import com.example.fucknotes.ui.common.TemplatePicker
import com.example.fucknotes.ui.common.paperColorOr
import com.example.fucknotes.ui.theme.brand

/**
 * Re-papering an open note.
 *
 * The same controls the notebook was created with, applied live: every change lands on the page
 * behind the sheet, so paper is chosen by looking at the page rather than at a preview of it. Sheet
 * size only appears for a notebook — a canvas has no sheets to size.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaperSheet(
    style: PaperStyle,
    paperColorArgb: Int,
    lineColorArgb: Int,
    spacingDp: Int,
    page: PageSpec,
    notebook: Boolean,
    onStyle: (PaperStyle) -> Unit,
    onPaperColor: (Int) -> Unit,
    onLineColor: (Int) -> Unit,
    onSpacing: (Int) -> Unit,
    onPage: (PageSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 32.dp)) {
            Text("Paper", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            TemplatePicker(
                selected = style,
                paperColor = paperColorArgb.paperColorOr(Color.White),
                lineColor = lineColorArgb.paperColorOr(MaterialTheme.brand.tileBorder),
                spacingDp = spacingDp.takeIf { it > 0 }?.toFloat() ?: 24f,
                onPick = onStyle,
            )

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    PaperSectionLabel("Paper colour")
                    Spacer(Modifier.height(8.dp))
                    ColorSwatchRow(
                        colors = PaperColors,
                        selectedArgb = paperColorArgb,
                        onPick = onPaperColor,
                    )
                }
                Column(Modifier.weight(1f)) {
                    PaperSectionLabel("Line colour")
                    Spacer(Modifier.height(8.dp))
                    ColorSwatchRow(
                        colors = LineColors,
                        selectedArgb = lineColorArgb,
                        onPick = onLineColor,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            SpacingRow(
                spacingDp = spacingDp.takeIf { it > 0 } ?: 24,
                enabled = style != PaperStyle.PLAIN,
                onChange = onSpacing,
            )

            if (notebook && page.customWidth == null) {
                Spacer(Modifier.height(18.dp))
                PageSizeRow(page = page, onPage = onPage)
            }
        }
    }
}
