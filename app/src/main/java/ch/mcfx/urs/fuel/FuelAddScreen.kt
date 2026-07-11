package ch.mcfx.urs.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import ch.mcfx.urs.data.remote.CarDto
import ch.mcfx.urs.data.remote.FillingStationDto
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

// No "error" role in the design system's palette yet (see Color.kt) — mirrors
// the same local-constant pattern already used in FuelStationsScreen /
// ProductListScreen, rather than waiting on the broader token set.
private val FormErrorColor = Color(0xFFD64545)

// A dedicated full screen rather than the list's old bottom sheet, per
//  — reachable both from the Fuel hub's "Add Fill-up" tile and from
// the Fill-ups list's FAB. Reuses FuelViewModel's existing form/submit
// logic unchanged; only the presentation (screen vs. sheet) differs.
@Composable
fun FuelAddScreen(
    onDone: () -> Unit,
    viewModel: FuelViewModel = viewModel(factory = FuelViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.openForm() }

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
            FuelUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))

            is FuelUiState.Error -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UrsText(stringResource(R.string.error_load), style = UrsTheme.typography.body)
            }

            is FuelUiState.Data -> if (showForm) {
                FillForm(form = formState, cars = state.cars, stations = state.stations, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun FillForm(
    form: FillFormState,
    cars: List<CarDto>,
    stations: List<FillingStationDto>,
    viewModel: FuelViewModel,
) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).padding(vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsDropdownField(
            label = stringResource(R.string.fill_car),
            options = cars,
            selectedLabel = form.car?.let { "${it.brand} ${it.model}" },
            optionLabel = { "${it.brand} ${it.model}" },
            onSelect = viewModel::selectCar,
            modifier = Modifier.fillMaxWidth(),
        )

        UrsDropdownField(
            label = stringResource(R.string.fill_station),
            options = stations,
            selectedLabel = form.station?.name,
            optionLabel = { it.name },
            onSelect = viewModel::selectStation,
            modifier = Modifier.fillMaxWidth(),
        )

        UrsTextField(
            value = form.odometer,
            onValueChange = viewModel::setOdometer,
            label = stringResource(R.string.fill_odometer),
            supportingText = form.lastOdometer?.let { stringResource(R.string.fill_last_odometer, it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsTextField(
                value = form.pricePerLiter,
                onValueChange = viewModel::setPricePerLiter,
                label = stringResource(R.string.fill_price_per_liter),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            UrsTextField(
                value = form.liters,
                onValueChange = viewModel::setLiters,
                label = stringResource(R.string.fill_liters),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        UrsTextField(
            value = form.date,
            onValueChange = viewModel::setDate,
            label = stringResource(R.string.fill_date),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        form.totalCost?.let {
            UrsText(
                stringResource(R.string.fill_total, it),
                style = UrsTheme.typography.cardTitle,
            )
        }

        if (form.submitFailed) {
            UrsText(
                stringResource(R.string.error_save),
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
