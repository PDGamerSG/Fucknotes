package com.example.fucknotes.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.KeyboardType
import com.example.fucknotes.data.model.LineColors
import com.example.fucknotes.data.model.MAX_INITIAL_NOTEBOOK_PAGES
import com.example.fucknotes.data.model.NotebookDraft
import com.example.fucknotes.data.model.PaperColors
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.ui.common.ColorSwatchRow
import com.example.fucknotes.ui.common.PageSizeRow
import com.example.fucknotes.ui.common.PaperPreview
import com.example.fucknotes.ui.common.PaperSectionLabel
import com.example.fucknotes.ui.common.SpacingRow
import com.example.fucknotes.ui.common.TemplatePicker
import com.example.fucknotes.ui.common.paperColorOr
import com.example.fucknotes.ui.theme.Radii
import com.example.fucknotes.ui.theme.brand

/**
 * The sheet that starts a notebook.
 *
 * Paper is chosen once, the way it is when you buy a notebook, so this is the only place where the
 * template, the tones, the size and the rule spacing are decided together. Every control writes
 * into one [NotebookDraft], and the preview at the top is the page renderer itself at a small zoom
 * — what is shown here is the page that gets made.
 */
@Composable
fun NewNotebookDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onCreate: (NotebookDraft) -> Unit,
) {
    var draft by remember { mutableStateOf(NotebookDraft(title = initialTitle)) }
    var pageCountText by remember { mutableStateOf(draft.initialPageCount.toString()) }
    val initialPageCount = pageCountText.toIntOrNull()
    val pageCountValid = initialPageCount in 1..MAX_INITIAL_NOTEBOOK_PAGES

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(Radii.sheet),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier
                .padding(20.dp)
                .widthIn(max = 620.dp)
                .fillMaxWidth(),
        ) {
            Column(Modifier.heightIn(max = 660.dp)) {
                DialogHeader(onDismiss = onDismiss)
                HorizontalDivider()

                NotebookSummary(
                    draft = draft,
                    onTitle = { draft = draft.copy(title = it) },
                    pageCountText = pageCountText,
                    pageCountValid = pageCountValid,
                    onPageCountText = { raw ->
                        if (raw.length <= 3 && raw.all { it.isDigit() }) {
                            pageCountText = raw
                            raw.toIntOrNull()?.takeIf { it in 1..MAX_INITIAL_NOTEBOOK_PAGES }?.let {
                                draft = draft.copy(initialPageCount = it)
                            }
                        }
                    },
                    onCreate = {
                        initialPageCount?.takeIf { it in 1..MAX_INITIAL_NOTEBOOK_PAGES }?.let {
                            onCreate(draft.copy(initialPageCount = it))
                        }
                    },
                )
                HorizontalDivider()

                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                ) {
                    PageSizeRow(page = draft.page, onPage = { draft = draft.copy(page = it) })

                    Spacer(Modifier.height(18.dp))
                    PaperSectionLabel("Templates")
                    Spacer(Modifier.height(10.dp))
                    TemplatePicker(
                        selected = draft.paperStyle,
                        paperColor = draft.paperColorArgb.paperColorOr(Color.White),
                        lineColor = draft.lineColorArgb.paperColorOr(MaterialTheme.brand.tileBorder),
                        spacingDp = draft.spacingDp.toFloat(),
                        onPick = { draft = draft.copy(paperStyle = it) },
                    )

                    Spacer(Modifier.height(20.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            PaperSectionLabel("Paper colour")
                            Spacer(Modifier.height(8.dp))
                            ColorSwatchRow(
                                colors = PaperColors,
                                selectedArgb = draft.paperColorArgb,
                                onPick = { draft = draft.copy(paperColorArgb = it) },
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            PaperSectionLabel("Line colour")
                            Spacer(Modifier.height(8.dp))
                            ColorSwatchRow(
                                colors = LineColors,
                                selectedArgb = draft.lineColorArgb,
                                onPick = { draft = draft.copy(lineColorArgb = it) },
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    SpacingRow(
                        spacingDp = draft.spacingDp,
                        enabled = draft.paperStyle != PaperStyle.PLAIN,
                        onChange = { draft = draft.copy(spacingDp = it) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DialogHeader(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterStart)) {
            Text("Cancel")
        }
        Text(
            "New notebook",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** The page as it will be, its name, and the one button that commits both. */
@Composable
private fun NotebookSummary(
    draft: NotebookDraft,
    onTitle: (String) -> Unit,
    pageCountText: String,
    pageCountValid: Boolean,
    onPageCountText: (String) -> Unit,
    onCreate: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PaperPreview(
            style = draft.paperStyle,
            modifier = Modifier
                .width(104.dp)
                .aspectRatio(draft.page.widthDp / draft.page.heightDp),
            paperColor = draft.paperColorArgb.paperColorOr(Color.White),
            lineColor = draft.lineColorArgb.paperColorOr(MaterialTheme.brand.tileBorder),
            spacingDp = draft.spacingDp.toFloat(),
            zoom = 0.26f,
            corner = 10.dp,
        )

        Column(Modifier.weight(1f)) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = onTitle,
                label = { Text("Title") },
                singleLine = true,
                shape = RoundedCornerShape(Radii.chip),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = pageCountText,
                onValueChange = onPageCountText,
                label = { Text("Empty pages at start") },
                supportingText = { Text("Choose 1–$MAX_INITIAL_NOTEBOOK_PAGES blank pages") },
                isError = pageCountText.isNotEmpty() && !pageCountValid,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(Radii.chip),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onCreate,
                enabled = draft.title.isNotBlank() && pageCountValid,
                shape = RoundedCornerShape(Radii.chip),
            ) {
                Text("Create")
            }
        }
    }
}
