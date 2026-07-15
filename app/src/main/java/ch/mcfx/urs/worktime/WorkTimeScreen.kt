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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.MonthlySummary
import ch.mcfx.urs.data.computeMonthlySummary
import ch.mcfx.urs.data.computeTotals
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.local.WorkTimeEntryWithBreaks
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle as JavaTimeTextStyle
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
    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()

    var overrideSheetOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            WorkTimeUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))
            is WorkTimeUiState.Data -> MonthContent(
                state = state,
                selectedYear = selectedYear,
                selectedMonth = selectedMonth,
                onSelectMonth = viewModel::selectMonth,
                onEditOverride = { overrideSheetOpen = true },
                onLongPress = viewModel::openActionSheet,
            )
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
    // the delete-confirmation and override sheets below it, just with
    // different content.
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

    if (overrideSheetOpen) {
        val currentOverride = (uiState as? WorkTimeUiState.Data)?.monthOverrides
            ?.find { it.year == selectedYear && it.month == selectedMonth }
        UrsBottomSheet(onDismissRequest = { overrideSheetOpen = false }) {
            MonthOverrideSheet(
                year = selectedYear,
                month = selectedMonth,
                initialValue = currentOverride?.targetHours ?: "",
                hasOverride = currentOverride != null,
                onSave = { hours ->
                    viewModel.setMonthOverride(hours)
                    overrideSheetOpen = false
                },
                onClear = {
                    viewModel.clearMonthOverride()
                    overrideSheetOpen = false
                },
                onCancel = { overrideSheetOpen = false },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthContent(
    state: WorkTimeUiState.Data,
    selectedYear: Int,
    selectedMonth: Int,
    onSelectMonth: (year: Int, month: Int) -> Unit,
    onEditOverride: () -> Unit,
    onLongPress: (WorkTimeEntryWithBreaks) -> Unit,
) {
    val monthPrefix = "%04d-%02d".format(selectedYear, selectedMonth)
    val monthEntries = state.entries.filter { it.entry.date.startsWith(monthPrefix) }
    val override = state.monthOverrides.find { it.year == selectedYear && it.month == selectedMonth }?.targetHours
    val summary = computeMonthlySummary(
        entries = state.entries,
        year = selectedYear,
        month = selectedMonth,
        employmentPercent = state.employmentPercent,
        targetHoursPerDay = state.userDefaultTargetHours,
        hourlyWage = state.hourlyWage,
        overrideTargetHours = override,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                MonthYearPicker(selectedYear, selectedMonth, onSelectMonth)
                MonthSummaryTiles(summary, onEditOverride)
            }
            Spacer(Modifier.height(Spacing.s))
        }

        if (monthEntries.isEmpty()) {
            item {
                UrsText(
                    stringResource(R.string.worktime_empty),
                    color = UrsTheme.colors.onSurfaceMuted,
                    modifier = Modifier.padding(top = Spacing.l),
                )
            }
        } else {
            items(monthEntries, key = { it.entry.id }) { entryWithBreaks ->
                EntryCard(entryWithBreaks, state.userDefaultTargetHours, onLongPress = { onLongPress(entryWithBreaks) })
            }
        }
    }
}

@Composable
private fun MonthYearPicker(selectedYear: Int, selectedMonth: Int, onSelect: (year: Int, month: Int) -> Unit) {
    val currentYear = LocalDate.now().year
    val yearOptions = remember(currentYear) { (currentYear downTo currentYear - 3).toList() }
    val monthOptions = remember { (1..12).toList() }

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        UrsDropdownField(
            label = stringResource(R.string.worktime_month),
            options = monthOptions,
            selectedLabel = monthName(selectedMonth),
            optionLabel = { monthName(it) },
            onSelect = { onSelect(selectedYear, it) },
            modifier = Modifier.weight(1f),
        )
        UrsDropdownField(
            label = stringResource(R.string.worktime_year),
            options = yearOptions,
            selectedLabel = selectedYear.toString(),
            optionLabel = { it.toString() },
            onSelect = { onSelect(it, selectedMonth) },
            modifier = Modifier.weight(1f),
        )
    }
}

private fun monthName(month: Int): String =
    Month.of(month).getDisplayName(JavaTimeTextStyle.FULL, Locale.getDefault())

@Composable
private fun MonthSummaryTiles(summary: MonthlySummary, onEditOverride: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        MonthStatTile(
            label = stringResource(R.string.worktime_stat_hours),
            value = formatHours(summary.actualHours),
            modifier = Modifier.weight(1f),
        )
        MonthStatTile(
            label = stringResource(R.string.worktime_stat_plus_minus),
            value = summary.overUndertimeHours?.let { formatSignedHours(it) } ?: "–",
            valueColor = summary.overUndertimeHours?.let { if (it < 0) FormErrorColor else null },
            onClick = onEditOverride,
            modifier = Modifier.weight(1f),
        )
        MonthStatTile(
            label = stringResource(R.string.worktime_stat_earnings),
            value = summary.earnings?.let { formatHours(it) } ?: "–",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MonthStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    UrsCard(
        radius = Radius.row,
        modifier = modifier.let { if (onClick != null) it.clickable(onClick = onClick) else it },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            UrsText(label, style = UrsTheme.typography.caption, color = UrsTheme.colors.onSurfaceMuted)
            UrsText(value, style = UrsTheme.typography.cardTitle, color = valueColor ?: UrsTheme.colors.onSurface)
        }
    }
}

@Composable
private fun MonthOverrideSheet(
    year: Int,
    month: Int,
    initialValue: String,
    hasOverride: Boolean,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onCancel: () -> Unit,
) {
    var value by remember(year, month, initialValue) { mutableStateOf(initialValue) }

    Column(
        modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(
            stringResource(R.string.worktime_override_title, monthName(month), year.toString()),
            style = UrsTheme.typography.cardTitle,
        )
        UrsTextField(
            value = value,
            onValueChange = { value = it },
            label = stringResource(R.string.worktime_override_label),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsOutlinedButton(
                text = stringResource(if (hasOverride) R.string.worktime_override_clear else R.string.cancel),
                onClick = if (hasOverride) onClear else onCancel,
                modifier = Modifier.weight(1f),
            )
            UrsButton(
                text = stringResource(R.string.save),
                onClick = { onSave(value) },
                enabled = value.toFloatOrNull() != null,
                modifier = Modifier.weight(1f),
            )
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
