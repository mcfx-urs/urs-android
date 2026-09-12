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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.InventoryEntity
import ch.mcfx.urs.data.local.ListEntity
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.data.sync.SyncPhase
import ch.mcfx.urs.fuel.FuelRoutes
import ch.mcfx.urs.inventory.InventoryRoutes
import ch.mcfx.urs.location.LOCATION_PERMISSIONS
import ch.mcfx.urs.location.hasLocationPermission
import ch.mcfx.urs.navigation.Destination
import ch.mcfx.urs.settings.SettingsRoutes
import ch.mcfx.urs.shoppinglist.ShoppingListRoutes
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsGlassCard
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.ursScreenContentPadding
import ch.mcfx.urs.ui.icons.IconCatalog
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Launcher-style Home grid (GitHub issue #12) — every tile (including what
 * used to be the bespoke Work Time / New Fuel Fill / Life Map rows) is now
 * one generic [HomeTileBody] placed by [HomeTileGrid] according to
 * [HomeViewModel.layout]. Long-press any tile to enter edit mode and select
 * it in the same gesture; while in edit mode, tap a different tile to
 * select it instead (corner dots resize, dragging its body moves it, the
 * top-right icon removes it), tap empty grid space or "Done" in the header
 * to exit.
 *
 * Dragging a tile onto another one reorders them (see [HomeLayoutEngine] —
 * the tile order is the authoritative layout state; `(column, row)` is
 * always derived from it, never stored directly). The swap only commits
 * once the drag has hovered the same target tile for [HoverCommitDelayMs],
 * so passing over several tiles on the way somewhere doesn't reflow the
 * grid for each one; releasing before that commits immediately.
 */
private val TILE_IMAGE: Map<String, Int> = mapOf(
    Destination.WORK_TIME.name to R.drawable.tile_work_time,
    NEW_FUEL_FILL_TILE_ID to R.drawable.tile_fuel,
    Destination.SHOPPING_LIST.name to R.drawable.tile_shopping_list,
    Destination.VEHICLE.name to R.drawable.tile_vehicle,
    Destination.INVENTORY.name to R.drawable.tile_inventory,
    Destination.BEER.name to R.drawable.tile_beer,
    Destination.BAKING.name to R.drawable.tile_baking,
    Destination.NOTES.name to R.drawable.tile_notes,
    // Interim: reuses the Notes tile art until Voice Notes gets its own.
    Destination.VOICE_NOTES.name to R.drawable.tile_notes,
    // Interim: reuses the K tile art until Chores gets its own commissioned
    // piece (GitHub issue #27, part B).
    Destination.CHORES.name to R.drawable.tile_k,
    Destination.PRICE_MONITOR.name to R.drawable.tile_price_monitor,
    Destination.K.name to R.drawable.tile_k,
    Destination.GOKART.name to R.drawable.tile_gokart,
    Destination.LIFE_MAP.name to R.drawable.tile_life_map,
    // Interim: reuses the K tile art until Kanban gets its own commissioned
    // piece, same as Chores above.
    Destination.KANBAN.name to R.drawable.tile_k,
)

private val TileHeight = 112.dp
private val TileBleedImageSize = 92.dp
private val TileBleedImageSizeTall = 132.dp
private val MiniMapZoom = 15.0
private val ResizeHandleSize = 24.dp
private val SelectionBorderWidth = 2.dp
private val FavoriteBadgeSize = 28.dp
// 1x1 tile: favorites stack vertically, up to 2 fit comfortably in the
// available height. A wider or taller tile has room for a horizontal row of
// 3 instead. Anything beyond that limit is simply not shown.
private const val MaxFavoriteBadgesCompact = 2
private const val MaxFavoriteBadgesWide = 3

// How long a dragged tile must hover over the same cell before the rest of
// the grid reflows around it — long enough that passing over several cells
// on the way somewhere doesn't shove other tiles around each time.
private const val HoverCommitDelayMs = 500L
private val RemoveIconSize = 28.dp

// Header collapses once the grid has scrolled past this many pixels — not
// just "scrollOffset > 0", so a tiny accidental drag doesn't immediately
// snap the header shut.
private const val HeaderCollapseThresholdPx = 12

