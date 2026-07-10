package ch.mcfx.urs.beer

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.remote.BeerLogDto
import java.util.Locale

private const val FIVE_DL_ML = 500
private const val THIRTY_THREE_CL_ML = 330

@Composable
fun BeerScreen(viewModel: BeerViewModel = viewModel(factory = BeerViewModel.Factory)) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        BeerUiState.Loading -> Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        is BeerUiState.Error -> Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.error_load), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                Button(onClick = viewModel::load) { Text(stringResource(R.string.retry)) }
            }
        }

        is BeerUiState.Data -> BeerContent(entries = state.entries, viewModel = viewModel)
    }
}

@Composable
private fun BeerContent(entries: List<BeerLogDto>, viewModel: BeerViewModel) {
    val daily = remember(entries) { BeerStats.dailyCounts(entries) }
    val monthly = remember(entries) { BeerStats.monthlyCounts(entries) }
    val litersThisYear = remember(entries) { BeerStats.totalLitersThisYear(entries) }
    val bathtubs = remember(litersThisYear) { BeerStats.bathtubs(litersThisYear) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { LogButtonsRow(onLog = viewModel::logBeer) }
        item { FunFactCard(litersThisYear = litersThisYear, bathtubs = bathtubs) }
        item { Text(stringResource(R.string.beer_chart_daily_title), style = MaterialTheme.typography.titleMedium) }
        item { BarChart(daily) }
        item { Text(stringResource(R.string.beer_chart_monthly_title), style = MaterialTheme.typography.titleMedium) }
        item { BarChart(monthly) }
        item { Text(stringResource(R.string.beer_history_title), style = MaterialTheme.typography.titleMedium) }
        if (entries.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.beer_history_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(entries, key = { it.id }) { entry ->
                EntryRow(entry = entry, onDelete = viewModel::deleteEntry)
            }
        }
    }
}

@Composable
private fun LogButtonsRow(onLog: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { onLog(FIVE_DL_ML) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.beer_add_5dl))
        }
        Button(onClick = { onLog(THIRTY_THREE_CL_ML) }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.beer_add_33cl))
        }
    }
}

@Composable
private fun FunFactCard(litersThisYear: Double, bathtubs: Double) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.beer_liters_this_year), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    String.format(Locale.US, "%.1f L", litersThisYear),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                stringResource(R.string.beer_fun_fact, String.format(Locale.US, "%.1f", bathtubs)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val CHART_BAR_MAX_HEIGHT = 80.dp
private val CHART_AXIS_WIDTH = 28.dp

@Composable
private fun BarChart(buckets: List<BeerStats.Bucket>) {
    val maxCount = (buckets.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)

    Row(modifier = Modifier.fillMaxWidth()) {
        // Auto-scaling axis (0 / half / max) instead of a number on every
        // bar — a number per bar got cluttered and didn't read as a scale;
        // three reference marks plus the gridlines below give the same
        // "how much is this bar" answer without repeating it 30 times.
        Column(
            modifier = Modifier.height(CHART_BAR_MAX_HEIGHT).width(CHART_AXIS_WIDTH),
            horizontalAlignment = Alignment.End,
        ) {
            Text(maxCount.toString(), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text((maxCount / 2).toString(), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text("0", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(8.dp))
        Box {
            Column(Modifier.height(CHART_BAR_MAX_HEIGHT).fillMaxWidth()) {
                HorizontalDivider()
                Spacer(Modifier.weight(1f))
                HorizontalDivider()
                Spacer(Modifier.weight(1f))
                HorizontalDivider()
            }
            // reverseLayout + newest-first order: the initial scroll position
            // then shows the most recent bars with today flush to the right
            // edge, instead of the oldest ones — the recent pattern is
            // what's actually useful at a glance, older history is the part
            // worth scrolling (leftward) for, same convention as a chat view
            // resting on its latest message.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), reverseLayout = true) {
                items(buckets.asReversed()) { bucket ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
                        Box(
                            modifier = Modifier.height(CHART_BAR_MAX_HEIGHT).fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(CHART_BAR_MAX_HEIGHT * (bucket.count.toFloat() / maxCount))
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                            )
                        }
                        Text(bucket.label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: BeerLogDto, onDelete: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(formatAmount(entry.amountMl), style = MaterialTheme.typography.titleMedium)
                Text(
                    entry.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onDelete(entry.id) }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.beer_entry_remove))
            }
        }
    }
}

private fun formatAmount(amountMl: String): String = when (amountMl.toIntOrNull()) {
    FIVE_DL_ML -> "5dl"
    THIRTY_THREE_CL_ML -> "33cl"
    else -> "$amountMl ml"
}
