package ch.mcfx.urs.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.remote.CarDto
import ch.mcfx.urs.data.remote.FillingStationDto
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelScreen(viewModel: FuelViewModel = viewModel(factory = FuelViewModel.Factory)) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            if (uiState is FuelUiState.Data) {
                FloatingActionButton(onClick = viewModel::openForm) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val state = uiState) {
                FuelUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is FuelUiState.Error -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.error_load), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = viewModel::load) { Text(stringResource(R.string.retry)) }
                }

                is FuelUiState.Data -> FillList(state)
            }
        }
    }

    if (showForm) {
        val data = uiState as? FuelUiState.Data ?: return
        ModalBottomSheet(onDismissRequest = viewModel::closeForm) {
            FillForm(
                form = formState,
                cars = data.cars,
                stations = data.stations,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun FillList(state: FuelUiState.Data) {
    val carNames = remember(state.cars) {
        state.cars.associate { it.id to "${it.brand} ${it.model}" }
    }
    val stationNames = remember(state.stations) {
        state.stations.associate { it.id to it.name }
    }

    if (state.fills.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.fills_empty),
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.fills, key = { it.id }) { fill ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            carNames[fill.carId] ?: fill.carId,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            totalCost(fill.pricePerLiter, fill.liters),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "${fill.date.substringBefore(' ')} · ${stationNames[fill.stationId] ?: fill.stationId}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.fill_amount_at_price, fill.liters, fill.pricePerLiter),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.fill_odometer_km, fill.odometer),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FillForm(
    form: FillFormState,
    cars: List<CarDto>,
    stations: List<FillingStationDto>,
    viewModel: FuelViewModel,
) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.fill_add), style = MaterialTheme.typography.headlineSmall)

        SelectField(
            label = stringResource(R.string.fill_car),
            options = cars,
            selectedLabel = form.car?.let { "${it.brand} ${it.model}" },
            optionLabel = { "${it.brand} ${it.model}" },
            onSelect = viewModel::selectCar,
        )

        SelectField(
            label = stringResource(R.string.fill_station),
            options = stations,
            selectedLabel = form.station?.name,
            optionLabel = { it.name },
            onSelect = viewModel::selectStation,
        )

        OutlinedTextField(
            value = form.odometer,
            onValueChange = viewModel::setOdometer,
            label = { Text(stringResource(R.string.fill_odometer)) },
            supportingText = form.lastOdometer?.let {
                { Text(stringResource(R.string.fill_last_odometer, it)) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = form.pricePerLiter,
                onValueChange = viewModel::setPricePerLiter,
                label = { Text(stringResource(R.string.fill_price_per_liter)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = form.liters,
                onValueChange = viewModel::setLiters,
                label = { Text(stringResource(R.string.fill_liters)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = form.date,
            onValueChange = viewModel::setDate,
            label = { Text(stringResource(R.string.fill_date)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        form.totalCost?.let {
            Text(
                stringResource(R.string.fill_total, it),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (form.submitFailed) {
            Text(
                stringResource(R.string.error_save),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = viewModel::submit,
            enabled = form.isValid && !form.submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(if (form.submitting) R.string.saving else R.string.save))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SelectField(
    label: String,
    options: List<T>,
    selectedLabel: String?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun totalCost(price: String, liters: String): String {
    val p = price.toFloatOrNull()
    val l = liters.toFloatOrNull()
    return if (p != null && l != null) String.format(Locale.US, "%.2f", p * l) else "–"
}
