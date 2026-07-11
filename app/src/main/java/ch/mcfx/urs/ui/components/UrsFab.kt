package ch.mcfx.urs.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.theme.UrsTheme

/**
 * Circular floating action button — replacement for
 * `material3.FloatingActionButton`. Reuses [UrsCard]'s exact shadow/border
 * treatment (elevated-surface look), just clipped to a circle instead of a
 * rounded rectangle. [content] is a plain slot (not a fixed icon) — call
 * sites keep passing their own `UrsText`/`UrsIcon`.
 *
 * Every call site positions this via `Modifier.align(Alignment.BottomEnd)
 * .padding(Spacing.l)` inside a full-size `Box` — since the app draws
 * edge-to-edge (`enableEdgeToEdge()`), that `Box` extends behind the system
 * navigation bar, so without this the FAB would end up positioned partly
 * inside the bar's reserved touch region (which the system claims
 * unconditionally, before the app ever sees the touch) rather than clearly
 * above it. Applied here, once, rather than at every call site.
 */
@Composable
fun UrsFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = UrsTheme.colors
    // Only the dark palette has a non-transparent border color (see Color.kt) — same check UrsCard uses.
    val darkTheme = colors.border.alpha > 0f

    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .size(56.dp)
            .shadow(
                elevation = 6.dp,
                shape = CircleShape,
                ambientColor = colors.shadowColor.copy(alpha = colors.shadowAlpha),
                spotColor = colors.shadowColor.copy(alpha = colors.shadowAlpha),
            )
            .clip(CircleShape)
            .background(colors.accent)
            .then(
                if (darkTheme) Modifier.border(1.dp, colors.border, CircleShape) else Modifier,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
