package ch.mcfx.urs.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.VehicleEntity
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.ursScreenContentPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Read-only counterpart to [VehicleAddScreen]'s edit form, reached by a
 * single tap on a vehicle list entry (long-press still opens the existing
 * Edit/Delete action sheet, unchanged). Resolves [vehicleId] against
 * [VehicleViewModel]'s already-loaded [VehicleUiState.Data] instead of a
 * parallel fetch path.
 */
@Composable
fun VehicleViewScreen(
    vehicleId: String,
    onEdit: () -> Unit,
    viewModel: VehicleViewModel = viewModel(factory = VehicleViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val vehicle = (uiState as? VehicleUiState.Data)?.vehicles?.firstOrNull { it.id == vehicleId }

    Box(modifier = Modifier.fillMaxSize()) {
        if (vehicle != null) {
            VehicleDetails(vehicle)
        } else {
            UrsProgressIndicator(Modifier.align(Alignment.Center))
        }

        UrsFab(
            onClick = onEdit,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
        ) {
            UrsIcon(
                imageVector = Icons.Filled.Edit,
                contentDescription = stringResource(R.string.vehicle_edit),
                tint = UrsTheme.colors.onAccent,
            )
        }
    }
}

@Composable
private fun VehicleDetails(vehicle: VehicleEntity) {
    val rows = buildList {
        add(stringResource(R.string.vehicle_brand) to vehicle.brand)
        add(stringResource(R.string.vehicle_model) to vehicle.model)
        add(stringResource(R.string.vehicle_year) to vehicle.year)
        add(stringResource(R.string.vehicle_fuel_type) to vehicle.fuelName)
        add(stringResource(R.string.vehicle_type) to stringResource(vehicleTypeLabelRes(vehicle.vehicleType)))
        addIfPresent(stringResource(R.string.vehicle_engine_code), vehicle.engineCode)
        addIfPresent(stringResource(R.string.vehicle_color), vehicle.color)
        addIfPresent(stringResource(R.string.vehicle_vin), vehicle.vin)
        addIfPresent(stringResource(R.string.vehicle_registration_number), vehicle.registrationNumber)
        addIfPresent(stringResource(R.string.vehicle_type_approval_number), vehicle.typeApprovalNumber)
        addIfPresent(stringResource(R.string.vehicle_displacement_ccm), vehicle.displacementCcm)
        addIfPresent(stringResource(R.string.vehicle_power_kw), vehicle.powerKw)
        addIfPresent(stringResource(R.string.vehicle_power_ps), vehicle.powerPs)
        addIfPresent(stringResource(R.string.vehicle_weight_kg), vehicle.weightKg)
        addIfPresent(stringResource(R.string.vehicle_first_registration_date), vehicle.firstRegistrationDate)
        addIfPresent(stringResource(R.string.vehicle_last_mfk_date), vehicle.lastMfkDate)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ursScreenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        items(rows) { (label, value) -> FieldRow(label = label, value = value) }
    }
}

private fun MutableList<Pair<String, String>>.addIfPresent(label: String, value: String?) {
    if (!value.isNullOrBlank()) add(label to value)
}

@Composable
private fun FieldRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        UrsText(label, style = UrsTheme.typography.caption, color = UrsTheme.colors.onSurfaceMuted)
        UrsText(value, style = UrsTheme.typography.body)
    }
}
