package ch.mcfx.urs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * One row in [UrsNavigationDrawer] — replacement for `material3.NavigationDrawerItem`.
 * Disabled items (features not built yet) are visually dimmed via the
 * theme's `disabledAlpha` and not clickable at all, rather than
 * clickable-but-a-no-op.
 */
@Composable
fun UrsNavigationDrawerItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = UrsTheme.colors
    val shape = RoundedCornerShape(Radius.card)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else colors.disabledAlpha)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        UrsIcon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.accent else colors.onSurface,
            modifier = Modifier.size(24.dp),
        )
        UrsText(
            text = label,
            style = UrsTheme.typography.cardTitle,
            color = if (selected) colors.accent else colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}
