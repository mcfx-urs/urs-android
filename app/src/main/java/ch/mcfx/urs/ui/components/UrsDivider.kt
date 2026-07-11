package ch.mcfx.urs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.theme.UrsTheme

/**
 * Thin full-width gridline — replacement for `material3.HorizontalDivider`.
 * Defaults to a low-opacity muted tone since its only current use is a bar
 * chart's reference gridline, not a prominent section separator.
 */
@Composable
fun UrsDivider(
    modifier: Modifier = Modifier,
    color: Color = UrsTheme.colors.onSurfaceMuted.copy(alpha = 0.2f),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}
