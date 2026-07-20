package ch.mcfx.urs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.theme.UrsTheme

/**
 * Centered pill-shaped bar used to visually separate stacked sections
 * (e.g. category groups in a grid), as opposed to [UrsDivider]'s thin
 * full-width gridline.
 */
@Composable
fun UrsSectionDivider(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(UrsTheme.colors.accent),
        )
    }
}
