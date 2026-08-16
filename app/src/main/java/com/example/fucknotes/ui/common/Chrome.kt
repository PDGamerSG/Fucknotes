package com.example.fucknotes.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fucknotes.ui.theme.Radii
import com.example.fucknotes.ui.theme.brand

/**
 * The app's floating chrome.
 *
 * Everything that hovers over a page — the toolbars, the zoom readout, the page counter, the create
 * menu — is the same material: a soft-cornered panel with one hairline and a low, wide shadow. It is
 * defined once here so a new control cannot drift into being a slightly different white.
 */
@Composable
fun FloatingPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radii.pill),
    elevation: Dp = 10.dp,
    content: @Composable () -> Unit,
) {
    val brand = MaterialTheme.brand
    Box(
        modifier
            .shadow(elevation, shape, ambientColor = Color(0x14000000), spotColor = Color(0x1F000000))
            .clip(shape)
            .background(brand.chrome)
            .border(1.dp, brand.chromeBorder, shape),
        content = { content() },
    )
}

/**
 * A control at writing-app scale.
 *
 * Material's `IconButton` reserves a 48 dp target, which is right for a form and wrong for a bar
 * that sits above a page all day; this is a plain clickable box, so eight tools and a pen tray fit
 * in one short row without the bar eating the sheet.
 */
@Composable
fun ToolButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    iconSize: Dp = 21.dp,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(iconSize),
            tint = when {
                !enabled -> scheme.onSurfaceVariant.copy(alpha = 0.35f)
                selected -> scheme.primary
                else -> scheme.onSurfaceVariant
            },
        )
    }
}

/** A floating readout — the zoom percentage, the page counter — optionally with a leading icon. */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(Radii.pill)
    Row(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.surface,
        )
        trailing?.let {
            Spacer(Modifier.width(6.dp))
            it()
        }
    }
}

/** A thin vertical rule used to group tools inside a floating bar. */
@Composable
fun ToolDivider(modifier: Modifier = Modifier, height: Dp = 20.dp) {
    Box(
        modifier
            .padding(horizontal = 4.dp)
            .size(width = 1.dp, height = height)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
