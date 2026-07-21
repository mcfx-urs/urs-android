package ch.mcfx.urs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.tokens.Spacing

private val SETTINGS_TILES = listOf(
    SettingsTile(SettingsRoutes.ABOUT, R.string.settings_tile_about, Icons.Filled.Info),
    SettingsTile(SettingsRoutes.ACCOUNT, R.string.settings_tile_account, Icons.Filled.AccountCircle),
    SettingsTile(SettingsRoutes.GENERAL, R.string.settings_tile_general, Icons.Filled.Tune),
)

@Composable
fun SettingsScreen(onNavigate: (route: String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        SETTINGS_TILES.forEach { tile ->
            SettingsRow(tile = tile, onClick = { onNavigate(tile.route) })
        }
    }
}
