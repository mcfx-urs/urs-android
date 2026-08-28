package ch.mcfx.urs.home

import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.expandVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.fuel.FuelRoutes
import ch.mcfx.urs.location.LOCATION_PERMISSIONS
import ch.mcfx.urs.location.hasLocationPermission
import ch.mcfx.urs.navigation.Destination
import ch.mcfx.urs.settings.SettingsRoutes
import ch.mcfx.urs.ui.components.UrsGlassCard
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.ursScreenContentPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

// WORK_TIME leads the list on purpose: it's the day-to-day recurring entry
// (unlike an occasional fuel fill-up), so it takes over the featured,
// full-width top slot — see WorkTimeCard below, which always renders
// whichever destination is first here. The "New Fuel Fill" shortcut right
// below it is a separate, non-Destination full-width row (see
// NewFuelFillCard) since it navigates straight to FuelRoutes.ADD rather
// than a Destination's own route — it isn't part of this list. LIFE_MAP is
// pulled out of the plain tile grid entirely (see LifeMapCard) for its own
// larger, full-width slot with a live location preview.
private val FEATURE_TILES = listOf(
    Destination.WORK_TIME,
    Destination.SHOPPING_LIST,
    Destination.VEHICLE,
    Destination.INVENTORY,
    Destination.BEER,
    Destination.LIFE_MAP,
    Destination.BAKING,
    Destination.NOTES,
    Destination.VOICE_NOTES,
    Destination.CHORES,
    Destination.PRICE_MONITOR,
    Destination.K,
    Destination.GOKART,
)

private val TILE_IMAGE = mapOf(
    Destination.SHOPPING_LIST to R.drawable.tile_shopping_list,
    Destination.VEHICLE to R.drawable.tile_vehicle,
    Destination.INVENTORY to R.drawable.tile_inventory,
    Destination.BEER to R.drawable.tile_beer,
    Destination.BAKING to R.drawable.tile_baking,
    Destination.NOTES to R.drawable.tile_notes,
    // Interim: reuses the Notes tile art until Voice Notes gets its own.
    Destination.VOICE_NOTES to R.drawable.tile_notes,
    // Interim: reuses the K tile art until Chores gets its own commissioned
    // piece (GitHub issue #27, part B).
    Destination.CHORES to R.drawable.tile_k,
    Destination.PRICE_MONITOR to R.drawable.tile_price_monitor,
    Destination.K to R.drawable.tile_k,
    Destination.GOKART to R.drawable.tile_gokart,
)

private val TileHeight = 112.dp
private val TileBleedImageSize = 92.dp
private val WideTileHeight = 80.dp
private val LifeMapCardHeight = 190.dp
private val MiniMapZoom = 15.0

// Header collapses once the grid has scrolled past this many pixels of its
// first item — not just "scrollOffset > 0", so a tiny accidental drag
// doesn't immediately snap the header shut.
private const val HeaderCollapseThresholdPx = 12

@Composable
fun HomeScreen(
    onNavigate: (Destination) -> Unit,
    onNavigateRoute: (route: String) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val location by viewModel.currentLocation.collectAsStateWithLifecycle()
    val featured = FEATURE_TILES.first()
    val tiles = FEATURE_TILES.drop(1).filter { it != Destination.LIFE_MAP }

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

    val gridState = rememberLazyGridState()
    val isHeaderCollapsed by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > HeaderCollapseThresholdPx
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(UrsTheme.colors.background)) {
        HomeHeader(
            username = viewModel.username,
            isCollapsed = isHeaderCollapsed,
            onSettingsClick = { onNavigateRoute(SettingsRoutes.HUB) },
            onAboutClick = { onNavigateRoute(SettingsRoutes.ABOUT) },
        )
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(2),
            contentPadding = ursScreenContentPadding(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                WorkTimeCard(onClick = { onNavigate(featured) })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                NewFuelFillCard(
                    subtitle = uiState.fuelAvgConsumptionL100Km?.let {
                        stringResource(R.string.fuel_avg_consumption_6mo, it)
                    },
                    onClick = { onNavigateRoute(FuelRoutes.ADD) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LifeMapCard(location = location, onClick = { onNavigate(Destination.LIFE_MAP) })
            }
            items(
                items = tiles,
                span = { destination -> if (destination == Destination.GOKART) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
            ) { destination ->
                if (destination == Destination.GOKART) {
                    WideBleedTile(destination = destination, onClick = { onNavigate(destination) })
                } else {
                    CornerBleedTile(
                        destination = destination,
                        subtitle = quickStat(destination, uiState),
                        onClick = { onNavigate(destination) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    username: String?,
    isCollapsed: Boolean,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
) {
    val colors = UrsTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = Spacing.l)
            .padding(top = Spacing.m),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Image(
                    painter = painterResource(R.drawable.urs_bear_logo),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                UrsText(stringResource(R.string.app_name), style = UrsTheme.typography.brand, color = colors.accent)
            }
            UrsIconButton(
                onClick = onSettingsClick,
                contentDescription = stringResource(R.string.nav_settings),
                imageVector = Icons.Filled.Settings,
                tint = colors.accent,
            )
        }

        AnimatedVisibility(
            visible = !isCollapsed,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.m)
                    .height(150.dp)
                    .clip(RoundedCornerShape(Radius.card)),
            ) {
                Image(
                    painter = painterResource(R.drawable.home_hero),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(colors.background.copy(alpha = 0f), colors.background.copy(alpha = 0.85f)),
                                startY = 40f,
                            ),
                        ),
                )
                UrsIconButton(
                    onClick = onAboutClick,
                    contentDescription = stringResource(R.string.settings_tile_about),
                    imageVector = Icons.Filled.Info,
                    tint = colors.accent,
                    modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.s),
                )
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.m)) {
                    UrsText(
                        stringResource(R.string.home_welcome_eyebrow),
                        style = UrsTheme.typography.body,
                        color = colors.onSurfaceMuted,
                    )
                    if (username != null) {
                        UrsText(username, style = UrsTheme.typography.brand, color = colors.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun quickStat(destination: Destination, state: HomeUiState): String? = when (destination) {
    Destination.BEER -> state.daysSinceLastBeer?.let {
        stringResource(R.string.home_days_since_beer, it)
    }
    else -> null
}

@Composable
private fun WorkTimeCard(onClick: () -> Unit) {
    UrsGlassCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Image(
                painter = painterResource(R.drawable.tile_work_time),
                contentDescription = null,
                modifier = Modifier.size(52.dp).clip(CircleShape),
            )
            UrsText(
                text = stringResource(Destination.WORK_TIME.labelRes),
                style = UrsTheme.typography.cardTitle,
                color = UrsTheme.colors.accent,
            )
        }
    }
}

@Composable
private fun NewFuelFillCard(subtitle: String?, onClick: () -> Unit) {
    val colors = UrsTheme.colors

    UrsGlassCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Image(
                    painter = painterResource(R.drawable.tile_fuel),
                    contentDescription = null,
                    modifier = Modifier.size(46.dp).clip(CircleShape),
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    UrsText(text = stringResource(R.string.home_new_fuel_fill), style = UrsTheme.typography.cardTitle, color = colors.accent)
                    if (subtitle != null) {
                        UrsText(text = subtitle, style = UrsTheme.typography.statAccent, color = colors.accent)
                    }
                }
            }
        }
    }
}

