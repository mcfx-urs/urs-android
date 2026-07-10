package ch.mcfx.urs.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.R

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
)

@Composable
fun SettingsScreen(onNavigate: (route: String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(SETTINGS_TILES) { tile ->
            SettingsTileCard(tile = tile, onClick = { tile.route?.let(onNavigate) })
        }
    }
}

@Composable
private fun SettingsTileCard(tile: SettingsTile, onClick: () -> Unit) {
    val containerColor = if (tile.isAvailable) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (tile.isAvailable) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(enabled = tile.isAvailable, onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(tile.icon, contentDescription = null, tint = contentColor)
        Text(
            text = stringResource(tile.labelRes),
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
        )
        if (!tile.isAvailable) {
            Text(
                text = stringResource(R.string.coming_soon),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}
