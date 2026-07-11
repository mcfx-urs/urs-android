package ch.mcfx.urs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius

/**
 * Small stadium-shaped badge. Two distinct uses in the mockup, both built
 * on this one composable via its color/style params:
 * - status pill ("Granted"): translucent accent tint, `statAccent` style —
 *   `.row .status { background: color-mix(accent 15%, transparent) }`.
 * - tag ("soon"): opaque surface-colored background, muted text, `tag`
 *   style — `.card.soon .tag { background: var(--card) }`.
 */
@Composable
fun UrsPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = UrsTheme.colors.accent.copy(alpha = 0.15f),
    contentColor: Color = UrsTheme.colors.accent,
    style: TextStyle = UrsTheme.typography.statAccent,
) {
    UrsText(
        text = text,
        style = style,
        color = contentColor,
        modifier = modifier
            .clip(Radius.pill)
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
