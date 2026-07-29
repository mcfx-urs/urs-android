package ch.mcfx.urs.baking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.BakingRepository
import ch.mcfx.urs.data.bakingTemplatesByKey
import ch.mcfx.urs.data.local.BakePlanEntity
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.ursScreenContentPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val PlanDisplayFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · HH:mm")

@Composable
fun BakingHistoryScreen(viewModel: BakingHistoryViewModel = viewModel(factory = BakingHistoryViewModel.Factory)) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        BakingHistoryUiState.Loading -> Box(Modifier.fillMaxSize()) { UrsProgressIndicator(Modifier.align(Alignment.Center)) }
        is BakingHistoryUiState.Data -> HistoryList(state.plans)
    }
}

@Composable
private fun HistoryList(plans: List<BakePlanEntity>) {
    if (plans.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            UrsText(
                stringResource(R.string.baking_history_empty),
                modifier = Modifier.align(Alignment.Center),
                color = UrsTheme.colors.onSurfaceMuted,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ursScreenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(plans, key = { it.id }) { plan ->
            UrsCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UrsText(bakingTemplatesByKey[plan.templateKey]?.name ?: plan.templateKey, style = UrsTheme.typography.cardTitle)
                    StatusBadge(plan.status)
                }
                UrsText(
                    Instant.ofEpochMilli(plan.anchorAtMillis).atZone(ZoneId.systemDefault()).format(PlanDisplayFormat),
                    style = UrsTheme.typography.body,
                    color = UrsTheme.colors.onSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    when (status) {
        BakingRepository.STATUS_COMPLETED -> UrsPill(text = stringResource(R.string.baking_status_completed))
        BakingRepository.STATUS_CANCELLED -> UrsPill(
            text = stringResource(R.string.baking_status_cancelled),
            containerColor = UrsTheme.colors.onSurfaceMuted.copy(alpha = 0.15f),
            contentColor = UrsTheme.colors.onSurfaceMuted,
        )
    }
}
