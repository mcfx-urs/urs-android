package ch.mcfx.urs.vehicle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import ch.mcfx.urs.R
import ch.mcfx.urs.fuel.FuelRoutes
import ch.mcfx.urs.obd.ObdRoutes
import ch.mcfx.urs.service.ServiceRoutes
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

// Same emoji-icon size as FuelHubScreen/SettingsScreen's tile grids.
private val TileIconStyle = TextStyle(fontSize = 32.sp)

private enum class VehicleTile(val route: String, val labelRes: Int, val emoji: String) {
    FUEL(FuelRoutes.HUB, R.string.vehicle_tile_fuel, "⛽"),
    OBD(ObdRoutes.LIVE, R.string.vehicle_tile_obd, "📟"),
    VEHICLES(VehicleRoutes.LIST, R.string.vehicle_tile_vehicles, "🚘"),
    SERVICE(ServiceRoutes.LIST, R.string.vehicle_tile_service, "🔧"),
}

@Composable
fun VehicleHubScreen(onNavigate: (route: String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(Spacing.l),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(VehicleTile.entries) { tile ->
            HubTile(
                emoji = tile.emoji,
                label = stringResource(tile.labelRes),
                onClick = { onNavigate(tile.route) },
            )
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