@Composable
fun HomeScreen(
    onNavigate: (Destination) -> Unit,
    onNavigateRoute: (route: String) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val favoriteLists by viewModel.favoriteLists.collectAsStateWithLifecycle()
    val favoriteInventories by viewModel.favoriteInventories.collectAsStateWithLifecycle()
    val location by viewModel.currentLocation.collectAsStateWithLifecycle()
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val selectedTileId by viewModel.selectedTileId.collectAsStateWithLifecycle()
    val showAddTilePicker by viewModel.showAddTilePicker.collectAsStateWithLifecycle()
    val syncPhase by viewModel.syncPhase.collectAsStateWithLifecycle()

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

    val scrollState = rememberScrollState()
    val isHeaderCollapsed = scrollState.value > HeaderCollapseThresholdPx

    Column(modifier = Modifier.fillMaxSize().background(UrsTheme.colors.background)) {
        HomeHeader(
            username = viewModel.username,
            isCollapsed = isHeaderCollapsed,
            editMode = editMode,
            syncPhase = syncPhase,
            onSettingsClick = { onNavigateRoute(SettingsRoutes.HUB) },
            onAboutClick = { onNavigateRoute(SettingsRoutes.ABOUT) },
            onDoneClick = viewModel::exitEditMode,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                // Tiles consume their own taps first (see HomeTileItem); this
                // only ever fires for a tap on empty grid space — exactly
                // "tap outside any tile" (point 8).
                .then(
                    if (editMode) {
                        Modifier.pointerInput(Unit) { detectTapGestures(onTap = { viewModel.exitEditMode() }) }
                    } else {
                        Modifier
                    },
                )
                .padding(ursScreenContentPadding()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            HomeTileGrid(
                layout = layout,
                editMode = editMode,
                selectedTileId = selectedTileId,
                uiState = uiState,
                favoriteLists = favoriteLists,
                favoriteInventories = favoriteInventories,
                location = location,
                onTileClick = { id ->
                    if (editMode) viewModel.toggleSelected(id) else navigateTo(id, onNavigate, onNavigateRoute)
                },
                onLongPress = { id -> viewModel.enterEditMode(selecting = id) },
                onMoveTile = viewModel::moveTile,
                onResizeTile = viewModel::resizeTile,
                onRemoveTile = viewModel::removeTile,
                onOpenFavoriteRoute = onNavigateRoute,
            )
            if (editMode) {
                EditModeExtraRow(
                    icon = Icons.Filled.Add,
                    label = stringResource(R.string.home_edit_add_tile),
                    onClick = viewModel::openAddTilePicker,
                )
                EditModeExtraRow(
                    icon = Icons.Filled.Refresh,
                    label = stringResource(R.string.home_edit_reset),
                    onClick = viewModel::resetLayoutToDefault,
                )
            }
        }
    }

    if (showAddTilePicker) {
        UrsBottomSheet(onDismissRequest = viewModel::dismissAddTilePicker) {
            AddTilePickerContent(tiles = viewModel.addableTiles, onPick = viewModel::addTile)
        }
    }
}

private fun navigateTo(id: String, onNavigate: (Destination) -> Unit, onNavigateRoute: (String) -> Unit) {
    if (id == NEW_FUEL_FILL_TILE_ID) {
        onNavigateRoute(FuelRoutes.ADD)
    } else {
        Destination.entries.firstOrNull { it.name == id }?.let(onNavigate)
    }
}

// GitHub issue #53's sync-status colors — the theme has no semantic
// success/warning/error tokens yet (only background/surface/accent/border),
// so these are plain literals rather than UrsTheme.colors entries.
private val SyncOkColor = Color(0xFF3F7A55)
private val SyncingColor = Color(0xFFC97A3D)
private val SyncErrorColor = Color(0xFFB3401F)

@Composable
private fun HomeHeader(
    username: String?,
    isCollapsed: Boolean,
    editMode: Boolean,
    syncPhase: SyncPhase,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onDoneClick: () -> Unit,
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
                Box {
                    Image(
                        painter = painterResource(R.drawable.urs_bear_logo),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                    // Sync status dot (GitHub issue #53) — always visible
                    // regardless of scroll/collapse state, unlike the hero
                    // moon tint below. contentDescription carries the state
                    // for accessibility since color alone wouldn't.
                    val (dotColor, dotDescription) = when (syncPhase) {
                        SyncPhase.IDLE_OK -> SyncOkColor to stringResource(R.string.sync_status_ok)
                        SyncPhase.SYNCING -> SyncingColor to stringResource(R.string.sync_status_syncing)
                        SyncPhase.IDLE_ERROR -> SyncErrorColor to stringResource(R.string.sync_status_error)
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .size(9.dp)
                            .border(1.dp, colors.background, CircleShape)
                            .background(dotColor, CircleShape)
                            .semantics { contentDescription = dotDescription },
                    )
                }
                UrsText(stringResource(R.string.app_name), style = UrsTheme.typography.brand, color = colors.accent)
            }
            if (editMode) {
                UrsText(
                    text = stringResource(R.string.home_edit_done),
                    style = UrsTheme.typography.cardTitle,
                    color = colors.accent,
                    modifier = Modifier.clickable(onClick = onDoneClick).padding(Spacing.s),
                )
            } else {
                UrsIconButton(
                    onClick = onSettingsClick,
                    contentDescription = stringResource(R.string.nav_settings),
                    imageVector = Icons.Filled.Settings,
                    tint = colors.accent,
                )
            }
        }

        AnimatedVisibility(
            visible = !isCollapsed && !editMode,
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
                // Same moon, tinted per sync phase (GitHub issue #53) — a
                // baked variant per state rather than a runtime color
                // filter, since the moon is partly behind the mountain
                // silhouette in the artwork and a naive tint would bleed
                // onto the bear/trees too.
                val heroDrawable = when (syncPhase) {
                    SyncPhase.IDLE_OK -> R.drawable.home_hero
                    SyncPhase.SYNCING -> R.drawable.home_hero_syncing
                    SyncPhase.IDLE_ERROR -> R.drawable.home_hero_error
                }
                Image(
                    painter = painterResource(heroDrawable),
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
private fun quickStat(id: String, state: HomeUiState): String? = when (id) {
    NEW_FUEL_FILL_TILE_ID -> state.fuelAvgConsumptionL100Km?.let { stringResource(R.string.fuel_avg_consumption_6mo, it) }
    Destination.BEER.name -> state.daysSinceLastBeer?.let { stringResource(R.string.home_days_since_beer, it) }
    else -> null
}

/**
 * Absolute-placement grid: [androidx.compose.foundation.lazy.grid.LazyVerticalGrid]
 * can't express explicit `(column, row)` with holes, so this measures every
 * tile to its exact pixel size from [HomeTilePlacement] and places it
 * directly via a plain [Layout]. Not lazy — the tile count (~13) is small
 * enough that this doesn't need virtualization. Column width is computed
 * once via [BoxWithConstraints] (composition-time, so it's available both
 * to the [Layout]'s measure pass and to each tile's own drag-threshold
 * math — the two must agree or a drag would miscompute its target cell).
 */
@Composable
private fun HomeTileGrid(
    layout: List<HomeTilePlacement>,
    editMode: Boolean,
    selectedTileId: String?,
    uiState: HomeUiState,
    favoriteLists: List<ListEntity>,
    favoriteInventories: List<InventoryEntity>,
    location: Location?,
    onTileClick: (String) -> Unit,
    onLongPress: (String) -> Unit,
    onMoveTile: (id: String, targetId: String) -> Unit,
    onResizeTile: (id: String, width: Int, height: Int) -> Unit,
    onRemoveTile: (String) -> Unit,
    onOpenFavoriteRoute: (String) -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val spacingPx = with(density) { Spacing.m.roundToPx() }
        val tileHeightPx = with(density) { TileHeight.roundToPx() }
        val totalWidthPx = with(density) { maxWidth.roundToPx() }
        val colWidthPx = (totalWidthPx - spacingPx * (HOME_GRID_COLUMNS - 1)) / HOME_GRID_COLUMNS

        Layout(
            content = {
                layout.forEach { placement ->
                    key(placement.destinationId) {
                        HomeTileItem(
                            placement = placement,
                            layout = layout,
                            selected = editMode && placement.destinationId == selectedTileId,
                            editMode = editMode,
                            uiState = uiState,
                            favoriteLists = favoriteLists,
                            favoriteInventories = favoriteInventories,
                            location = location,
                            colWidthPx = colWidthPx,
                            tileHeightPx = tileHeightPx,
                            spacingPx = spacingPx,
                            onClick = { onTileClick(placement.destinationId) },
                            onLongPress = { onLongPress(placement.destinationId) },
                            onMoveTile = onMoveTile,
                            onResizeTile = onResizeTile,
                            onRemoveTile = onRemoveTile,
                            onOpenFavoriteRoute = onOpenFavoriteRoute,
                        )
                    }
                }
            },
        ) { measurables, constraints ->
            fun widthPx(w: Int) = colWidthPx * w + spacingPx * (w - 1)
            fun heightPx(h: Int) = tileHeightPx * h + spacingPx * (h - 1)
            fun xPx(col: Int) = col * (colWidthPx + spacingPx)
            fun yPx(row: Int) = row * (tileHeightPx + spacingPx)

            val placeables = measurables.mapIndexed { index, measurable ->
                val p = layout[index]
                measurable.measure(Constraints.fixed(widthPx(p.width).coerceAtLeast(0), heightPx(p.height).coerceAtLeast(0)))
            }
            val maxRow = layout.maxOfOrNull { it.endRowExclusive } ?: 0
            val totalHeight = if (layout.isEmpty()) 0 else yPx(maxRow) - spacingPx

            layout(constraints.maxWidth, totalHeight.coerceAtLeast(0)) {
                placeables.forEachIndexed { index, placeable ->
                    val p = layout[index]
                    placeable.place(xPx(p.column), yPx(p.row))
                }
            }
        }
    }
}

@Composable
private fun HomeTileItem(
    placement: HomeTilePlacement,
    layout: List<HomeTilePlacement>,
    selected: Boolean,
    editMode: Boolean,
    uiState: HomeUiState,
    favoriteLists: List<ListEntity>,
    favoriteInventories: List<InventoryEntity>,
    location: Location?,
    colWidthPx: Int,
    tileHeightPx: Int,
    spacingPx: Int,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onMoveTile: (id: String, targetId: String) -> Unit,
    onResizeTile: (id: String, width: Int, height: Int) -> Unit,
    onRemoveTile: (String) -> Unit,
    onOpenFavoriteRoute: (String) -> Unit,
) {
    // Raw finger movement since the drag started — never adjusted or
    // rebased. dragStartCol/Row is captured once, at the start of the
    // gesture. The on-screen translation is then always computed fresh as
    // "where the drag started, plus how far the finger has moved, minus
    // wherever the tile's real (committed) position currently is" — a pure
    // function of always-current state, so it self-corrects the instant a
    // swap commits and this tile's placement moves, with no need to predict
    // or manually re-adjust anything at commit time.
    var rawDelta by remember { mutableStateOf(Offset.Zero) }
    var dragStartCol by remember { mutableStateOf(placement.column) }
    var dragStartRow by remember { mutableStateOf(placement.row) }
    // True only between onDragStart and onDragEnd/onDragCancel. The
    // translation math below is a drag-follow offset — meaningless once the
    // finger is up, when the tile just sits at its real (re-packed)
    // placement. Without this gate, a drag that committed a reorder leaves
    // dragStartCol/Row pointing at the pre-drag cell while rawDelta is back
    // to zero, so graphicsLayer renders the tile shifted by the exact
    // inverse of the move — visually snapped back onto its old cell (and,
    // being drawn at a higher zIndex, hiding whatever tile now sits there)
    // until some later, unrelated recomposition clears it.
    var isDragging by remember { mutableStateOf(false) }
    // The tile currently under the finger but not yet swapped with — the
    // swap (and the reflow it causes) waits for HoverCommitDelayMs of
    // hovering over the *same* tile so passing over several tiles on the
    // way somewhere doesn't reorder the list for each one.
    var pendingTargetId by remember { mutableStateOf<String?>(null) }
    var pendingJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val currentEditMode = rememberUpdatedState(editMode)
    val currentSelected = rememberUpdatedState(selected)
    // placement.column/row must never be read directly inside the gesture
    // callbacks below: pointerInput(placement.destinationId) launches its
    // coroutine once and never restarts it (the id never changes), so any
    // plain parameter read inside stays frozen at whatever it was back
    // then — exactly what caused tiles to jump using stale positions after
    // an unrelated swap had already moved them. rememberUpdatedState keeps
    // these reading the latest value regardless.
    val currentPlacement = rememberUpdatedState(placement)
    val currentLayout = rememberUpdatedState(layout)

    // A tile can become selected without ever going through onDragStart —
    // a plain tap-to-select in edit mode selects it directly, with no
    // gesture involved at all. Without this, dragStartCol/Row would still
    // hold whatever value they had at this composable's very first
    // rendering, however outdated an unrelated swap since then has made
    // it, and the graphicsLayer block below would render the tile
    // translated toward that stale spot the instant it's selected.
    LaunchedEffect(selected) {
        if (selected) {
            dragStartCol = currentPlacement.value.column
            dragStartRow = currentPlacement.value.row
            rawDelta = Offset.Zero
        }
    }

    val colors = UrsTheme.colors

    fun targetAt(col: Int, row: Int): HomeTilePlacement? = currentLayout.value.firstOrNull { candidate ->
        candidate.destinationId != currentPlacement.value.destinationId &&
            col >= candidate.column && col < candidate.endColumnExclusive &&
            row >= candidate.row && row < candidate.endRowExclusive
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                if (selected && isDragging) {
                    val stepX = (colWidthPx + spacingPx).toFloat()
                    val stepY = (tileHeightPx + spacingPx).toFloat()
                    translationX = (dragStartCol - currentPlacement.value.column) * stepX + rawDelta.x
                    translationY = (dragStartRow - currentPlacement.value.row) * stepY + rawDelta.y
                } else {
                    translationX = 0f
                    translationY = 0f
                }
            }
            .zIndex(if (selected) 1f else 0f)
            // Not keyed on editMode/selected: those flip as a *result* of
            // onLongPress mid-gesture, and restarting this coroutine right
            // then would cancel the drag that's supposed to continue
            // seamlessly into a move. rememberUpdatedState keeps onDrag
            // reading the latest values without needing the key to change.
            .pointerInput(placement.destinationId) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragStartCol = currentPlacement.value.column
                        dragStartRow = currentPlacement.value.row
                        rawDelta = Offset.Zero
                        isDragging = true
                        onLongPress()
                    },
                    onDragEnd = {
                        pendingJob?.cancel()
                        pendingJob = null
                        pendingTargetId?.let { onMoveTile(currentPlacement.value.destinationId, it) }
                        pendingTargetId = null
                        rawDelta = Offset.Zero
                        isDragging = false
                    },
                    onDragCancel = {
                        pendingJob?.cancel()
                        pendingJob = null
                        pendingTargetId = null
                        rawDelta = Offset.Zero
                        isDragging = false
                    },
                    onDrag = { change, delta ->
                        change.consume()
                        if (!(currentEditMode.value && currentSelected.value)) return@detectDragGesturesAfterLongPress
                        rawDelta += delta
                        val stepX = colWidthPx + spacingPx
                        val stepY = tileHeightPx + spacingPx
                        if (stepX <= 0 || stepY <= 0) return@detectDragGesturesAfterLongPress
                        val newCol = (dragStartCol + (rawDelta.x / stepX).roundToInt())
                            .coerceIn(0, HOME_GRID_COLUMNS - currentPlacement.value.width)
                        val newRow = (dragStartRow + (rawDelta.y / stepY).roundToInt()).coerceAtLeast(0)
                        val target = targetAt(newCol, newRow)
                        if (target == null) {
                            if (pendingTargetId != null) {
                                pendingJob?.cancel()
                                pendingJob = null
                                pendingTargetId = null
                            }
                        } else if (target.destinationId != pendingTargetId) {
                            pendingJob?.cancel()
                            pendingTargetId = target.destinationId
                            pendingJob = scope.launch {
                                delay(HoverCommitDelayMs)
                                onMoveTile(currentPlacement.value.destinationId, target.destinationId)
                                pendingTargetId = null
                            }
                        }
                    },
                )
            }
            // Separate detector for a plain tap (navigate, or toggle
            // selection in edit mode) — the drag detector above never
            // consumes anything before its long-press timeout fires, so a
            // quick tap reaches this one untouched.
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
    ) {
        val borderModifier = if (selected) {
            Modifier.border(SelectionBorderWidth, colors.accent, RoundedCornerShape(Radius.card))
        } else {
            Modifier
        }
        Box(modifier = Modifier.fillMaxSize().then(borderModifier).padding(if (selected) 2.dp else 0.dp)) {
            HomeTileBody(
                id = placement.destinationId,
                width = placement.width,
                height = placement.height,
                uiState = uiState,
                location = location,
                favoriteLists = if (editMode) emptyList() else favoriteLists,
                favoriteInventories = if (editMode) emptyList() else favoriteInventories,
                editMode = editMode,
                onClick = onClick,
                onOpenFavoriteRoute = onOpenFavoriteRoute,
            )
        }

        if (selected) {
            UrsIconButton(
                onClick = { onRemoveTile(placement.destinationId) },
                contentDescription = stringResource(R.string.home_edit_remove_tile),
                imageVector = Icons.Filled.Close,
                size = RemoveIconSize,
                iconSize = 16.dp,
                tint = colors.onAccent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 6.dp)
                    .clip(CircleShape)
                    .background(colors.accent),
            )
            listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd).forEach { corner ->
                ResizeHandle(
                    corner = corner,
                    colWidthPx = colWidthPx,
                    tileHeightPx = tileHeightPx,
                    startWidth = placement.width,
                    startHeight = placement.height,
                    onResize = { w, h -> onResizeTile(placement.destinationId, w, h) },
                )
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ResizeHandle(
    corner: Alignment,
    colWidthPx: Int,
    tileHeightPx: Int,
    startWidth: Int,
    startHeight: Int,
    onResize: (width: Int, height: Int) -> Unit,
) {
    var resizeOffset by remember { mutableStateOf(Offset.Zero) }
    var resizeOrigin by remember { mutableStateOf(startWidth to startHeight) }
    val colors = UrsTheme.colors

    Box(
        modifier = Modifier
            .align(corner)
            .size(ResizeHandleSize)
            .offset(
                x = if (corner == Alignment.TopStart || corner == Alignment.BottomStart) (-8).dp else 8.dp,
                y = if (corner == Alignment.TopStart || corner == Alignment.TopEnd) (-8).dp else 8.dp,
            )
            .clip(CircleShape)
            .background(colors.accent)
            .pointerInput(colWidthPx, tileHeightPx) {
                detectDragGestures(
                    onDragStart = { resizeOffset = Offset.Zero; resizeOrigin = startWidth to startHeight },
                    onDragEnd = { resizeOffset = Offset.Zero },
                    onDragCancel = { resizeOffset = Offset.Zero },
                    onDrag = { change, delta ->
                        change.consume()
                        resizeOffset += delta
                        val (origW, origH) = resizeOrigin
                        val widthThreshold = colWidthPx / 2f
                        val heightThreshold = tileHeightPx / 2f
                        val newW = when {
                            resizeOffset.x > widthThreshold -> 2
                            resizeOffset.x < -widthThreshold -> 1
                            else -> origW
                        }
                        val newH = when {
                            resizeOffset.y > heightThreshold -> 2
                            resizeOffset.y < -heightThreshold -> 1
                            else -> origH
                        }
                        if (newW != origW || newH != origH) {
                            onResize(newW, newH)
                            resizeOrigin = newW to newH
                            resizeOffset = Offset.Zero
                        }
                    },
                )
            },
    )
}

@Composable
private fun AddTilePickerContent(tiles: List<Destination>, onPick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        UrsText(stringResource(R.string.home_edit_picker_title), style = UrsTheme.typography.cardTitle, color = UrsTheme.colors.accent)
        if (tiles.isEmpty()) {
            UrsText(
                stringResource(R.string.home_edit_picker_empty),
                style = UrsTheme.typography.body,
                color = UrsTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(vertical = Spacing.m),
            )
        } else {
            LazyColumn(modifier = Modifier.height((tiles.size.coerceAtMost(6) * 56).dp)) {
                items(tiles) { destination ->
                    UrsText(
                        text = stringResource(destination.labelRes),
                        style = UrsTheme.typography.body,
                        color = UrsTheme.colors.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(destination.name) }
                            .padding(vertical = Spacing.m),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditModeExtraRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .clickable(onClick = onClick)
            .padding(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        UrsIconButton(onClick = onClick, contentDescription = label, imageVector = icon, tint = UrsTheme.colors.accent)
        UrsText(label, style = UrsTheme.typography.body, color = UrsTheme.colors.accent)
    }
}

/**
 * The one generic tile body every destination (plus the New-Fuel-Fill
 * shortcut and Life Map) now renders through. 1-tall keeps the previous
 * `CornerBleedTile` look; height 2 scales the bleed image up and makes room
 * for a second stat line (issue #12's own open question — the cheapest
 * treatment that still reads as "bigger", matching the recommendation
 * already on file in NIGHTRUN-REPORT.md). Life Map keeps its live map
 * preview regardless of shape, sized to whatever placement it currently has.
 */
@Composable
private fun HomeTileBody(
    id: String,
    width: Int,
    height: Int,
    uiState: HomeUiState,
    location: Location?,
    favoriteLists: List<ListEntity>,
    favoriteInventories: List<InventoryEntity>,
    editMode: Boolean,
    onClick: () -> Unit,
    onOpenFavoriteRoute: (String) -> Unit,
) {
    if (id == Destination.LIFE_MAP.name) {
        LifeMapTileBody(location = location, editMode = editMode, onClick = onClick)
        return
    }

    val colors = UrsTheme.colors
    val destination = Destination.entries.firstOrNull { it.name == id }
    val title = if (id == NEW_FUEL_FILL_TILE_ID) {
        stringResource(R.string.home_new_fuel_fill)
    } else {
        destination?.let { stringResource(it.labelRes) } ?: id
    }
    val subtitle = quickStat(id, uiState)
    val available = destination?.isAvailable ?: true
    val image = TILE_IMAGE[id] ?: R.drawable.tile_k
    val imageSize = if (height >= 2) TileBleedImageSizeTall else TileBleedImageSize

    UrsGlassCard(
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.fillMaxSize().alpha(if (available) 1f else colors.disabledAlpha),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = 10.dp)
                    .size(imageSize),
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
                UrsText(text = title, style = UrsTheme.typography.cardTitle, color = colors.accent)
                if (subtitle != null) {
                    UrsText(text = subtitle, style = UrsTheme.typography.statAccent, color = colors.accent)
                }
                if (!available) {
                    UrsPill(
                        text = stringResource(R.string.coming_soon).uppercase(),
                        containerColor = colors.surface,
                        contentColor = colors.onSurfaceMuted,
                        style = UrsTheme.typography.tag,
                    )
                }
            }

            val favorites = when (id) {
                Destination.SHOPPING_LIST.name -> favoriteLists.map {
                    FavoriteBadgeItem(firstGlyph(it.name), IconCatalog.drawableFor(it.iconId), ShoppingListRoutes.listDetail(it.publicId))
                }
                Destination.INVENTORY.name -> favoriteInventories.map {
                    FavoriteBadgeItem(firstGlyph(it.name), IconCatalog.drawableFor(it.iconId), InventoryRoutes.products(it.publicId, it.name))
                }
                else -> emptyList()
            }
            if (favorites.isNotEmpty()) {
                FavoriteBadges(
                    items = favorites,
                    wide = width == 2 || height == 2,
                    onOpen = onOpenFavoriteRoute,
                    modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.m),
                )
            }
        }
    }
}

