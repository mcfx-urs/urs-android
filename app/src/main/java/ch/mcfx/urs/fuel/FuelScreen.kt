package ch.mcfx.urs.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import java.util.Locale

// Same reasoning as FuelHubScreen's/HomeScreen's own local text-style
// constants — the design system's type scale doesn't have a "big FAB glyph"
// size, so this is a one-off rather than a new shared token.
private val FabIconStyle = TextStyle(fontSize = 28.sp)

@Composable
fun FuelScreen(
    onAddFillUp: () -> Unit,
    viewModel: FuelViewModel = viewModel(factory = FuelViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            FuelUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))

            is FuelUiState.Error -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UrsText(stringResource(R.string.error_load), style = UrsTheme.typography.body)
                Spacer(Modifier.height(Spacing.l))
                UrsButton(text = stringResource(R.string.retry), onClick = viewModel::load)
            }

            is FuelUiState.Data -> FillList(state)
        }

        if (uiState is FuelUiState.Data) {
            UrsFab(
                onClick = onAddFillUp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
            ) {
                UrsText(text = "+", style = FabIconStyle, color = UrsTheme.colors.onAccent)
            }
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
            UrsText(
                stringResource(R.string.fills_empty),
                modifier = Modifier.align(Alignment.Center),
                color = UrsTheme.colors.onSurfaceMuted,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(state.fills, key = { it.id }) { fill ->
            UrsCard(radius = Radius.row, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    UrsText(carNames[fill.carId] ?: fill.carId, style = UrsTheme.typography.cardTitle)
                    UrsText(totalCost(fill.pricePerLiter, fill.liters), style = UrsTheme.typography.cardTitle)
                }
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    UrsText(
                        "${fill.date.substringBefore(' ')} · ${stationNames[fill.stationId] ?: fill.stationId}",
                        style = UrsTheme.typography.body,
                        color = UrsTheme.colors.onSurfaceMuted,
                    )
                    UrsText(
                        stringResource(R.string.fill_amount_at_price, fill.liters, fill.pricePerLiter),
                        style = UrsTheme.typography.body,
                        color = UrsTheme.colors.onSurfaceMuted,
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                UrsText(
                    stringResource(R.string.fill_odometer_km, fill.odometer),
                    style = UrsTheme.typography.caption,
                    color = UrsTheme.colors.onSurfaceMuted,
                )
            }
        }
    }
}

internal fun totalCost(price: String, liters: String): String {
    val p = price.toFloatOrNull()
    val l = liters.toFloatOrNull()
    return if (p != null && l != null) String.format(Locale.US, "%.2f", p * l) else "–"
}
