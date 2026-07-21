package ch.mcfx.urs.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

// Shared list-style row for Settings and its sub-hubs (General) — the
// established Begleiter direction for settings-style screens, unlike
// Home/Fuel's card grid.
internal data class SettingsTile(val route: String, val labelRes: Int, val icon: ImageVector)

@Composable
internal fun SettingsRow(tile: SettingsTile, onClick: () -> Unit) {
    val colors = UrsTheme.colors

    UrsCard(
        radius = Radius.row,
        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = 14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsIcon(imageVector = tile.icon, contentDescription = null, tint = colors.accent)
            UrsText(
                text = stringResource(tile.labelRes),
                style = UrsTheme.typography.cardTitle,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            UrsText(text = "→", style = UrsTheme.typography.statAccent, color = colors.accent)
        }
    }
}