private data class FavoriteBadgeItem(
    val glyph: String,
    val iconRes: Int?,
    val route: String,
)

/** First character of a name, codepoint-aware so a multi-byte emoji isn't split. */
private fun firstGlyph(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    return String(Character.toChars(trimmed.codePointAt(0)))
}

@Composable
private fun FavoriteBadges(items: List<FavoriteBadgeItem>, wide: Boolean, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val shown = items.take(if (wide) MaxFavoriteBadgesWide else MaxFavoriteBadgesCompact)
    val colors = UrsTheme.colors
    val badges: @Composable () -> Unit = {
        shown.forEach { item ->
            Box(
                modifier = Modifier
                    .size(FavoriteBadgeSize)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .border(1.dp, colors.accent, CircleShape)
                    .clickable { onOpen(item.route) },
                contentAlignment = Alignment.Center,
            ) {
                if (item.iconRes != null) {
                    UrsIcon(
                        painter = painterResource(item.iconRes),
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    UrsText(text = item.glyph, style = UrsTheme.typography.caption, color = colors.accent)
                }
            }
        }
    }
    if (wide) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) { badges() }
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) { badges() }
    }
}

/**
 * Life Map's tile: a real, non-interactive preview of the last known
 * location (osmdroid — the same library `LifeMapScreen` already uses).
 * Falls back to a plain illustration only until a location is available
 * (no permission yet, or the very first app launch before a fix has
 * landed) — never a fake/placeholder coordinate.
 */
@Composable
private fun LifeMapTileBody(location: Location?, editMode: Boolean, onClick: () -> Unit) {
    val colors = UrsTheme.colors

    UrsGlassCard(contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxSize()) {
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

            // The embedded osmdroid MapView swallows single-finger touches
            // over the map area before HomeTileItem's outer tap detector can
            // register a completed tap, so without this only the title text
            // was tap-navigable. A transparent catcher over the whole tile
            // forwards a plain tap to the same open-Life-Map action. Omitted
            // in edit mode so the tile's long-press move/resize gestures
            // aren't intercepted.
            if (!editMode) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
                )
            }
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
