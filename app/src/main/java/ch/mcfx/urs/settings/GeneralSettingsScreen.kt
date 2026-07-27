package ch.mcfx.urs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Watch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.tokens.Spacing

private val GENERAL_TILES = listOf(
    SettingsTile(SettingsRoutes.VPN, R.string.settings_tile_vpn, Icons.Filled.VpnKey),
    SettingsTile(SettingsRoutes.NOTIFICATIONS, R.string.settings_tile_notifications, Icons.Filled.Notifications),
    SettingsTile(SettingsRoutes.LOCATION_HISTORY, R.string.settings_tile_location_history, Icons.Filled.LocationOn),
    SettingsTile(SettingsRoutes.PRODUCT_MANAGEMENT, R.string.settings_tile_product_management, Icons.Filled.Inventory),
    SettingsTile(SettingsRoutes.WATCH_RELAY, R.string.settings_tile_watch_relay, Icons.Filled.Watch),
    SettingsTile(SettingsRoutes.THEME, R.string.settings_tile_theme, Icons.Filled.Palette),
)

@Composable
fun GeneralSettingsScreen(onNavigate: (route: String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        GENERAL_TILES.forEach { tile ->
            SettingsRow(tile = tile, onClick = { onNavigate(tile.route) })
        }
    }
}
