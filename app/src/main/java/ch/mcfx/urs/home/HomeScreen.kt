package ch.mcfx.urs.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.fuel.FuelRoutes
import ch.mcfx.urs.location.LOCATION_PERMISSIONS
import ch.mcfx.urs.location.hasLocationPermission
import ch.mcfx.urs.navigation.Destination
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

// WORK_TIME leads the list on purpose: it's the day-to-day recurring entry
// (unlike an occasional fuel fill-up), so it takes over the featured,
// two-column top slot — see FeaturedCard below, which always renders
// whichever destination is first here. The "New Fuel Fill" shortcut right
// below it is a separate, non-Destination full-width row (see
// NewFuelFillCard) since it navigates straight to FuelRoutes.ADD rather
// than a Destination's own route — it isn't part of this list.
private val FEATURE_TILES = listOf(
    Destination.WORK_TIME,
    Destination.SHOPPING_LIST,
    Destination.VEHICLE,
    Destination.INVENTORY,
    Destination.BEER,
    Destination.LIFE_MAP,
    Destination.PRICE_MONITOR,
    Destination.K,
    Destination.GOKART,
)

// The mockup uses raw emoji as tile icons (colorful, not tinted vectors) —
// a deliberate style choice, confirmed against the finalized artifact
// rather than treated as a placeholder. `Destination.icon` (an ImageVector)
// stays as-is for contexts that still want a single-tone icon (e.g. the
// nav drawer once it's rebuilt); this is a separate, Home-only mapping.
// Android's emoji glyphs render with noticeably more internal padding than
// the mockup's browser-rendered ones at the same nominal font-size, so
// these are bumped well past the mockup's literal 30px/22px CSS values to
// match visually rather than numerically.
private val FeaturedIconStyle = TextStyle(fontSize = 42.sp)
private val TileIconStyle = TextStyle(fontSize = 32.sp)

// LazyVerticalGrid doesn't stretch a row's shorter cells to match its
// tallest one — each tile's card would otherwise size to only its own
// content (a subtitle/"SOON" pill makes a tile taller than one with just a
// title), so two tiles side by side in the same row could end up visibly
// different heights. Fixing every FeatureTile to this height, regardless
// of which of the three content shapes below it renders, keeps the whole
// grid visually even.
private val TileHeight = 116.dp

private val TILE_EMOJI = mapOf(
    Destination.WORK_TIME to "🕒",
    Destination.SHOPPING_LIST to "🛒",
    Destination.VEHICLE to "🚗",
    Destination.INVENTORY to "📦",
    Destination.BEER to "🍺",
    Destination.LIFE_MAP to "🗺️",
    Destination.GOKART to "🏁",
    Destination.PRICE_MONITOR to "📷",
    Destination.K to "❓",
)

@Composable
fun HomeScreen(
    onNavigate: (Destination) -> Unit,
    onNavigateRoute: (route: String) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val featured = FEATURE_TILES.first()
    val tiles = FEATURE_TILES.drop(1)

    val context = LocalContext.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> if (result.values.any { it }) viewModel.refreshLocation() }

    // Proactive, one-time request — not re-shown on every Home visit
    // once the user has answered it (granted or denied) the first time.
    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) {
            viewModel.refreshLocation()
        } else if (!viewModel.hasPromptedLocationPermission()) {
            viewModel.markLocationPermissionPrompted()
            locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        // Edge-to-edge (MainActivity's enableEdgeToEdge()) means the grid
        // draws behind the system nav bar unless told otherwise — the last
        // tile row would otherwise sit partly underneath/obscured by it,
        // same class of bug UrsFab/FuelStationMapScreen already work around
        // for their own floating controls.
        contentPadding = PaddingValues(
            start = Spacing.l,
            end = Spacing.l,
            top = Spacing.l,
            bottom = Spacing.l + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { WelcomeLede() }
        item(span = { GridItemSpan(maxLineSpan) }) {
            FeaturedCard(
                destination = featured,
                subtitle = quickStat(featured, uiState),
                onClick = { onNavigate(featured) },
            )
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            NewFuelFillCard(
                subtitle = uiState.fuelAvgConsumptionL100Km?.let {
                    stringResource(R.string.fuel_avg_consumption_6mo, it)
                },
                onClick = { onNavigateRoute(FuelRoutes.ADD) },
            )
        }
        items(tiles) { destination ->
            FeatureTile(
                destination = destination,
                subtitle = quickStat(destination, uiState),
                onClick = { onNavigate(destination) },
            )
        }
    }
}

