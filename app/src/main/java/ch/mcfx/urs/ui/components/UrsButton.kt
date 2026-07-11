package ch.mcfx.urs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius

/** Filled, accent-colored action button — replacement for `material3.Button`. */
@Composable
fun UrsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = UrsTheme.colors
    val alpha = if (enabled) 1f else colors.disabledAlpha
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.card))
            .background(colors.accent.copy(alpha = alpha))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        UrsText(text = text, style = UrsTheme.typography.body, color = colors.onAccent)
    }
}

/** Outlined, border-only action button — replacement for `material3.OutlinedButton`. */
@Composable
fun UrsOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = UrsTheme.colors
    val alpha = if (enabled) 1f else colors.disabledAlpha
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.card))
            .border(1.dp, colors.accent.copy(alpha = alpha), RoundedCornerShape(Radius.card))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        UrsText(text = text, style = UrsTheme.typography.body, color = colors.accent.copy(alpha = alpha))
    }
}
