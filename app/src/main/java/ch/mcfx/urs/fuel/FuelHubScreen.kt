package ch.mcfx.urs.fuel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.local.VehicleEntity
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsFilterChip
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.ursScreenContentPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import kotlinx.coroutines.launch

// Same emoji-icon size Android needs to visually match the mockup's
// browser-rendered icons (see HomeScreen.kt's TileIconStyle for the same
// note in more detail).
private val TileIconStyle = TextStyle(fontSize = 32.sp)

private enum class FuelTile(val route: String, val labelRes: Int, val emoji: String) {
    FILLS(FuelRoutes.FILLS, R.string.fuel_tile_fills, "📋"),
    ADD(FuelRoutes.ADD, R.string.fuel_tile_add, "➕"),
    STATIONS(FuelRoutes.STATIONS, R.string.fuel_tile_stations, "⛽"),
    STATS(FuelRoutes.STATS, R.string.fuel_tile_stats, "📊"),
    PRICE(FuelRoutes.PRICE, R.string.fuel_tile_price, "💰"),
}

@Composable
fun FuelHubScreen(
    onNavigate: (route: String) -> Unit,
    viewModel: FuelHubViewModel = viewModel(factory = FuelHubViewModel.Factory),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var syncing by remember { mutableStateOf(false) }
    val hubState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        if (hubState.vehicles.isNotEmpty()) {
            VehicleQuickSwitchRow(
                vehicles = hubState.vehicles,
                selectedVehicleId = hubState.defaultVehicleId,
                onSelect = viewModel::setDefaultVehicle,
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = ursScreenContentPadding(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            items(FuelTile.entries) { tile ->
                HubTile(
                    emoji = tile.emoji,
                    label = stringResource(tile.labelRes),
                    onClick = { onNavigate(tile.route) },
                )
            }
            item {
                HubTile(
                    emoji = "🔄",
                    label = stringResource(if (syncing) R.string.fuel_tile_syncing else R.string.fuel_tile_sync_now),
                    onClick = {
                        if (syncing) return@HubTile
                        // Bypasses WorkManager entirely — immediate,
                        // user-initiated, no backoff/constraints needed (those
                        // exist for the unattended periodic/connectivity-
                        // triggered paths, see SyncWorker). Push then pull,
                        // same order as AboutViewModel.syncNow().
                        val app = context.applicationContext as UrsApplication
                        coroutineScope.launch {
                            syncing = true
                            app.container.syncManager.syncNow()
                            app.container.pullCoordinator.pullAll()
                            syncing = false
                        }
                    },
                )
            }
        }
    }
}

// Shown regardless of vehicle count (consistent with the Statistics filter),
// as long as there's at least one vehicle — tapping a chip sets that vehicle
// as the user's default immediately.
@Composable
private fun VehicleQuickSwitchRow(
    vehicles: List<VehicleEntity>,
    selectedVehicleId: String?,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.l, bottom = Spacing.s),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        UrsText(
            text = stringResource(R.string.fuel_default_vehicle_label),
            style = UrsTheme.typography.caption,
            color = UrsTheme.colors.onSurfaceMuted,
            modifier = Modifier.padding(horizontal = Spacing.l),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            vehicles.forEach { vehicle ->
                UrsFilterChip(
                    label = "${vehicle.brand} ${vehicle.model}",
                    selected = vehicle.id == selectedVehicleId,
                    onClick = { onSelect(vehicle.id) },
                )
            }
        }
    }
}

@Composable
private fun HubTile(emoji: String, label: String, onClick: () -> Unit) {
    UrsCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            UrsText(text = emoji, style = TileIconStyle)
            UrsText(text = label, style = UrsTheme.typography.cardTitle, color = UrsTheme.colors.accent)
        }
    }
}
