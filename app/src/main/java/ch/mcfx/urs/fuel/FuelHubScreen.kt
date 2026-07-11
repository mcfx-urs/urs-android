package ch.mcfx.urs.fuel

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
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

// Same emoji-icon size Android needs to visually match the mockup's
// browser-rendered icons (see HomeScreen.kt's TileIconStyle for the same
// note in more detail).
private val TileIconStyle = TextStyle(fontSize = 32.sp)

private enum class FuelTile(val route: String, val labelRes: Int, val emoji: String) {
    FILLS(FuelRoutes.FILLS, R.string.fuel_tile_fills, "📋"),
    ADD(FuelRoutes.ADD, R.string.fuel_tile_add, "➕"),
    STATIONS(FuelRoutes.STATIONS, R.string.fuel_tile_stations, "⛽"),
    STATS(FuelRoutes.STATS, R.string.fuel_tile_stats, "📊"),
}

@Composable
fun FuelHubScreen(onNavigate: (route: String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(Spacing.l),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(FuelTile.entries) { tile ->
            HubTile(tile = tile, onClick = { onNavigate(tile.route) })
        }
    }
}

@Composable
private fun HubTile(tile: FuelTile, onClick: () -> Unit) {
    UrsCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            UrsText(text = tile.emoji, style = TileIconStyle)
            UrsText(text = stringResource(tile.labelRes), style = UrsTheme.typography.cardTitle)
        }
    }
}
