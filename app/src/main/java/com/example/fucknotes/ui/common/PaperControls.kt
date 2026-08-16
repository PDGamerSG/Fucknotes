package com.example.fucknotes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Crop32
import androidx.compose.material.icons.outlined.CropPortrait
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fucknotes.data.ink.PageFormat
import com.example.fucknotes.data.ink.PageSpec
import com.example.fucknotes.data.ink.RuleSpacingRange
import com.example.fucknotes.data.model.PaperStyle
import com.example.fucknotes.ui.theme.Radii

/**
 * The controls that describe a sheet of paper.
 *
 * Paper is chosen twice in this app — once when a notebook is created and again when an open note
 * is re-papered — and the two have to offer the same choices in the same order, or the second one
 * reads as a different feature. They live here so both screens are the same set of controls.
 */

/** The four papers, each drawn as itself at the tones and spacing currently chosen. */
@Composable
fun TemplatePicker(
    selected: PaperStyle,
    paperColor: Color,
    lineColor: Color,
    spacingDp: Float,
    onPick: (PaperStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val templates = listOf(
        PaperStyle.PLAIN to "Plain",
        PaperStyle.LINED to "Rule",
        PaperStyle.GRID to "Grid",
        PaperStyle.DOTTED to "Dot",
    )
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        templates.forEach { (style, label) ->
            val active = selected == style
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radii.chip))
                    .clickable { onPick(style) }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .then(
                            if (active) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                            } else {
                                Modifier
                            }
                        )
                        .padding(3.dp)
                ) {
                    PaperPreview(
                        style = style,
                        modifier = Modifier.fillMaxSize(),
                        paperColor = paperColor,
                        lineColor = lineColor,
                        spacingDp = spacingDp,
                        zoom = 0.22f,
                        corner = 8.dp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Sheet size on the left, the direction it is turned on the right. */
@Composable
fun PageSizeRow(page: PageSpec, onPage: (PageSpec) -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PaperSectionLabel("Size")
        Spacer(Modifier.width(14.dp))

        Box {
            Row(
                Modifier
                    .clip(RoundedCornerShape(Radii.chip))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radii.chip))
                    .clickable { menu = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(page.format.label, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Outlined.UnfoldMore,
                    contentDescription = "Change the size",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                PageFormat.entries.forEach { format ->
                    DropdownMenuItem(
                        text = {
                            Text("${format.label}  ·  ${format.shortSideDp.toInt()} × ${format.longSideDp.toInt()} pt")
                        },
                        trailingIcon = { if (format == page.format) Icon(Icons.Filled.Check, null) },
                        onClick = {
                            menu = false
                            onPage(page.copy(format = format))
                        },
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        OrientationToggle(landscape = page.landscape, onChange = { onPage(page.copy(landscape = it)) })
    }
}

@Composable
private fun OrientationToggle(landscape: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(Radii.chip))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(Radii.chip)),
    ) {
        listOf(false to Icons.Outlined.CropPortrait, true to Icons.Outlined.Crop32).forEach { (isLandscape, icon) ->
            val active = landscape == isLandscape
            Box(
                Modifier
                    .size(width = 44.dp, height = 34.dp)
                    .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onChange(isLandscape) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = if (isLandscape) "Landscape" else "Portrait",
                    modifier = Modifier.size(18.dp),
                    tint = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Distance between rules, in points. Disabled on plain paper, which has none. */
@Composable
fun SpacingRow(
    spacingDp: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PaperSectionLabel("Spacing")
        Spacer(Modifier.width(14.dp))
        Slider(
            value = spacingDp.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = RuleSpacingRange,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "$spacingDp",
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
    }
}

@Composable
fun PaperSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/** A stored 0 means "unset", which is the one value that must not be drawn as transparent black. */
fun Int.paperColorOr(fallback: Color): Color = if (this == 0) fallback else Color(this)
