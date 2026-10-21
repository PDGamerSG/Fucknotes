package com.example.fucknotes.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.fucknotes.data.db.AttachmentEntity
import com.example.fucknotes.data.model.AttachmentType
import com.example.fucknotes.data.model.NoteType
import com.example.fucknotes.media.AudioRecorder
import com.example.fucknotes.ui.components.PaperBackground
import com.example.fucknotes.ui.common.paperColorOr
import com.example.fucknotes.ui.editor.ink.DefaultRuleColor

/**
 * A typed note: title, body or checklist, then whatever is attached to it.
 *
 * Unlike [InkEditor] this takes the view model directly. Its controls are ordinary form callbacks
 * — a title change, a ticked box — none of them on a drawing hot path where a re-supplied lambda
 * would cost a frame, so twelve separate parameters would buy nothing but noise.
 */
@Composable
fun TypedEditor(
    state: EditorUiState,
    viewModel: EditorViewModel,
    playingPath: String?,
    onPlay: (String) -> Unit,
    onAddTag: () -> Unit,
    modifier: Modifier = Modifier,
    inkOverlay: @Composable BoxScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxSize()
            .background(state.colorArgb.paperColorOr(MaterialTheme.colorScheme.background))
    ) {
        TextField(
            value = state.title,
            onValueChange = viewModel::setTitle,
            placeholder = { Text("Title", style = MaterialTheme.typography.titleLarge) },
            textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            singleLine = true,
            colors = transparentTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        Box(Modifier.weight(1f)) {
            PaperBackground(
                style = state.paperStyle,
                spacing = state.ruleSpacingDp.dp,
                lineColor = state.lineColorArgb.paperColorOr(DefaultRuleColor),
                modifier = Modifier.fillMaxSize(),
            ) {
                when (state.type) {
                    NoteType.CHECKLIST -> ChecklistBody(state = state, viewModel = viewModel)

                    NoteType.TEXT -> Column(Modifier.verticalScroll(rememberScrollState())) {
                        TextField(
                            value = state.body,
                            onValueChange = viewModel::setBody,
                            placeholder = { Text("Start writing…") },
                            colors = transparentTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 260.dp),
                        )
                        AttachmentStrip(
                            attachments = state.attachments,
                            playingPath = playingPath,
                            onPlay = onPlay,
                            onRemove = viewModel::removeAttachment,
                        )
                        Spacer(Modifier.height(80.dp))
                    }

                    // The ink types are drawn by InkEditor; this branch cannot be reached.
                    NoteType.DRAWING, NoteType.NOTEBOOK -> Box(Modifier.fillMaxSize())
                }
            }

            // Ink sits above the text so handwriting can annotate typed content.
            inkOverlay()
        }

        if (state.type != NoteType.TEXT) {
            AttachmentStrip(
                attachments = state.attachments,
                playingPath = playingPath,
                onPlay = onPlay,
                onRemove = viewModel::removeAttachment,
            )
        }
        TagStrip(state = state, onAdd = onAddTag, onRemove = viewModel::removeTag)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun transparentTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

@Composable
private fun ChecklistBody(state: EditorUiState, viewModel: EditorViewModel) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
    ) {
        state.checklist.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.checked,
                    onCheckedChange = { viewModel.toggleChecklistItem(item.id) },
                )
                TextField(
                    value = item.text,
                    onValueChange = { viewModel.updateChecklistItem(item.id, it) },
                    placeholder = { Text("List item") },
                    colors = transparentTextFieldColors(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.removeChecklistItem(item.id) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove item")
                }
            }
        }
        TextButton(onClick = { viewModel.addChecklistItem() }, modifier = Modifier.padding(start = 8.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Add item")
        }
        if (state.checklist.any { it.checked }) {
            Row {
                TextButton(onClick = viewModel::uncheckAll) { Text("Uncheck all") }
                TextButton(onClick = viewModel::deleteCheckedItems) { Text("Delete ticked items") }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
internal fun AttachmentStrip(
    attachments: List<AttachmentEntity>,
    playingPath: String?,
    onPlay: (String) -> Unit,
    onRemove: (AttachmentEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    LazyRow(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
    ) {
        items(attachments, key = { it.id }) { attachment ->
            when (attachment.type) {
                AttachmentType.IMAGE -> Box {
                    Image(
                        painter = rememberAsyncImagePainter(attachment.path),
                        contentDescription = "Attached image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(110.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                    )
                    IconButton(
                        onClick = { onRemove(attachment) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(28.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                    }
                }

                AttachmentType.AUDIO -> Row(
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onPlay(attachment.path) }) {
                        Icon(
                            if (playingPath == attachment.path) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = "Play recording",
                        )
                    }
                    Text(AudioRecorder.formatDuration(attachment.durationMs))
                    IconButton(onClick = { onRemove(attachment) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove recording")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagStrip(state: EditorUiState, onAdd: () -> Unit, onRemove: (Long) -> Unit) {
    LazyRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(state.tags, key = { it.id }) { tag ->
            AssistChip(
                onClick = { onRemove(tag.id) },
                label = { Text("#${tag.name}") },
                trailingIcon = {
                    Icon(Icons.Filled.Close, contentDescription = "Remove tag", Modifier.size(16.dp))
                },
            )
        }
        item {
            AssistChip(
                onClick = onAdd,
                label = { Text("Add tag") },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null, Modifier.size(16.dp))
                },
            )
        }
    }
}

/** The media bar under a typed note: pictures, a photo, a voice note, and the word count. */
@Composable
fun EditorBottomBar(
    state: EditorUiState,
    onAddImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onRecord: () -> Unit,
    onCancelRecording: () -> Unit,
    onAddChecklistItem: () -> Unit,
) {
    Column {
        HorizontalDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onAddImage) {
                Icon(Icons.Outlined.Image, contentDescription = "Add a picture")
            }
            IconButton(onClick = onTakePhoto) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = "Take a photo")
            }
            IconButton(onClick = onRecord) {
                Icon(
                    if (state.recording) Icons.Filled.Stop else Icons.Outlined.Mic,
                    contentDescription = if (state.recording) "Stop recording" else "Record a voice note",
                    tint = if (state.recording) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.recording) {
                Text(
                    AudioRecorder.formatDuration(state.recordingElapsedMs),
                    style = MaterialTheme.typography.labelLarge,
                )
                TextButton(onClick = onCancelRecording) { Text("Cancel") }
            }
            if (state.type == NoteType.CHECKLIST) {
                IconButton(onClick = onAddChecklistItem) {
                    Icon(Icons.Outlined.CheckBox, contentDescription = "Add a list item")
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${state.wordCount} words",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 14.dp),
            )
        }
    }
}