/**
 * Life Map's Home tile: a real, non-interactive preview of the last known
 * location (osmdroid — the same library `LifeMapScreen` already uses),
 * centered on [location]. Falls back to a plain illustration only until a
 * location is available (no permission yet, or the very first app launch
 * before a fix has landed) — never a fake/placeholder coordinate.
 */
@Composable
private fun LifeMapCard(location: Location?, onClick: () -> Unit) {
    val colors = UrsTheme.colors

    UrsGlassCard(
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.fillMaxWidth().height(LifeMapCardHeight),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                UrsText(
                    text = stringResource(Destination.LIFE_MAP.labelRes),
                    style = UrsTheme.typography.cardTitle,
                    color = colors.accent,
                    modifier = Modifier.padding(Spacing.m),
                )
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (location != null) {
                        MiniMapView(location = location, modifier = Modifier.fillMaxSize())
                    } else {
                        Image(
                            painter = painterResource(R.drawable.tile_life_map),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            // Transparent tap target drawn last (on top of the map), so the
            // whole tile navigates to the full Life Map screen and the
            // embedded preview never intercepts the tap as a pan/zoom gesture.
            Box(modifier = Modifier.fillMaxSize().clickable(onClick = onClick))
        }
    }
}

@Composable
private fun MiniMapView(location: Location, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(false)
            setBuiltInZoomControls(false)
            isClickable = false
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.clipToBounds(),
        update = { view ->
            val point = GeoPoint(location.latitude, location.longitude)
            // Deferred via view.post() — centering before the view has a
            // valid (non-zero) layout size computes the geo-to-screen
            // projection against a zero-size rect and silently lands on the
            // wrong spot (same root cause LifeMapScreen already works around).
            view.post {
                view.controller.setZoom(MiniMapZoom)
                view.controller.setCenter(point)
                view.overlays.clear()
                view.overlays.add(Marker(view).apply { position = point })
                view.invalidate()
            }
        },
    )
}

@Composable
private fun CornerBleedTile(destination: Destination, subtitle: String?, onClick: () -> Unit) {
    val colors = UrsTheme.colors

    UrsGlassCard(
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(TileHeight)
            .alpha(if (destination.isAvailable) 1f else colors.disabledAlpha)
            .clickable(enabled = destination.isAvailable, onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(TILE_IMAGE.getValue(destination)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = 10.dp)
                    .size(TileBleedImageSize),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colorStops = arrayOf(
                                0f to colors.surface,
                                0.42f to colors.surface,
                                0.78f to colors.surface.copy(alpha = 0f),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.m)
                    .fillMaxWidth(0.62f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                UrsText(text = stringResource(destination.labelRes), style = UrsTheme.typography.cardTitle, color = colors.accent)
                if (subtitle != null) {
                    UrsText(text = subtitle, style = UrsTheme.typography.statAccent, color = colors.accent)
                }
                if (!destination.isAvailable) {
                    UrsPill(
                        text = stringResource(R.string.coming_soon).uppercase(),
                        containerColor = colors.surface,
                        contentColor = colors.onSurfaceMuted,
                        style = UrsTheme.typography.tag,
                    )
                }
            }
        }
    }
}

@Composable
private fun WideBleedTile(destination: Destination, onClick: () -> Unit) {
    val colors = UrsTheme.colors

    UrsGlassCard(
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(WideTileHeight)
            .alpha(if (destination.isAvailable) 1f else colors.disabledAlpha)
            .clickable(enabled = destination.isAvailable, onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(TILE_IMAGE.getValue(destination)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.55f).fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to colors.surface,
                                0.5f to colors.surface,
                                0.85f to colors.surface.copy(alpha = 0f),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier.align(Alignment.CenterStart).padding(Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                UrsText(text = stringResource(destination.labelRes), style = UrsTheme.typography.cardTitle, color = colors.accent)
                if (!destination.isAvailable) {
                    UrsPill(
                        text = stringResource(R.string.coming_soon).uppercase(),
                        containerColor = colors.surface,
                        contentColor = colors.onSurfaceMuted,
                        style = UrsTheme.typography.tag,
                    )
                }
            }
        }
    }
}
