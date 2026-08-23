package ch.mcfx.urs.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.theme.UrsTheme

/**
 * Tappable, tinted vector icon with a minimum 48dp touch target — replacement
 * for `material3.IconButton` + `material3.Icon` combined.
 *
 * [size]/[iconSize] default to that 48dp/24dp pair everywhere; pass smaller
 * values only for a deliberately dense row of many icons (e.g. the note
 * editor's formatting toolbar) where the default spacing wouldn't fit.
 */
@Composable
fun UrsIconButton(
    onClick: () -> Unit,
    contentDescription: String?,
    imageVector: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = UrsTheme.colors.onSurface,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
) {
    val colors = UrsTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        UrsIcon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = colors.disabledAlpha),
            modifier = Modifier.size(iconSize),
        )
    }
}
