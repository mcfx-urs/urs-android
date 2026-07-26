package ch.mcfx.urs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
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

private val ADMIN_TILE =
    SettingsTile(SettingsRoutes.ADMIN, R.string.settings_tile_admin, Icons.Filled.AdminPanelSettings)

// isSuperUser gates the Admin tile — regular users never see it at all,
// rather than seeing it disabled. Image Review used to be a tile here too
// ('s approve/reject queue for AI-generated catalog images) — moved
// into Product Management (General → Product Management) instead, reachable
// via its own icon button there, since it's catalog-image-review work, not
// a general Settings destination.
@Composable
fun SettingsScreen(isSuperUser: Boolean, onNavigate: (route: String) -> Unit) {
    val tiles = if (isSuperUser) SETTINGS_TILES + ADMIN_TILE else SETTINGS_TILES
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        tiles.forEach { tile ->
            SettingsRow(tile = tile, onClick = { onNavigate(tile.route) })
        }
    }
}
