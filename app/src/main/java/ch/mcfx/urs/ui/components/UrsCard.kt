package ch.mcfx.urs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Core Begleiter card container, matching the finalized mockup's CSS
 * exactly: light theme gets a soft warm-dark drop shadow (not pure black);
 * dark theme gets a thin accent-tinted border plus a plain dark drop
 * shadow instead — no colored "glow" blur, the mockup's dark cards only
 * tint the *edge*, not a diffuse glow behind the card.
 */
@Composable
fun UrsCard(
    modifier: Modifier = Modifier,
    radius: Dp = Radius.card,
    contentPadding: PaddingValues = PaddingValues(Spacing.l),
    /** Set false for a flat, shadow-less surface — the "coming soon" tile treatment. */
    elevated: Boolean = true,
    backgroundColor: Color = UrsTheme.colors.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = UrsTheme.colors
    val shape = RoundedCornerShape(radius)
    // Only the dark palette has a non-transparent border color (see Color.kt).
    val darkTheme = colors.border.alpha > 0f

    val decoratedModifier = when {
        !elevated -> modifier.clip(shape).background(backgroundColor)
        darkTheme -> modifier
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = colors.shadowColor.copy(alpha = colors.shadowAlpha),
                spotColor = colors.shadowColor.copy(alpha = colors.shadowAlpha),
            )
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, colors.border, shape)
        else -> modifier
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = colors.shadowColor.copy(alpha = colors.shadowAlpha),
                spotColor = colors.shadowColor.copy(alpha = colors.shadowAlpha),
            )
            .clip(shape)
            .background(backgroundColor)
    }

    Column(
        modifier = decoratedModifier.padding(contentPadding),
        content = content,
    )
}
