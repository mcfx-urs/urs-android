package ch.mcfx.urs.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.VehicleType
import ch.mcfx.urs.data.remote.FuelDto
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsDateField
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

private val FormErrorColor = Color(0xFFD64545)

private val VehicleTypeOptions = VehicleType.entries.toList()

private fun vehicleTypeLabelRes(type: VehicleType) = when (type) {
    VehicleType.CAR -> R.string.vehicle_type_car
    VehicleType.MOTORBIKE -> R.string.vehicle_type_motorbike
    VehicleType.EBIKE -> R.string.vehicle_type_ebike
}

@Composable
fun VehicleAddScreen(
    onDone: () -> Unit,
    /** Null creates a new vehicle; set edits that existing row (see [VehicleViewModel.openFormForEdit]). */
    vehicleId: String? = null,
    viewModel: VehicleViewModel = viewModel(factory = VehicleViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { if (vehicleId != null) viewModel.openFormForEdit(vehicleId) else viewModel.openForm() }

    var hasOpened by remember { mutableStateOf(false) }
    LaunchedEffect(showForm) {
        if (showForm) {
            hasOpened = true
        } else if (hasOpened) {
            onDone()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            VehicleUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))
            is VehicleUiState.Data -> if (showForm) {
                VehicleForm(form = formState, fuelTypes = state.fuelTypes, viewModel = viewModel)
            } else if (vehicleId != null) {
                UrsProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun VehicleForm(form: VehicleFormState, fuelTypes: List<FuelDto>, viewModel: VehicleViewModel) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl)
            .padding(top = Spacing.xl)
            // Edge-to-edge means this screen draws behind the system nav
            // bar unless told otherwise — the Save button would otherwise
            // sit partly underneath/obscured by it, same class of bug
            // HomeScreen/UrsFab/FuelStationMapScreen already work around.
            .padding(bottom = Spacing.xl + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsTextField(
            value = form.brand,
            onValueChange = viewModel::setBrand,
            label = stringResource(R.string.vehicle_brand),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = form.model,
            onValueChange = viewModel::setModel,
            label = stringResource(R.string.vehicle_model),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = form.year,
            onValueChange = viewModel::setYear,
            label = stringResource(R.string.vehicle_year),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsDropdownField(
            label = stringResource(R.string.vehicle_fuel_type),
            options = fuelTypes,
            selectedLabel = fuelTypes.firstOrNull { it.id == form.fuelId }?.name,
            optionLabel = { it.name },
            onSelect = { viewModel.setFuelId(it.id) },
            modifier = Modifier.fillMaxWidth(),
        )
        val vehicleTypeLabels = VehicleTypeOptions.associateWith { stringResource(vehicleTypeLabelRes(it)) }
        UrsDropdownField(
            label = stringResource(R.string.vehicle_type),
            options = VehicleTypeOptions,
            selectedLabel = vehicleTypeLabels[form.vehicleType],
            optionLabel = { vehicleTypeLabels.getValue(it) },
            onSelect = viewModel::setVehicleType,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = form.engineCode,
            onValueChange = viewModel::setEngineCode,
            label = stringResource(R.string.vehicle_engine_code),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        UrsText(
            stringResource(R.string.vehicle_registration_document_section),
            style = UrsTheme.typography.cardTitle,
        )
        UrsTextField(
            value = form.color,
            onValueChange = viewModel::setColor,
            label = stringResource(R.string.vehicle_color),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = form.vin,
            onValueChange = viewModel::setVin,
            label = stringResource(R.string.vehicle_vin),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = form.registrationNumber,
            onValueChange = viewModel::setRegistrationNumber,
            label = stringResource(R.string.vehicle_registration_number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = form.typeApprovalNumber,
            onValueChange = viewModel::setTypeApprovalNumber,
            label = stringResource(R.string.vehicle_type_approval_number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsTextField(
                value = form.displacementCcm,
                onValueChange = viewModel::setDisplacementCcm,
                label = stringResource(R.string.vehicle_displacement_ccm),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            UrsTextField(
                value = form.weightKg,
                onValueChange = viewModel::setWeightKg,
                label = stringResource(R.string.vehicle_weight_kg),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsTextField(
                value = form.powerKw,
                onValueChange = viewModel::setPowerKw,
                label = stringResource(R.string.vehicle_power_kw),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            UrsTextField(
                value = form.powerPs,
                onValueChange = viewModel::setPowerPs,
                label = stringResource(R.string.vehicle_power_ps),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        UrsDateField(
            value = form.firstRegistrationDate,
            onValueChange = viewModel::setFirstRegistrationDate,
            label = stringResource(R.string.vehicle_first_registration_date),
            modifier = Modifier.fillMaxWidth(),
        )
        UrsDateField(
            value = form.lastMfkDate,
            onValueChange = viewModel::setLastMfkDate,
            label = stringResource(R.string.vehicle_last_mfk_date),
            modifier = Modifier.fillMaxWidth(),
        )

        if (form.submitFailure != VehicleFailure.NONE) {
            UrsText(
                text = when (form.submitFailure) {
                    VehicleFailure.CONNECTIVITY -> stringResource(R.string.login_error_connectivity)
                    else -> stringResource(R.string.error_save)
                },
                color = FormErrorColor,
                style = UrsTheme.typography.body,
            )
        }

        UrsButton(
            text = stringResource(if (form.submitting) R.string.saving else R.string.save),
            onClick = viewModel::submit,
            enabled = form.isValid && !form.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
