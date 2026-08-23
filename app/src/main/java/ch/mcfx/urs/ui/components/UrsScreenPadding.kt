package ch.mcfx.urs.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.imeAnimationTarget
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
 *
 * The insets padding deliberately wraps (comes before) `verticalScroll`
 * rather than sitting inside it as ordinary content padding — same total
 * scrollable distance either way, but only this order actually shrinks the
 * scrollable container's own measured viewport by the keyboard's height.
 * With insets applied as plain inner padding instead, the container still
 * reports its *un-shrunk*, keyboard-covered bounds as "visible", so a
 * cursor sitting behind the keyboard reads as already in view to any
 * bring-into-view request (Compose's own auto-scroll for a focused text
 * field, or a manual `BringIntoViewRequester` call) — nothing ever scrolls
 * to reveal it. Confirmed via `RichTextField`'s note-content field, the
 * first field in this app tall enough for the cursor to end up behind the
 * keyboard while still being actively typed into.
 *
 * Uses [WindowInsets.Companion.imeAnimationTarget] rather than the plain,
 * live-animating [WindowInsets.Companion.ime]: the target reports the
 * keyboard's *final* resting height immediately, not interpolated across
 * the show/hide animation, so this container's own measured size is
 * already correct on the very first frame after focus instead of shrinking
 * gradually alongside the keyboard. That in turn matters for any
 * bring-into-view behavior reacting to focus (this app's own, or Compose's
 * built-in default) — computed against a live-resizing ancestor, it's
 * racing a moving target and typically ends up needing a second, visibly
 * separate correction once the resize actually finishes; computed against
 * an ancestor that's already its final size, it should only need one.
 *
 * [scrollState] defaults to a freshly `remember`ed one (every existing call
 * site keeps working unchanged) — pass one explicitly only when a caller
 * needs to drive this exact scroll position itself (e.g. `RichTextField`
 * scrolling its own cursor into view directly, bypassing the bring-into-view
 * request chain entirely rather than layering another request onto it).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Modifier.ursFormScrollPadding(
    horizontal: Dp = Spacing.xl,
    vertical: Dp = Spacing.xl,
    scrollState: ScrollState = rememberScrollState(),
): Modifier = this
    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.imeAnimationTarget))
    .verticalScroll(scrollState)
    .padding(horizontal = horizontal, vertical = vertical)
