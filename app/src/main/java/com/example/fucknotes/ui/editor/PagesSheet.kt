package com.example.fucknotes.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.ink.PageSpec
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.ui.common.PaperPreview
import com.example.fucknotes.ui.common.paperColorOr
import com.example.fucknotes.ui.theme.brand

/**
 * Every page of the notebook, to jump between.
 *
 * The thumbnails are paper only — rendering the ink of a long notebook at thumbnail size would cost
 * a full stroke pass per sheet — so the page number does the identifying. It is a way of moving,
 * not a way of reading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesSheet(
    pageCount: Int,
    currentPage: Int,
    style: PaperStyle,
    paperColorArgb: Int,
    lineColorArgb: Int,
    spacingDp: Float,
    page: PageSpec,
    onGoToPage: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Pages", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "$pageCount ${if (pageCount == 1) "page" else "pages"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 420.dp),
            ) {
                items((0 until pageCount).toList()) { index ->
                    PageThumb(
                        index = index,
                        current = index == currentPage,
                        style = style,
                        paperColorArgb = paperColorArgb,
                        lineColorArgb = lineColorArgb,
                        spacingDp = spacingDp,
                        page = page,
                        onClick = { onGoToPage(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PageThumb(
    index: Int,
    current: Boolean,
    style: PaperStyle,
    paperColorArgb: Int,
    lineColorArgb: Int,
    spacingDp: Float,
    page: PageSpec,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(page.widthDp / page.heightDp)
                .clickable(onClick = onClick)
                .then(
                    if (current) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    } else {
                        Modifier
                    }
                )
        ) {
            PaperPreview(
                style = style,
                modifier = Modifier.fillMaxSize(),
                paperColor = paperColorArgb.paperColorOr(Color.White),
                lineColor = lineColorArgb.paperColorOr(MaterialTheme.brand.tileBorder),
                spacingDp = spacingDp,
                zoom = 0.16f,
                corner = 8.dp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${index + 1}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
            color = if (current) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
