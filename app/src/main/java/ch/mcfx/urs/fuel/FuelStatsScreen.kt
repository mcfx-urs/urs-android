package ch.mcfx.urs.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.remote.CarDto
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuelStatsScreen(viewModel: FuelStatsViewModel = viewModel(factory = FuelStatsViewModel.Factory)) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.loading -> Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        uiState.error -> Box(Modifier.fillMaxSize()) {
            Text(stringResource(R.string.error_load), modifier = Modifier.align(Alignment.Center))
        }

        uiState.fillCount == 0 && uiState.cars.isEmpty() -> Box(Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.stats_empty),
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                CarFilterRow(
                    cars = uiState.cars,
                    selectedCarId = uiState.selectedCarId,
                    onSelect = viewModel::selectCar,
                )
            }
            item { StatsSummary(uiState) }
            item {
                Text(stringResource(R.string.stats_monthly_title), style = MaterialTheme.typography.titleMedium)
            }
            items(uiState.monthly, key = { it.yearMonth }) { month -> MonthlyRow(month) }
        }
    }
}

@Composable
private fun CarFilterRow(cars: List<CarDto>, selectedCarId: String?, onSelect: (String?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selectedCarId == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.stats_all_cars)) },
            )
        }
        items(cars, key = { it.id }) { car ->
            FilterChip(
                selected = selectedCarId == car.id,
                onClick = { onSelect(car.id) },
                label = { Text("${car.brand} ${car.model}") },
            )
        }
    }
}

@Composable
private fun StatsSummary(state: FuelStatsUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatRow(
                stringResource(R.string.stats_avg_consumption),
                state.avgConsumption?.let { stringResource(R.string.stats_l100km, it) } ?: "–",
            )
            StatRow(
                stringResource(R.string.stats_avg_price),
                state.avgPricePerLiter?.let { String.format(Locale.US, "%.3f", it) } ?: "–",
            )
            StatRow(stringResource(R.string.stats_total_cost), String.format(Locale.US, "%.2f", state.totalCost))
            StatRow(
                stringResource(R.string.stats_total_km),
                stringResource(R.string.fill_odometer_km, formatKm(state.totalKm)),
            )
            StatRow(stringResource(R.string.stats_fill_count), state.fillCount.toString())
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun MonthlyRow(month: MonthlyFuelStat) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(month.yearMonth, style = MaterialTheme.typography.titleMedium)
                Text(String.format(Locale.US, "%.2f", month.totalCost), style = MaterialTheme.typography.titleMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.fill_odometer_km, formatKm(month.totalKm)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    month.avgConsumption?.let { stringResource(R.string.stats_l100km, it) } ?: "–",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatKm(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
