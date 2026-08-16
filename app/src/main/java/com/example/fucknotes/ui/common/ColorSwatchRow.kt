package com.example.fucknotes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A row of colour choices.
 *
 * The tick is drawn in whichever of black or white the swatch can carry, so a white paper chip and
 * a near-black one both show their selection instead of one of them going blank.
 */
@Composable
fun ColorSwatchRow(
    colors: List<Int>,
    selectedArgb: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    swatchSize: Dp = 26.dp,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEach { argb ->
            val color = Color(argb)
            val selected = argb == selectedArgb
            Box(
                Modifier
                    .size(swatchSize)
                    .background(color, CircleShape)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .clickable { onPick(argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected",
                        modifier = Modifier.size(swatchSize * 0.55f),
                        tint = if (color.luminance() > 0.5f) Color(0xFF1B1B1F) else Color.White,
                    )
                }
            }
        }
    }
}
