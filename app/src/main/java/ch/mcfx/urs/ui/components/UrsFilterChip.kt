package ch.mcfx.urs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Small selectable stadium-shaped chip — replacement for `material3.FilterChip`.
 *
 * Unlike [UrsPill] (a static badge), this is tappable, so the selected state
 * uses a full-opacity accent fill rather than [UrsPill]'s translucent tint —
 * it needs to read clearly as "on", not just as a label. The unselected
 * state is a thin outline instead, reusing [UrsTextField]'s unfocused-border
 * color for the same "quiet, inactive" look.
 */
@Composable
fun UrsFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UrsTheme.colors
    val shape = Radius.pill

    val decoratedModifier = if (selected) {
        modifier.clip(shape).background(colors.accent)
    } else {
        modifier.clip(shape).border(1.dp, colors.onSurfaceMuted.copy(alpha = 0.3f), shape)
    }

    UrsText(
        text = label,
        style = UrsTheme.typography.body,
        color = if (selected) colors.onAccent else colors.onSurface,
        modifier = decoratedModifier
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
    )
}
