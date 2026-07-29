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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Glass-look card — same shadow/clip shell as [UrsCard], but a diagonal
 * accent-tinted gradient wash over the surface color and an accent-tinted
 * border on every theme, instead of a flat fill. No real blur
 * (`Modifier.blur`/`RenderEffect` needs API 31+, this app's `minSdk` is 26)
 * — the glass read comes entirely from the gradient + transparency + border,
 * so it looks identical on every supported API level.
 */
@Composable
fun UrsGlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = Radius.card,
    contentPadding: PaddingValues = PaddingValues(Spacing.l),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = UrsTheme.colors
    val shape = RoundedCornerShape(radius)
    val gradient = Brush.linearGradient(
        colors = listOf(colors.accent.copy(alpha = 0.12f), colors.accent.copy(alpha = 0f)),
    )

    Column(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = colors.shadowColor.copy(alpha = colors.shadowAlpha),
                spotColor = colors.shadowColor.copy(alpha = colors.shadowAlpha),
            )
            .clip(shape)
            .background(colors.surface)
            .background(gradient)
            .border(1.dp, colors.accent.copy(alpha = 0.18f), shape)
            .padding(contentPadding),
        content = content,
    )
}
