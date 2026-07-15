package ch.mcfx.urs.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

private data class SettingsTile(
    val route: String?,
    val labelRes: Int,
    val icon: ImageVector,
    val isAvailable: Boolean,
)

private val SETTINGS_TILES = listOf(
    SettingsTile(null, R.string.settings_tile_users, Icons.Filled.People, isAvailable = false),
    SettingsTile(SettingsRoutes.VPN, R.string.settings_tile_vpn, Icons.Filled.VpnKey, isAvailable = true),
    SettingsTile(SettingsRoutes.NOTIFICATIONS, R.string.settings_tile_notifications, Icons.Filled.Notifications, isAvailable = true),
    SettingsTile(SettingsRoutes.WORK_TIME, R.string.settings_tile_work_time, Icons.Filled.Schedule, isAvailable = true),
)

// List-style rows, not a tile grid — the established Begleiter direction
// for settings-style screens, unlike Home/Fuel's card grid.
@Composable
fun SettingsScreen(onNavigate: (route: String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        SETTINGS_TILES.forEach { tile ->
            SettingsRow(tile = tile, onClick = { tile.route?.let(onNavigate) })
        }
    }
}

@Composable
private fun SettingsRow(tile: SettingsTile, onClick: () -> Unit) {
    val colors = UrsTheme.colors
    val alpha = if (tile.isAvailable) 1f else colors.disabledAlpha

    UrsCard(
        radius = Radius.row,
        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = 14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = tile.isAvailable, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsIcon(
                imageVector = tile.icon,
                contentDescription = null,
                tint = (if (tile.isAvailable) colors.accent else colors.onSurfaceMuted).copy(alpha = alpha),
            )
            UrsText(
                text = stringResource(tile.labelRes),
                style = UrsTheme.typography.cardTitle,
                color = colors.onSurface.copy(alpha = alpha),
                modifier = Modifier.weight(1f),
            )
            if (tile.isAvailable) {
                UrsText(text = "→", style = UrsTheme.typography.statAccent, color = colors.accent)
            } else {
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
