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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsDivider
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import java.util.Locale

private const val FIVE_DL_ML = 500
private const val THIRTY_THREE_CL_ML = 330

@Composable
fun BeerScreen(viewModel: BeerViewModel = viewModel(factory = BeerViewModel.Factory)) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        BeerUiState.Loading -> Box(Modifier.fillMaxSize()) {
            UrsProgressIndicator(Modifier.align(Alignment.Center))
        }

        is BeerUiState.Error -> Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UrsText(stringResource(R.string.error_load), style = UrsTheme.typography.body)
                Spacer(Modifier.height(Spacing.l))
                UrsButton(text = stringResource(R.string.retry), onClick = viewModel::load)
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
        contentPadding = PaddingValues(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        item { LogButtonsRow(onLog = viewModel::logBeer) }
        item { FunFactCard(litersThisYear = litersThisYear, bathtubs = bathtubs) }
        item { UrsText(stringResource(R.string.beer_chart_daily_title), style = UrsTheme.typography.cardTitle) }
        item { BarChart(daily) }
        item { UrsText(stringResource(R.string.beer_chart_monthly_title), style = UrsTheme.typography.cardTitle) }
        item { BarChart(monthly) }
        item { UrsText(stringResource(R.string.beer_history_title), style = UrsTheme.typography.cardTitle) }
        if (entries.isEmpty()) {
            item {
                UrsText(
                    stringResource(R.string.beer_history_empty),
                    color = UrsTheme.colors.onSurfaceMuted,
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
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        UrsButton(
            text = stringResource(R.string.beer_add_5dl),
            onClick = { onLog(FIVE_DL_ML) },
            modifier = Modifier.weight(1f),
        )
        UrsButton(
            text = stringResource(R.string.beer_add_33cl),
            onClick = { onLog(THIRTY_THREE_CL_ML) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FunFactCard(litersThisYear: Double, bathtubs: Double) {
    UrsCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            UrsText(stringResource(R.string.beer_liters_this_year), color = UrsTheme.colors.onSurfaceMuted)
            UrsText(
                String.format(Locale.US, "%.1f L", litersThisYear),
                style = UrsTheme.typography.cardTitle,
            )
        }
        Spacer(Modifier.height(Spacing.s))
        UrsText(
            stringResource(R.string.beer_fun_fact, String.format(Locale.US, "%.1f", bathtubs)),
            style = UrsTheme.typography.body,
            color = UrsTheme.colors.onSurfaceMuted,
        )
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
            UrsText(maxCount.toString(), style = UrsTheme.typography.caption)
            Spacer(Modifier.weight(1f))
            UrsText((maxCount / 2).toString(), style = UrsTheme.typography.caption)
            Spacer(Modifier.weight(1f))
            UrsText("0", style = UrsTheme.typography.caption)
        }
        Spacer(Modifier.width(Spacing.s))
        Box {
            Column(Modifier.height(CHART_BAR_MAX_HEIGHT).fillMaxWidth()) {
                UrsDivider()
                Spacer(Modifier.weight(1f))
                UrsDivider()
                Spacer(Modifier.weight(1f))
                UrsDivider()
            }
            // reverseLayout + newest-first order: the initial scroll position
            // then shows the most recent bars with today flush to the right
            // edge, instead of the oldest ones — the recent pattern is
            // what's actually useful at a glance, older history is the part
            // worth scrolling (leftward) for, same convention as a chat view
            // resting on its latest message.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), reverseLayout = true) {
                items(buckets.asReversed()) { bucket ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(Spacing.xl)) {
                        Box(
                            modifier = Modifier.height(CHART_BAR_MAX_HEIGHT).fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(CHART_BAR_MAX_HEIGHT * (bucket.count.toFloat() / maxCount))
                                    .background(UrsTheme.colors.accent, RoundedCornerShape(2.dp)),
                            )
                        }
                        UrsText(bucket.label, style = UrsTheme.typography.caption)
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: BeerLogDto, onDelete: (String) -> Unit) {
    UrsCard(radius = Radius.row, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                UrsText(formatAmount(entry.amountMl), style = UrsTheme.typography.cardTitle)
                UrsText(
                    entry.date,
                    style = UrsTheme.typography.caption,
                    color = UrsTheme.colors.onSurfaceMuted,
                )
            }
            UrsIconButton(
                onClick = { onDelete(entry.id) },
                contentDescription = stringResource(R.string.beer_entry_remove),
                imageVector = Icons.Filled.Close,
            )
        }
    }
}

private fun formatAmount(amountMl: String): String = when (amountMl.toIntOrNull()) {
    FIVE_DL_ML -> "5dl"
    THIRTY_THREE_CL_ML -> "33cl"
    else -> "$amountMl ml"
}
