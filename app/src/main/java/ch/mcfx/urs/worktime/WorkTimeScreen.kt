package ch.mcfx.urs.worktime

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.MonthlySummary
import ch.mcfx.urs.data.WageBreakdown
import ch.mcfx.urs.data.WageLineItem
import ch.mcfx.urs.data.WageLineItemType
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
import ch.mcfx.urs.ui.components.ursScreenContentPadding
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

// Deliberately not a Spacing token — this is a gesture-recognition
// threshold, not a layout distance, and needs to be large enough that an
// off-axis wobble mid vertical-scroll can't trip it (GitHub issue #6).
private val MonthSwipeThreshold = 96.dp

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
    val overrideSheetOpen by viewModel.showOverrideSheet.collectAsStateWithLifecycle()
    val overrideSaveFailed by viewModel.overrideSaveFailed.collectAsStateWithLifecycle()
    // Holds the tapped tile's breakdown itself (not just an open/closed
    // flag) since MonthContent recomputes summary internally — bundling the
    // data here avoids a second computeMonthlySummary call at this level
    // just to re-derive it. Rendered as a sibling of the Box below (like
    // the other three sheets), never nested inside it — nesting it under
    // MonthContent (inside the Box) previously let the FAB, declared after
    // MonthContent in the same Box, draw on top of the sheet instead of
    // being covered by it.
    var wageBreakdownRequest by remember { mutableStateOf<WageBreakdownRequest?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            WorkTimeUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))
            is WorkTimeUiState.Data -> MonthContent(
                state = state,
                selectedYear = selectedYear,
                selectedMonth = selectedMonth,
                onSelectMonth = viewModel::selectMonth,
                onEditOverride = viewModel::openOverrideSheet,
                onLongPress = viewModel::openActionSheet,
                onShowWageBreakdown = { breakdown, hourlyWage, actualHours ->
                    wageBreakdownRequest = WageBreakdownRequest(breakdown, hourlyWage, actualHours)
                },
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
        UrsBottomSheet(onDismissRequest = viewModel::closeOverrideSheet) {
            MonthOverrideSheet(
                year = selectedYear,
                month = selectedMonth,
                initialValue = currentOverride?.daysWorked ?: "",
                hasOverride = currentOverride != null,
                saveFailed = overrideSaveFailed,
                onSave = viewModel::setMonthOverride,
                onClear = viewModel::clearMonthOverride,
                onCancel = viewModel::closeOverrideSheet,
            )
        }
    }

    wageBreakdownRequest?.let { request ->
        UrsBottomSheet(onDismissRequest = { wageBreakdownRequest = null }) {
            WageBreakdownSheet(breakdown = request.breakdown, hourlyWage = request.hourlyWage, actualHours = request.actualHours)
        }
    }
}

