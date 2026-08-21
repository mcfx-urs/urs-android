package ch.mcfx.urs.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

/**
 * For a form screen's outermost `Column` (a plain vertical list of text
 * fields ending in a Save button, not a `LazyColumn`): makes it vertically
 * scrollable and clears both the system navigation bar and the on-screen
 * keyboard (unioned, not added, so the two insets don't stack while the
 * keyboard already covers the nav bar). Without this, once a bottom field
 * is focused the keyboard covers it (and the Save button) with no way to
 * scroll them into view. This exact fix was hand-copied into
 * WorkTimeAddScreen/FuelAddScreen/FuelPriceAddScreen/ServiceAddScreen/
 * VehicleAddScreen and then still missed on the next new form screen
 * (WageRulesSettingsScreen) — every scrollable form screen should use this
 * instead of reassembling the chain by hand.
 */
@Composable
fun Modifier.ursFormScrollPadding(horizontal: Dp = Spacing.xl, vertical: Dp = Spacing.xl): Modifier = this
    .verticalScroll(rememberScrollState())
    .padding(horizontal = horizontal)
    .padding(top = vertical)
    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
    .padding(bottom = vertical)
