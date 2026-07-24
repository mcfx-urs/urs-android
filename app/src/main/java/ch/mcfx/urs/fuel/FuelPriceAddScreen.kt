package ch.mcfx.urs.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.remote.FuelDto
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsDateField
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

// Same "no error color token yet" workaround FuelAddScreen already uses.
private val FormErrorColor = Color(0xFFD64545)

@Composable
fun FuelPriceAddScreen(
    onDone: () -> Unit,
    viewModel: FuelPriceViewModel = viewModel(factory = FuelPriceViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()

    LaunchedEffect(saved) { if (saved) onDone() }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            FuelPriceUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))
            is FuelPriceUiState.Data -> PriceForm(
                form = formState,
                pickerStations = state.pickerStations,
                fuelTypes = state.fuelTypes,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun PriceForm(
    form: FuelPriceFormState,
    pickerStations: List<StationPickerOption>,
    fuelTypes: List<FuelDto>,
    viewModel: FuelPriceViewModel,
) {
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
        UrsDropdownField(
            label = stringResource(R.string.fill_station),
            options = pickerStations,
            selectedLabel = form.station?.name,
            optionLabel = { it.station.name },
            onSelect = { viewModel.selectStation(it.station) },
            modifier = Modifier.fillMaxWidth(),
        )

        UrsDropdownField(
            label = stringResource(R.string.fuel_price_fuel_type),
            options = fuelTypes,
            selectedLabel = form.fuelType?.name,
            optionLabel = { it.name },
            onSelect = viewModel::selectFuelType,
            modifier = Modifier.fillMaxWidth(),
        )

        UrsTextField(
            value = form.price,
            onValueChange = viewModel::setPrice,
            label = stringResource(R.string.fill_price_per_liter),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        UrsDateField(
            value = form.date,
            onValueChange = viewModel::setDate,
            label = stringResource(R.string.fill_date),
            modifier = Modifier.fillMaxWidth(),
        )

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