private data class WageBreakdownRequest(val breakdown: WageBreakdown, val hourlyWage: String, val actualHours: Float)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthContent(
    state: WorkTimeUiState.Data,
    selectedYear: Int,
    selectedMonth: Int,
    onSelectMonth: (year: Int, month: Int) -> Unit,
    onEditOverride: () -> Unit,
    onLongPress: (WorkTimeEntryWithBreaks) -> Unit,
    onShowWageBreakdown: (breakdown: WageBreakdown, hourlyWage: String, actualHours: Float) -> Unit,
) {
    val monthPrefix = "%04d-%02d".format(selectedYear, selectedMonth)
    val monthEntries = state.entries.filter { it.entry.date.startsWith(monthPrefix) }
    val override = state.monthOverrides.find { it.year == selectedYear && it.month == selectedMonth }?.daysWorked
    val today = LocalDate.now()
    val isCurrentMonth = selectedYear == today.year && selectedMonth == today.monthValue
    val summary = computeMonthlySummary(
        entries = state.entries,
        year = selectedYear,
        month = selectedMonth,
        employmentPercent = state.employmentPercent,
        targetHoursPerDay = state.userDefaultTargetHours,
        hourlyWage = state.hourlyWage,
        wageRules = state.wageRules,
        overrideDaysWorked = override,
        isCurrentMonth = isCurrentMonth,
    )

    // Complements MonthYearPicker's dropdowns (GitHub issue #6) rather than
    // replacing them — same year bound as its own yearOptions
    // (currentYear downTo currentYear - 3) so a swipe can never reach a
    // month the dropdowns themselves wouldn't offer. Discrete switch, no
    // drag-follows-finger animation: this list's own content is already
    // fully re-derived per selected month (entries/summary), so there's no
    // single scrollable "next month" content to visually drag in — a
    // HorizontalPager would need to pre-build neighboring pages for that.
    val minYear = today.year - 3
    val maxYear = today.year
    val density = LocalDensity.current
    var dragAccumulatorPx by remember { mutableStateOf(0f) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(selectedYear, selectedMonth, minYear, maxYear) {
                val thresholdPx = with(density) { MonthSwipeThreshold.toPx() }
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulatorPx = 0f },
                    onDragEnd = {
                        if (dragAccumulatorPx <= -thresholdPx) {
                            val (year, month) = nextMonth(selectedYear, selectedMonth)
                            if (year <= maxYear) onSelectMonth(year, month)
                        } else if (dragAccumulatorPx >= thresholdPx) {
                            val (year, month) = previousMonth(selectedYear, selectedMonth)
                            if (year >= minYear) onSelectMonth(year, month)
                        }
                        dragAccumulatorPx = 0f
                    },
                    onDragCancel = { dragAccumulatorPx = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    dragAccumulatorPx += dragAmount
                }
            },
        contentPadding = ursScreenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                MonthYearPicker(selectedYear, selectedMonth, onSelectMonth)
                MonthSummaryTiles(
                    summary = summary,
                    onEditOverride = onEditOverride,
                    onShowWageBreakdown = {
                        summary.wageBreakdown?.let { onShowWageBreakdown(it, state.hourlyWage, summary.actualHours) }
                    },
                )
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

private fun nextMonth(year: Int, month: Int): Pair<Int, Int> =
    if (month == 12) (year + 1) to 1 else year to (month + 1)

private fun previousMonth(year: Int, month: Int): Pair<Int, Int> =
    if (month == 1) (year - 1) to 12 else year to (month - 1)

@Composable
private fun MonthSummaryTiles(summary: MonthlySummary, onEditOverride: () -> Unit, onShowWageBreakdown: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
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
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            EarningsTile(
                wageBreakdown = summary.wageBreakdown,
                onClick = onShowWageBreakdown,
                modifier = Modifier.weight(1f),
            )
            MonthStatTile(
                label = stringResource(R.string.worktime_stat_percent_of_soll),
                value = summary.percentOfContractSoll?.let { "${formatHours(it)}%" } ?: "–",
                valueColor = summary.percentOfContractSoll?.let { if (it < 100f) FormErrorColor else null },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// Two stacked rows (gross/net) instead of MonthStatTile's single value —
// same card shell, but right-aligned tabular-figure values so the decimal
// points of both figures line up vertically regardless of digit count.
// Clickable (opens WageBreakdownSheet) only once there's a breakdown to
// show — same null-guard the "–" placeholder below already relies on.
@Composable
private fun EarningsTile(wageBreakdown: WageBreakdown?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    UrsCard(
        radius = Radius.row,
        modifier = modifier.let { if (wageBreakdown != null) it.clickable(onClick = onClick) else it },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            UrsText(
                stringResource(R.string.worktime_stat_earnings),
                style = UrsTheme.typography.caption,
                color = UrsTheme.colors.onSurfaceMuted,
            )
            EarningsRow(stringResource(R.string.worktime_stat_gross), wageBreakdown?.gross)
            EarningsRow(stringResource(R.string.worktime_stat_net), wageBreakdown?.net)
        }
    }
}

@Composable
private fun EarningsRow(label: String, amount: Float?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        UrsText(label, style = UrsTheme.typography.body, color = UrsTheme.colors.onSurfaceMuted)
        UrsText(
            amount?.let { formatHours(it) } ?: "–",
            style = UrsTheme.typography.cardTitle.copy(fontFamily = FontFamily.Monospace, textAlign = TextAlign.End),
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
    saveFailed: Boolean,
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
        if (saveFailed) {
            UrsText(
                stringResource(R.string.error_save),
                color = FormErrorColor,
                style = UrsTheme.typography.body,
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
                if (entryWithBreaks.entry.mealAllowance) {
                    UrsPill(text = stringResource(R.string.worktime_meal_allowance_badge))
                }
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
        if (entryWithBreaks.entry.comment.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            UrsText(
                entryWithBreaks.entry.comment,
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
private fun WageBreakdownSheet(breakdown: WageBreakdown, hourlyWage: String, actualHours: Float) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        UrsText(stringResource(R.string.worktime_wage_breakdown_title), style = UrsTheme.typography.cardTitle)

        WageBreakdownRow(
            label = stringResource(R.string.worktime_wage_breakdown_base_wage),
            detail = stringResource(R.string.worktime_wage_breakdown_base_wage_detail, formatHours(actualHours), hourlyWage),
            amount = breakdown.baseWage,
        )
        breakdown.surcharges.forEach { WageLineItemRow(it) }
        if (breakdown.mealAllowanceDays > 0) {
            WageBreakdownRow(
                label = stringResource(R.string.worktime_wage_breakdown_meal_allowance, breakdown.mealAllowanceDays),
                detail = null,
                amount = breakdown.mealAllowanceAmount,
            )
        }
        WageBreakdownTotalRow(stringResource(R.string.worktime_stat_gross), breakdown.gross)
        breakdown.deductions.forEach { WageLineItemRow(it, isDeduction = true) }
        WageBreakdownTotalRow(stringResource(R.string.worktime_stat_net), breakdown.net)
    }
}

@Composable
private fun WageLineItemRow(item: WageLineItem, isDeduction: Boolean = false) {
    WageBreakdownRow(
        label = stringResource(item.type.labelRes()),
        detail = item.percent?.let { "${formatHours(it)}%" },
        amount = if (isDeduction) -item.amount else item.amount,
    )
}

private fun WageLineItemType.labelRes(): Int = when (this) {
    WageLineItemType.VACATION_PAY -> R.string.worktime_wage_breakdown_vacation_pay
    WageLineItemType.HOLIDAY_PAY -> R.string.worktime_wage_breakdown_holiday_pay
    WageLineItemType.THIRTEENTH_MONTH -> R.string.worktime_wage_breakdown_thirteenth_month
    WageLineItemType.AHV_IV_EO -> R.string.worktime_wage_breakdown_ahv
    WageLineItemType.ALV -> R.string.worktime_wage_breakdown_alv
    WageLineItemType.SUVA_NBU -> R.string.worktime_wage_breakdown_suva
    WageLineItemType.KTG -> R.string.worktime_wage_breakdown_ktg
    WageLineItemType.BVG -> R.string.worktime_wage_breakdown_bvg
}

@Composable
private fun WageBreakdownRow(label: String, detail: String?, amount: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = Spacing.m)) {
            UrsText(label, style = UrsTheme.typography.body)
            detail?.let { UrsText(it, style = UrsTheme.typography.caption, color = UrsTheme.colors.onSurfaceMuted) }
        }
        UrsText(
            formatHours(amount),
            style = UrsTheme.typography.body.copy(fontFamily = FontFamily.Monospace, textAlign = TextAlign.End),
        )
    }
}

@Composable
private fun WageBreakdownTotalRow(label: String, amount: Float) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        UrsText(label, style = UrsTheme.typography.cardTitle)
        UrsText(
            formatHours(amount),
            style = UrsTheme.typography.cardTitle.copy(fontFamily = FontFamily.Monospace, textAlign = TextAlign.End),
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

// Not private: also used by WorkTimeAddScreen's live daily-total preview.
fun formatHours(hours: Float): String = String.format(Locale.US, "%.2f", hours)

fun formatSignedHours(hours: Float): String =
    if (hours >= 0) "+${formatHours(hours)}" else formatHours(hours)
