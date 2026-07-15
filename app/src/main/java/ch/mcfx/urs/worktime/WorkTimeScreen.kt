package ch.mcfx.urs.worktime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.computeTotals
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.local.WorkTimeEntryWithBreaks
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import java.util.Locale

// Same reasoning as FuelScreen's own local text-style/color constants — the
// design system's type scale doesn't have a "big FAB glyph" size and no
// "error" role in its palette yet (see Color.kt).
private val FabIconStyle = TextStyle(fontSize = 28.sp)
private val FormErrorColor = Color(0xFFD64545)

@Composable
fun WorkTimeScreen(
    onAddEntry: () -> Unit,
    viewModel: WorkTimeViewModel = viewModel(factory = WorkTimeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            WorkTimeUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))
            is WorkTimeUiState.Data -> EntryList(state)
        }

        if (uiState is WorkTimeUiState.Data) {
            UrsFab(
                onClick = onAddEntry,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
            ) {
                UrsText(text = "+", style = FabIconStyle, color = UrsTheme.colors.onAccent)
            }
        }
    }
}

@Composable
private fun EntryList(state: WorkTimeUiState.Data) {
    if (state.entries.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            UrsText(
                stringResource(R.string.worktime_empty),
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
        items(state.entries, key = { it.entry.id }) { entryWithBreaks ->
            EntryCard(entryWithBreaks, state.userDefaultTargetHours)
        }
    }
}

@Composable
private fun EntryCard(entryWithBreaks: WorkTimeEntryWithBreaks, userDefaultTargetHours: String) {
    val totals = entryWithBreaks.computeTotals(userDefaultTargetHours)

    UrsCard(radius = Radius.row, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsText(entryWithBreaks.entry.date, style = UrsTheme.typography.cardTitle)
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SyncStatusPill(entryWithBreaks.entry.syncStatus)
                totals.dailyTotalHours?.let {
                    UrsText(
                        stringResource(R.string.worktime_daily_total, formatHours(it)),
                        style = UrsTheme.typography.cardTitle,
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            UrsText(
                "${entryWithBreaks.entry.workStart.take(5)} – ${entryWithBreaks.entry.workEnd.take(5)}",
                style = UrsTheme.typography.body,
                color = UrsTheme.colors.onSurfaceMuted,
            )
            totals.overUndertimeHours?.let {
                UrsText(
                    formatSignedHours(it),
                    style = UrsTheme.typography.body,
                    color = if (it < 0) FormErrorColor else UrsTheme.colors.onSurfaceMuted,
                )
            }
        }
        if (entryWithBreaks.breaks.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xs))
            UrsText(
                stringResource(
                    R.string.worktime_break_count,
                    entryWithBreaks.breaks.size,
                ),
                style = UrsTheme.typography.caption,
                color = UrsTheme.colors.onSurfaceMuted,
            )
        }
    }
}

@Composable
private fun SyncStatusPill(status: SyncStatus) {
    when (status) {
        SyncStatus.PENDING -> UrsPill(text = stringResource(R.string.worktime_status_pending))
        SyncStatus.FAILED -> UrsPill(
            text = stringResource(R.string.worktime_status_failed),
            containerColor = FormErrorColor.copy(alpha = 0.15f),
            contentColor = FormErrorColor,
        )
        SyncStatus.SYNCED -> Unit
    }
}

private fun formatHours(hours: Float): String = String.format(Locale.US, "%.2f", hours)

private fun formatSignedHours(hours: Float): String =
    if (hours >= 0) "+${formatHours(hours)}" else formatHours(hours)
