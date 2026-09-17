package ch.mcfx.urs.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import ch.mcfx.urs.ui.theme.UrsTheme

/**
 * Thin [BasicText] wrapper standing in for `material3.Text` — kept
 * intentionally minimal, this design system has no Material typography
 * scale/color defaults to fall back to.
 *
 * [maxLines]/[overflow] default to unlimited/clip, i.e. [BasicText]'s own
 * defaults — every existing call site is unaffected unless it opts in.
 */
@Composable
fun UrsText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = UrsTheme.typography.body,
    color: Color = UrsTheme.colors.onSurface,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = overflow,
    )
}
