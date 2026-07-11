package ch.mcfx.urs.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.remote.CarDto
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsFilterChip
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import java.util.Locale

@Composable
fun FuelStatsScreen(viewModel: FuelStatsViewModel = viewModel(factory = FuelStatsViewModel.Factory)) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        uiState.loading -> Box(Modifier.fillMaxSize()) {
            UrsProgressIndicator(Modifier.align(Alignment.Center))
        }

        uiState.error -> Box(Modifier.fillMaxSize()) {
            UrsText(stringResource(R.string.error_load), modifier = Modifier.align(Alignment.Center))
        }

        uiState.fillCount == 0 && uiState.cars.isEmpty() -> Box(Modifier.fillMaxSize()) {
            UrsText(
                stringResource(R.string.stats_empty),
                modifier = Modifier.align(Alignment.Center),
                color = UrsTheme.colors.onSurfaceMuted,
            )
        }

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
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
                UrsText(stringResource(R.string.stats_monthly_title), style = UrsTheme.typography.screenTitle)
            }
            items(uiState.monthly, key = { it.yearMonth }) { month -> MonthlyRow(month) }
        }
    }
}

@Composable
private fun CarFilterRow(cars: List<CarDto>, selectedCarId: String?, onSelect: (String?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        item {
            UrsFilterChip(
                label = stringResource(R.string.stats_all_cars),
                selected = selectedCarId == null,
                onClick = { onSelect(null) },
            )
        }
        items(cars, key = { it.id }) { car ->
            UrsFilterChip(
                label = "${car.brand} ${car.model}",
                selected = selectedCarId == car.id,
                onClick = { onSelect(car.id) },
            )
        }
    }
}

@Composable
private fun StatsSummary(state: FuelStatsUiState) {
    UrsCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
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
        UrsText(label, color = UrsTheme.colors.onSurfaceMuted)
        UrsText(value, style = UrsTheme.typography.cardTitle)
    }
}

@Composable
private fun MonthlyRow(month: MonthlyFuelStat) {
    UrsCard(radius = Radius.row, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                UrsText(month.yearMonth, style = UrsTheme.typography.cardTitle)
                UrsText(String.format(Locale.US, "%.2f", month.totalCost), style = UrsTheme.typography.cardTitle)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                UrsText(
                    stringResource(R.string.fill_odometer_km, formatKm(month.totalKm)),
                    style = UrsTheme.typography.body,
                    color = UrsTheme.colors.onSurfaceMuted,
                )
                UrsText(
                    month.avgConsumption?.let { stringResource(R.string.stats_l100km, it) } ?: "–",
                    style = UrsTheme.typography.body,
                    color = UrsTheme.colors.onSurfaceMuted,
                )
            }
        }
    }
}

private fun formatKm(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
