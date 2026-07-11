package ch.mcfx.urs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.theme.UrsTheme

/**
 * Small rounded-square toggle — replacement for `material3.Checkbox`.
 * Unchecked: border-only. Checked: filled accent background with a
 * checkmark.
 */
@Composable
fun UrsCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UrsTheme.colors
    val shape = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .size(22.dp)
            .clip(shape)
            .then(
                if (checked) {
                    Modifier.background(colors.accent)
                } else {
                    Modifier.border(1.dp, colors.onSurfaceMuted.copy(alpha = 0.4f), shape)
                },
            )
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            UrsIcon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