// The "urs" wordmark + bear logo live in the real TopAppBar for Home
// (AppNavigation.kt) — matching the mockup's `.topbar`, which is the
// screen's actual app bar, not a second in-content header. Only the
// greeting line (`.lede`) belongs to the screen content itself.
@Composable
private fun WelcomeLede() {
    UrsText(
        text = stringResource(R.string.home_welcome_back),
        style = UrsTheme.typography.body,
        color = UrsTheme.colors.onSurfaceMuted,
    )
}

// Full-width shortcut straight to FuelRoutes.ADD, skipping the Fuel hub —
// not Destination-backed (unlike FeaturedCard/FeatureTile) since it
// navigates to a plain sub-route, not a top-level Destination.
@Composable
private fun NewFuelFillCard(subtitle: String?, onClick: () -> Unit) {
    val colors = UrsTheme.colors

    UrsCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                UrsText(
                    text = stringResource(R.string.home_new_fuel_fill),
                    style = UrsTheme.typography.cardTitle,
                    color = colors.accent,
                )
                if (subtitle != null) {
                    UrsText(text = subtitle, style = UrsTheme.typography.statAccent, color = colors.accent)
                }
            }
            UrsText(text = "⛽", style = FeaturedIconStyle)
        }
    }
}

// Fuel's average-consumption stat now lives on the New Fuel Fill shortcut
// (see NewFuelFillCard), not a Destination tile — other tiles simply show
// none until they have data worth surfacing here too.
@Composable
private fun quickStat(destination: Destination, state: HomeUiState): String? = when (destination) {
    Destination.BEER -> state.daysSinceLastBeer?.let {
        stringResource(R.string.home_days_since_beer, it)
    }
    else -> null
}

@Composable
private fun FeaturedCard(destination: Destination, subtitle: String?, onClick: () -> Unit) {
    val colors = UrsTheme.colors

    UrsCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = destination.isAvailable, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                UrsText(
                    text = stringResource(destination.labelRes),
                    style = UrsTheme.typography.cardTitle,
                    color = colors.accent,
                )
                if (subtitle != null) {
                    UrsText(text = subtitle, style = UrsTheme.typography.statAccent, color = colors.accent)
                }
            }
            UrsText(text = TILE_EMOJI.getValue(destination), style = FeaturedIconStyle)
        }
    }
}

@Composable
private fun FeatureTile(destination: Destination, subtitle: String?, onClick: () -> Unit) {
    val colors = UrsTheme.colors

    if (!destination.isAvailable) {
        UrsCard(
            elevated = false,
            backgroundColor = lerp(colors.background, colors.surface, 0.7f),
            modifier = Modifier.fillMaxWidth().height(TileHeight),
        ) {
            Column(
                modifier = Modifier.alpha(colors.disabledAlpha),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                UrsText(text = TILE_EMOJI.getValue(destination), style = TileIconStyle)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    UrsText(text = stringResource(destination.labelRes), style = UrsTheme.typography.cardTitle)
                    UrsPill(
                        text = stringResource(R.string.coming_soon).uppercase(),
                        containerColor = colors.surface,
                        contentColor = colors.onSurfaceMuted,
                        style = UrsTheme.typography.tag,
                    )
                }
            }
        }
        return
    }

    UrsCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(TileHeight)
            .clickable(onClick = onClick),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            UrsText(text = TILE_EMOJI.getValue(destination), style = TileIconStyle)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                UrsText(text = stringResource(destination.labelRes), style = UrsTheme.typography.cardTitle, color = colors.accent)
                if (subtitle != null) {
                    UrsText(text = subtitle, style = UrsTheme.typography.statAccent, color = colors.accent)
                }
            }
        }
    }
}
