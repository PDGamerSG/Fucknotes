package com.example.fucknotes.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.fucknotes.data.db.AttachmentEntity
import com.example.fucknotes.data.ink.PageSpec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageImagesSheet(
    images: List<AttachmentEntity>, page: PageSpec,
    onChange: (AttachmentEntity) -> Unit, onRemove: (AttachmentEntity) -> Unit, onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(20.dp)) {
            Text("Images on this page", style = MaterialTheme.typography.titleLarge)
            if (images.isEmpty()) Text("Use Import image in the page menu to add a picture.")
            LazyColumn(Modifier.heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(images, key = { it.id }) { image ->
                    Column {
                        AsyncImage(model = java.io.File(image.path), contentDescription = "Page image",
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(140.dp))
                        Row {
                            listOf("Left" to (-16f to 0f), "Right" to (16f to 0f),
                                "Up" to (0f to -16f), "Down" to (0f to 16f)).forEach { (label, delta) ->
                                TextButton(onClick = {
                                    onChange(image.copy(
                                        pageX = (image.pageX + delta.first).coerceIn(0f, (page.widthDp - image.pageWidth).coerceAtLeast(0f)),
                                        pageY = (image.pageY + delta.second).coerceIn(0f, (page.heightDp - image.pageHeight).coerceAtLeast(0f))))
                                }) { Text(label) }
                            }
                        }
                        Row {
                            listOf("Smaller" to 0.9f, "Larger" to 1.1f).forEach { (label, factor) ->
                                TextButton(onClick = {
                                    val scale = minOf(factor, page.widthDp / image.pageWidth, page.heightDp / image.pageHeight)
                                    val width = (image.pageWidth * scale).coerceAtLeast(12f)
                                    val height = (image.pageHeight * scale).coerceAtLeast(12f)
                                    onChange(image.copy(pageWidth = width, pageHeight = height,
                                        pageX = (image.pageX + (image.pageWidth - width) / 2).coerceIn(0f, (page.widthDp - width).coerceAtLeast(0f)),
                                        pageY = (image.pageY + (image.pageHeight - height) / 2).coerceIn(0f, (page.heightDp - height).coerceAtLeast(0f))))
                                }) { Text(label) }
                            }
                            TextButton(onClick = { onRemove(image) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
}
