package ch.mcfx.urs.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.theme.UrsTheme

/**
 * Fixed-size overlay panel docked to the top of the screen content, directly
 * under the app's [UrsTopBar], and stretching all the way down to just above
 * the system navigation bar — alternative to [UrsBottomSheet] for flows
 * whose content shape varies (tabs, search results, a follow-up step) but
 * whose surrounding chrome shouldn't resize/jump as a result (the shopping
 * list's add-product flow: HÄUFIG/ZULETZT/KATEGORIEN/search results and its
 * note-entry follow-up all render inside these exact same bounds). Being
 * docked flush on every edge (no floating margin anywhere), it's drawn as a
 * plain rectangle — no corner rounding, no drop shadow, just a border in the
 * dark palette (same check [UrsCard] uses).
 *
 * Unlike [UrsBottomSheet] there's no drag-to-dismiss — a fully docked panel
 * has nothing to "throw away" toward, and no visible scrim area remains to
 * tap outside of (it's opaque and fills the screen, so a tap-outside-to-
 * dismiss layer behind it would only ever catch stray taps that fall
 * through gaps in [content] — not an intentional "outside" gesture). Dismiss
 * is the close affordance [content] itself provides, or the system back
 * gesture below.
 */
@Composable
fun UrsDockedPanel(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = UrsTheme.colors
    // Only the dark palette has a non-transparent border color (see Color.kt) — same check UrsCard uses.
    val darkTheme = colors.border.alpha > 0f

    BackHandler(onBack = onDismissRequest)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight()
                .background(colors.surface)
                .then(
                    if (darkTheme) Modifier.border(1.dp, colors.border) else Modifier,
                )
                // Docked top-to-bottom, so its own bottom edge must stop
                // above both the system navigation bar and the keyboard.
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
            content = content,
        )
    }
}
