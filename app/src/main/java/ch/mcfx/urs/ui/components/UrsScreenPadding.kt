package ch.mcfx.urs.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Content padding for a screen's outermost scrollable container
 * (`LazyColumn`/`LazyVerticalGrid`) — same shape as a plain [PaddingValues]
 * except the bottom inset also reserves room for the system navigation bar.
 * Edge-to-edge (`enableEdgeToEdge()`) means that bar draws over the app, so
 * without this the last row ends up resting behind/under it instead of
 * fully scrollable clear of it. Every screen's top-level scroll container
 * should use this instead of a bare `PaddingValues(...)`.
 */
@Composable
fun ursScreenContentPadding(
    horizontal: Dp = Spacing.l,
    vertical: Dp = Spacing.l,
): PaddingValues {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(start = horizontal, end = horizontal, top = vertical, bottom = vertical + bottomInset)
}

/**
 * Just the system navigation bar's own bottom inset, nothing else — for a
 * scroll container nested inside a parent that already applies the regular
 * screen padding on every side, where only the extra system-bar clearance
 * is still missing.
 */
@Composable
fun ursNavigationBarsBottomPadding(): PaddingValues =
    PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
