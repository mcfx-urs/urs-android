package ch.mcfx.urs.worktime

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.computeTotals
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.local.WorkTimeEntryWithBreaks
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsOutlinedButton
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
    onEditEntry: (Long) -> Unit,
    viewModel: WorkTimeViewModel = viewModel(factory = WorkTimeViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionSheetEntry by viewModel.actionSheetEntry.collectAsStateWithLifecycle()
    val pendingDeleteEntry by viewModel.pendingDeleteEntry.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            WorkTimeUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))
            is WorkTimeUiState.Data -> EntryList(state, onLongPress = viewModel::openActionSheet)
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

    // Long-press → Edit/Delete: reuses the same UrsBottomSheet mechanic as
    // the delete-confirmation sheet below it, just with different content.
    actionSheetEntry?.let { entry ->
        UrsBottomSheet(onDismissRequest = viewModel::closeActionSheet) {
            EntryActionSheet(
                onEdit = {
                    viewModel.closeActionSheet()
                    onEditEntry(entry.entry.id)
                },
                onDelete = viewModel::requestDelete,
            )
        }
    }

    if (pendingDeleteEntry != null) {
        UrsBottomSheet(onDismissRequest = viewModel::cancelDelete) {
            DeleteConfirmSheet(onConfirm = viewModel::confirmDelete, onCancel = viewModel::cancelDelete)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryList(state: WorkTimeUiState.Data, onLongPress: (WorkTimeEntryWithBreaks) -> Unit) {
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
            EntryCard(entryWithBreaks, state.userDefaultTargetHours, onLongPress = { onLongPress(entryWithBreaks) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryCard(entryWithBreaks: WorkTimeEntryWithBreaks, userDefaultTargetHours: String, onLongPress: () -> Unit) {
    val totals = entryWithBreaks.computeTotals(userDefaultTargetHours)

    UrsCard(
        radius = Radius.row,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongPress),
    ) {
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

@Composable
private fun EntryActionSheet(onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l)) {
        ActionSheetRow(label = stringResource(R.string.worktime_edit), icon = Icons.Filled.Edit, onClick = onEdit)
        ActionSheetRow(
            label = stringResource(R.string.worktime_delete),
            icon = Icons.Filled.Delete,
            onClick = onDelete,
            tint = FormErrorColor,
        )
    }
}

@Composable
private fun ActionSheetRow(label: String, icon: ImageVector, onClick: () -> Unit, tint: Color = UrsTheme.colors.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrsIcon(imageVector = icon, contentDescription = null, tint = tint)
        UrsText(label, style = UrsTheme.typography.cardTitle, color = tint)
    }
}

@Composable
private fun DeleteConfirmSheet(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.worktime_delete_confirm_title), style = UrsTheme.typography.cardTitle)
        UrsText(
            stringResource(R.string.worktime_delete_confirm_body),
            style = UrsTheme.typography.body,
            color = UrsTheme.colors.onSurfaceMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsOutlinedButton(text = stringResource(R.string.cancel), onClick = onCancel, modifier = Modifier.weight(1f))
            UrsButton(text = stringResource(R.string.worktime_delete), onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

private fun formatHours(hours: Float): String = String.format(Locale.US, "%.2f", hours)

private fun formatSignedHours(hours: Float): String =
    if (hours >= 0) "+${formatHours(hours)}" else formatHours(hours)
