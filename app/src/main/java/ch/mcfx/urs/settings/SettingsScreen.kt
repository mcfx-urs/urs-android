package ch.mcfx.urs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
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

// 's approve/reject queue for AI-generated catalog images — same
// super-user gating as ADMIN_TILE, kept as its own tile rather than nested
// inside AdminScreen since that screen has no navigation callback today.
private val IMAGE_REVIEW_TILE =
    SettingsTile(SettingsRoutes.IMAGE_REVIEW, R.string.settings_tile_image_review, Icons.Filled.CheckCircle)

// isSuperUser gates the Admin/Image Review tiles — regular users never see
// them at all, rather than seeing them disabled.
@Composable
fun SettingsScreen(isSuperUser: Boolean, onNavigate: (route: String) -> Unit) {
    val tiles = if (isSuperUser) SETTINGS_TILES + ADMIN_TILE + IMAGE_REVIEW_TILE else SETTINGS_TILES
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        tiles.forEach { tile ->
            SettingsRow(tile = tile, onClick = { onNavigate(tile.route) })
        }
    }
}
