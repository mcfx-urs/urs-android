package ch.mcfx.urs.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

@Composable
fun DefaultVehicleSettingsScreen(
    viewModel: DefaultVehicleSettingsViewModel = viewModel(factory = DefaultVehicleSettingsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = UrsTheme.colors

    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        UrsText(stringResource(R.string.default_vehicle_settings_title), style = UrsTheme.typography.screenTitle)
        UrsText(
            stringResource(R.string.default_vehicle_settings_description),
            style = UrsTheme.typography.caption,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(bottom = Spacing.s),
        )

        if (uiState.vehicles.isEmpty()) {
            UrsText(
                stringResource(R.string.default_vehicle_settings_empty),
                style = UrsTheme.typography.body,
                color = colors.onSurfaceMuted,
            )
        } else {
            val selected = uiState.vehicles.firstOrNull { it.id == uiState.selectedVehicleId }
            UrsDropdownField(
                label = stringResource(R.string.default_vehicle_settings_label),
                options = uiState.vehicles,
                selectedLabel = selected?.let { "${it.brand} ${it.model}" },
                optionLabel = { "${it.brand} ${it.model}" },
                onSelect = { viewModel.selectVehicle(it.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
