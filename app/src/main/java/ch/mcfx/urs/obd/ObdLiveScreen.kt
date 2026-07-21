package ch.mcfx.urs.obd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

// No "error" role in the design system's palette yet (see Color.kt) - same
// local-constant pattern already used in VpnSettingsScreen/ProductListScreen.
private val ErrorColor = Color(0xFFD64545)

private val ValueTextStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold)

@Composable
fun ObdLiveScreen(onOpenSetup: () -> Unit, viewModel: ObdViewModel = viewModel(factory = ObdViewModel.Factory)) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val reading by viewModel.reading.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsText(stringResource(R.string.obd_live_title), style = UrsTheme.typography.screenTitle)
            UrsIconButton(
                onClick = onOpenSetup,
                contentDescription = stringResource(R.string.obd_setup_title),
                imageVector = Icons.Filled.Settings,
            )
        }

        StatusPill(connectionState)

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                ValueTile(
                    label = stringResource(R.string.obd_rpm),
                    value = reading.rpm?.toString() ?: "—",
                    modifier = Modifier.weight(1f),
                )
                ValueTile(
                    label = stringResource(R.string.obd_speed),
                    value = reading.speedKmh?.let { "$it${stringResource(R.string.obd_unit_kmh)}" } ?: "—",
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                ValueTile(
                    label = stringResource(R.string.obd_coolant),
                    value = reading.coolantTempC?.let { "$it${stringResource(R.string.obd_unit_celsius)}" } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                ValueTile(
                    label = stringResource(R.string.obd_fuel_level),
                    value = reading.fuelLevelPercent?.let { "$it${stringResource(R.string.obd_unit_percent)}" } ?: "—",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatusPill(state: ObdConnectionState) {
    val (containerColor, contentColor) = if (state == ObdConnectionState.ERROR) {
        ErrorColor.copy(alpha = 0.15f) to ErrorColor
    } else {
        UrsTheme.colors.accent.copy(alpha = 0.15f) to UrsTheme.colors.accent
    }
    UrsPill(text = stringResource(statusLabel(state)), containerColor = containerColor, contentColor = contentColor)
}

// Not private - reused by ObdSetupScreen's connection row.
fun statusLabel(state: ObdConnectionState): Int = when (state) {
    ObdConnectionState.DISCONNECTED -> R.string.obd_status_disconnected
    ObdConnectionState.CONNECTING -> R.string.obd_status_connecting
    ObdConnectionState.INITIALIZING -> R.string.obd_status_initializing
    ObdConnectionState.CONNECTED -> R.string.obd_status_connected
    ObdConnectionState.RECONNECTING -> R.string.obd_status_reconnecting
    ObdConnectionState.ERROR -> R.string.obd_status_error
}

@Composable
private fun ValueTile(label: String, value: String, modifier: Modifier = Modifier) {
    UrsCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            UrsText(label, style = UrsTheme.typography.caption, color = UrsTheme.colors.onSurfaceMuted)
            UrsText(value, style = ValueTextStyle, color = UrsTheme.colors.accent)
        }
    }
}
