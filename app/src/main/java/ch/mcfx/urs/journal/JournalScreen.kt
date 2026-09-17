package ch.mcfx.urs.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.chores.ChoreIconView
import ch.mcfx.urs.chores.parseChoreColor
import ch.mcfx.urs.chores.shareIcsExport
import ch.mcfx.urs.data.local.TrackerDomainEntity
import ch.mcfx.urs.data.local.TrackerEventEntity
import ch.mcfx.urs.data.local.TrackerTypeEntity
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsDiscardChangesDialog
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale

// Slightly smaller than the design system's own `caption` (13sp) - these
// labels sit inside a fixed-height chip/bar with no room to wrap, so they
// also always render single-line, clipped rather than ellipsized (the
// owner's explicit preference - just cut off whatever's longer than the bar).
private val CalendarLabelFontSize = 10.sp
private const val MAX_LANES = 2
private const val MAX_CHIPS_PER_DAY = 4
private const val MonthSwipeThresholdPx = 64f
private val WeekNumberGutterWidth = 20.dp
private val DayNumberHeight = 20.dp
private val BarHeight = 16.dp
private val BarGap = 2.dp

@Composable
fun JournalScreen(
    viewModel: JournalViewModel = viewModel(factory = JournalViewModel.Factory),
    onOpenDay: (LocalDate) -> Unit,
    onOpenOverview: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val hiddenTypeIds by viewModel.hiddenTypeIds.collectAsStateWithLifecycle()
    val notifyTypeIds by viewModel.notifyTypeIds.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var filterOpen by remember { mutableStateOf(false) }
    var domainEditor by remember { mutableStateOf(false) }
    var typeEditor by remember { mutableStateOf<JournalTypeEditorTarget?>(null) }
    var entryEditor by remember { mutableStateOf<JournalEntryEditorTarget?>(null) }
    var exportSheet by remember { mutableStateOf(false) }

    var typeEditorDirty by remember { mutableStateOf(false) }
    var confirmingDiscardTypeEditor by remember { mutableStateOf(false) }
    LaunchedEffect(typeEditor) { typeEditorDirty = false }

    var entryEditorDirty by remember { mutableStateOf(false) }
    var confirmingDiscardEntryEditor by remember { mutableStateOf(false) }
    LaunchedEffect(entryEditor) { entryEditorDirty = false }

    val typesByPublicId = state.typesByPublicId
    val domainsByPublicId = state.domainsByPublicId
    val visibleEvents = remember(state.events, hiddenTypeIds) {
        state.events.filter { it.trackerTypeId !in hiddenTypeIds }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.l, vertical = Spacing.xs)) {
        JournalMonthHeader(
            month = month,
            onPrev = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
            onPick = viewModel::showMonth,
            onOpenFilter = { filterOpen = true },
            onExport = { exportSheet = true },
            onAdd = { entryEditor = JournalEntryEditorTarget.New(LocalDate.now()) },
        )
        Spacer(Modifier.height(Spacing.xs))
        JournalMonthGrid(
            month = month,
            events = visibleEvents,
            typesByPublicId = typesByPublicId,
            domainsByPublicId = domainsByPublicId,
            onDayClick = onOpenDay,
            onSwipePrev = viewModel::previousMonth,
            onSwipeNext = viewModel::nextMonth,
            modifier = Modifier.weight(1f),
        )
    }

    if (filterOpen) {
        UrsBottomSheet(onDismissRequest = { filterOpen = false }) {
            JournalFilterSheet(
                domains = state.domains,
                activeTypes = state.activeTypes,
                hiddenTypeIds = hiddenTypeIds,
                onToggleType = viewModel::toggleTypeVisible,
                onToggleDomain = viewModel::toggleDomainVisible,
                onAddDomain = { filterOpen = false; domainEditor = true },
                onAddType = { domainId -> filterOpen = false; typeEditor = JournalTypeEditorTarget.New(domainId) },
                onEditType = { filterOpen = false; typeEditor = JournalTypeEditorTarget.Edit(it) },
                onOpenOverview = { filterOpen = false; onOpenOverview() },
            )
        }
    }

    if (domainEditor) {
        UrsBottomSheet(onDismissRequest = { domainEditor = false }) {
            JournalDomainEditorSheet(
                onSave = { name, color, icon ->
                    viewModel.createDomain(name, color, icon)
                    domainEditor = false
                },
            )
        }
    }

    typeEditor?.let { target ->
        UrsBottomSheet(onDismissRequest = { if (typeEditorDirty) confirmingDiscardTypeEditor = true else typeEditor = null }) {
            val editingType = (target as? JournalTypeEditorTarget.Edit)?.type
            JournalTypeEditorSheet(
                target = target,
                domains = state.domains,
                onSave = { name, color, icon, domainId, calendar, interval ->
                    when (target) {
                        is JournalTypeEditorTarget.New -> viewModel.createType(name, color, icon, domainId, calendar, interval)
                        is JournalTypeEditorTarget.Edit -> viewModel.updateType(target.type.id, name, color, icon, domainId, calendar, interval)
                    }
                    typeEditor = null
                },
                onArchive = {
                    (target as? JournalTypeEditorTarget.Edit)?.let { viewModel.archiveType(it.type.id) }
                    typeEditor = null
                },
                notifyOverdueEnabled = editingType?.let { it.publicId in notifyTypeIds } ?: false,
                onNotifyOverdueChange = editingType?.let { type -> { on: Boolean -> viewModel.setTypeNotifyEnabled(type.publicId, on) } },
                onDirtyChanged = { typeEditorDirty = it },
            )
        }
    }
    if (confirmingDiscardTypeEditor) {
        UrsDiscardChangesDialog(
            onDiscard = { confirmingDiscardTypeEditor = false; typeEditor = null },
            onKeepEditing = { confirmingDiscardTypeEditor = false },
        )
    }

    if (exportSheet) {
        UrsBottomSheet(onDismissRequest = { exportSheet = false }) {
            JournalExportSheet(
                onExport = { exportAll ->
                    exportSheet = false
                    val export = viewModel.buildIcsExport(exportAll)
                    shareIcsExport(context, export) { viewModel.markExported(export.exportedTypeLocalIds) }
                },
            )
        }
    }

    entryEditor?.let { target ->
        UrsBottomSheet(onDismissRequest = { if (entryEditorDirty) confirmingDiscardEntryEditor = true else entryEditor = null }) {
            JournalEntryEditorSheet(
                target = target,
                domains = state.domains,
                activeTypes = state.activeTypes,
                onSave = { typeId, startDate, endDate, startTime, endTime, note ->
                    when (target) {
                        is JournalEntryEditorTarget.New -> viewModel.logEvent(typeId, startDate, endDate, startTime, endTime, note)
                        is JournalEntryEditorTarget.Edit ->
                            viewModel.updateEvent(target.event.id, typeId, startDate, endDate, startTime, endTime, note)
                    }
                    entryEditor = null
                },
                onDirtyChanged = { entryEditorDirty = it },
            )
        }
    }
    if (confirmingDiscardEntryEditor) {
        UrsDiscardChangesDialog(
            onDiscard = { confirmingDiscardEntryEditor = false; entryEditor = null },
            onKeepEditing = { confirmingDiscardEntryEditor = false },
        )
    }
}

@Composable
private fun JournalMonthHeader(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPick: (YearMonth) -> Unit,
    onOpenFilter: () -> Unit,
    onExport: () -> Unit,
    onAdd: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val label = "${month.month.getDisplayName(JavaTextStyle.FULL, Locale.getDefault())} ${month.year}"
    val yearNow = LocalDate.now().year
    val years = (yearNow - 3..yearNow + 1).toList()

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UrsIconButton(onClick = onPrev, contentDescription = stringResource(R.string.chores_prev_month), imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft)
            UrsText(
                text = label,
                style = UrsTheme.typography.cardTitle,
                color = UrsTheme.colors.accent,
                modifier = Modifier.weight(1f).clip(Radius.pill).clickable { picking = !picking }.padding(vertical = Spacing.xs),
            )
            UrsIconButton(onClick = onNext, contentDescription = stringResource(R.string.chores_next_month), imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight)
            UrsIconButton(onClick = onOpenFilter, contentDescription = stringResource(R.string.chores_type_filter), imageVector = Icons.Filled.Tune)
            UrsIconButton(onClick = onExport, contentDescription = stringResource(R.string.chores_export_calendar), imageVector = Icons.Filled.Share)
            UrsIconButton(onClick = onAdd, contentDescription = stringResource(R.string.journal_add_entry), imageVector = Icons.Filled.Add)
        }
        if (picking) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                UrsDropdownField(
                    label = stringResource(R.string.chores_month_label),
                    options = (1..12).toList(),
                    selectedLabel = Month.of(month.monthValue).getDisplayName(JavaTextStyle.FULL, Locale.getDefault()),
                    optionLabel = { Month.of(it).getDisplayName(JavaTextStyle.FULL, Locale.getDefault()) },
                    onSelect = { onPick(YearMonth.of(month.year, it)); picking = false },
                    modifier = Modifier.weight(1f),
                )
                UrsDropdownField(
                    label = stringResource(R.string.chores_year_label),
                    options = years,
                    selectedLabel = month.year.toString(),
                    optionLabel = { it.toString() },
                    onSelect = { onPick(YearMonth.of(it, month.monthValue)); picking = false },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private data class JournalLanePlacement(val event: TrackerEventEntity, val lane: Int, val startCol: Int, val endCol: Int)

private fun isMultiDay(event: TrackerEventEntity): Boolean = event.occurredOnEnd != null && event.occurredOnEnd != event.occurredOn

// Longest event first (matches urs-web's Journal port) - a candidate lane is
// checked against every event already placed in it (full interval overlap),
// not just the previous one, since processing order is no longer chronological.
private fun assignJournalLanes(events: List<TrackerEventEntity>, days: List<LocalDate>): List<JournalLanePlacement> {
    val sorted = events.sortedByDescending { event ->
        val start = LocalDate.parse(event.occurredOn)
        val end = event.occurredOnEnd?.let(LocalDate::parse) ?: start
        ChronoUnit.DAYS.between(start, end)
    }
    val lanes = mutableListOf<MutableList<TrackerEventEntity>>()
    val placed = mutableListOf<JournalLanePlacement>()
    for (event in sorted) {
        val start = LocalDate.parse(event.occurredOn)
        val end = event.occurredOnEnd?.let(LocalDate::parse) ?: start
        var laneIndex = lanes.indexOfFirst { laneEvents ->
            laneEvents.all { placedEvent ->
                val placedStart = LocalDate.parse(placedEvent.occurredOn)
                val placedEnd = placedEvent.occurredOnEnd?.let(LocalDate::parse) ?: placedStart
                placedEnd.isBefore(start) || placedStart.isAfter(end)
            }
        }
        if (laneIndex == -1) {
            laneIndex = lanes.size
            lanes.add(mutableListOf())
        }
        lanes[laneIndex].add(event)
        val startCol = days.indexOfFirst { !it.isBefore(start) }.takeIf { it >= 0 } ?: 0
        val endCol = days.indexOfLast { !it.isAfter(end) }.takeIf { it >= 0 } ?: 6
        placed.add(JournalLanePlacement(event, laneIndex, startCol, endCol))
    }
    return placed
}

@Composable
private fun JournalMonthGrid(
    month: YearMonth,
    events: List<TrackerEventEntity>,
    typesByPublicId: Map<String, TrackerTypeEntity>,
    domainsByPublicId: Map<String, TrackerDomainEntity>,
    onDayClick: (LocalDate) -> Unit,
    onSwipePrev: () -> Unit,
    onSwipeNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstOfMonth = month.atDay(1)
    val gridStart = firstOfMonth.minusDays((firstOfMonth.dayOfWeek.value - 1).toLong())
    val weekdayLabels = remember { (1..7).map { DayOfWeek.of(it).getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()) } }
    var dragAccum by remember(month) { mutableStateOf(0f) }
    val today = LocalDate.now()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(month) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onDragEnd = {
                        if (dragAccum <= -MonthSwipeThresholdPx) onSwipeNext() else if (dragAccum >= MonthSwipeThresholdPx) onSwipePrev()
                        dragAccum = 0f
                    },
                    onDragCancel = { dragAccum = 0f },
                ) { change, amount -> change.consume(); dragAccum += amount }
            },
    ) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(WeekNumberGutterWidth))
            weekdayLabels.forEach { label ->
                UrsText(
                    text = label,
                    style = UrsTheme.typography.caption.copy(textAlign = TextAlign.Center),
                    color = UrsTheme.colors.onSurfaceMuted,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val weekCount = 6
        Column(Modifier.fillMaxWidth().weight(1f)) {
            for (week in 0 until weekCount) {
                val weekStart = gridStart.plusDays((week * 7).toLong())
                val days = (0 until 7).map { weekStart.plusDays(it.toLong()) }
                val rowStart = days.first()
                val rowEnd = days.last()
                val rowEvents = events.filter { e ->
                    val start = LocalDate.parse(e.occurredOn)
                    val end = e.occurredOnEnd?.let(LocalDate::parse) ?: start
                    !start.isAfter(rowEnd) && !end.isBefore(rowStart)
                }
                val multiDayAll = assignJournalLanes(rowEvents.filter(::isMultiDay), days)
                val multiDay = multiDayAll.filter { it.lane < MAX_LANES }
                val laneCoverageByDay = days.map { date ->
                    val covering = multiDay.filter { placement ->
                        val start = LocalDate.parse(placement.event.occurredOn)
                        val end = placement.event.occurredOnEnd?.let(LocalDate::parse) ?: start
                        !date.isBefore(start) && !date.isAfter(end)
                    }
                    if (covering.isEmpty()) 0 else covering.maxOf { it.lane } + 1
                }
                val weekNumber = weekStart.get(WeekFields.ISO.weekOfWeekBasedYear())

                JournalWeekRow(
                    days = days,
                    month = month,
                    today = today,
                    weekNumber = weekNumber,
                    rowEvents = rowEvents,
                    multiDay = multiDay,
                    laneCoverageByDay = laneCoverageByDay,
                    typesByPublicId = typesByPublicId,
                    domainsByPublicId = domainsByPublicId,
                    onDayClick = onDayClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// Two layers in one Box, not one shared Row of lane-rows: (1) one clickable
// column per day, its own per-day lane spacer sized only for the bars that
// actually cross that specific day (a bar-free day reserves nothing), then
// its chip stack; (2) the multi-day bars drawn on top via Row(weight) so a
// bar still visually spans multiple day columns and intercepts taps in its
// own area, while empty stretches of that overlay row have no click handler
// at all and let the tap fall through to the day column underneath. This is
// the same fix the equivalent urs-web bug needed - a globally shared lane
// block (same reserved height for every day, and a real gap between the
// day-number/lane-gap/chip areas that nothing beneath it responded to) is
// exactly what this replaces.
@Composable
private fun JournalWeekRow(
    days: List<LocalDate>,
    month: YearMonth,
    today: LocalDate,
    weekNumber: Int,
    rowEvents: List<TrackerEventEntity>,
    multiDay: List<JournalLanePlacement>,
    laneCoverageByDay: List<Int>,
    typesByPublicId: Map<String, TrackerTypeEntity>,
    domainsByPublicId: Map<String, TrackerDomainEntity>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.width(WeekNumberGutterWidth).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            UrsText(weekNumber.toString(), style = UrsTheme.typography.caption, color = UrsTheme.colors.onSurfaceMuted, modifier = Modifier.padding(top = Spacing.xs))
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.matchParentSize()) {
                days.forEachIndexed { i, date ->
                    val dayEvents = rowEvents.filter { !isMultiDay(it) && it.occurredOn == date.toString() }
                    val laneSpacerHeight = if (laneCoverageByDay[i] > 0) BarGap + (BarHeight + BarGap) * laneCoverageByDay[i] else 0.dp
                    JournalDayCell(
                        date = date,
                        month = month,
                        today = today,
                        laneSpacerHeight = laneSpacerHeight,
                        events = dayEvents,
                        typesByPublicId = typesByPublicId,
                        domainsByPublicId = domainsByPublicId,
                        onClick = { onDayClick(date) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
            for (lane in 0 until MAX_LANES) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BarHeight)
                        .offset(y = DayNumberHeight + BarGap + (BarHeight + BarGap) * lane),
                ) {
                    var col = 0
                    while (col < 7) {
                        val bar = multiDay.firstOrNull { it.lane == lane && it.startCol == col }
                        if (bar != null) {
                            val type = typesByPublicId[bar.event.trackerTypeId]
                            val domain = type?.domainId?.let { domainsByPublicId[it] }
                            val span = (bar.endCol - bar.startCol + 1).coerceAtLeast(1)
                            Box(
                                modifier = Modifier
                                    .weight(span.toFloat())
                                    .fillMaxHeight()
                                    .padding(horizontal = 1.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(domain?.color?.let(::parseChoreColor) ?: UrsTheme.colors.onSurfaceMuted)
                                    .clickable { onDayClick(LocalDate.parse(bar.event.occurredOn)) }
                                    .padding(horizontal = 2.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                UrsText(
                                    type?.name.orEmpty(),
                                    style = UrsTheme.typography.caption.copy(fontSize = CalendarLabelFontSize),
                                    color = UrsTheme.colors.onAccent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                            }
                            col = bar.endCol + 1
                        } else {
                            Spacer(Modifier.weight(1f).fillMaxHeight())
                            col += 1
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalDayCell(
    date: LocalDate,
    month: YearMonth,
    today: LocalDate,
    laneSpacerHeight: androidx.compose.ui.unit.Dp,
    events: List<TrackerEventEntity>,
    typesByPublicId: Map<String, TrackerTypeEntity>,
    domainsByPublicId: Map<String, TrackerDomainEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inMonth = YearMonth.from(date) == month
    val numberColor = when {
        !inMonth -> UrsTheme.colors.onSurfaceMuted.copy(alpha = 0.4f)
        date == today -> UrsTheme.colors.accent
        else -> UrsTheme.colors.onSurface
    }
    val overflow = events.size > MAX_CHIPS_PER_DAY
    val visibleCount = if (overflow) MAX_CHIPS_PER_DAY - 1 else events.size

    // The whole cell is one clickable target (number, lane gap, and chips
    // alike) instead of separate clickable sub-areas with a dead zone
    // between them.
    Column(modifier = modifier.clickable(onClick = onClick).padding(horizontal = 1.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(DayNumberHeight), contentAlignment = Alignment.TopCenter) {
            UrsText(date.dayOfMonth.toString(), style = UrsTheme.typography.caption, color = numberColor)
        }
        Spacer(Modifier.height(laneSpacerHeight))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            events.take(visibleCount).forEach { event ->
                val type = typesByPublicId[event.trackerTypeId]
                val domain = type?.domainId?.let { domainsByPublicId[it] }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(domain?.color?.let(::parseChoreColor) ?: UrsTheme.colors.onSurfaceMuted)
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(type?.color?.let(::parseChoreColor) ?: UrsTheme.colors.onSurfaceMuted))
                    UrsText(
                        type?.name.orEmpty(),
                        style = UrsTheme.typography.caption.copy(fontSize = CalendarLabelFontSize),
                        color = UrsTheme.colors.onAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
            if (overflow) {
                UrsText("+${events.size - visibleCount}", style = UrsTheme.typography.caption, color = UrsTheme.colors.onSurfaceMuted)
            }
        }
    }
}

@Composable
private fun JournalExportSheet(onExport: (exportAll: Boolean) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.chores_export_title), style = UrsTheme.typography.cardTitle)
        UrsText(stringResource(R.string.chores_export_body), style = UrsTheme.typography.body, color = UrsTheme.colors.onSurfaceMuted)
        UrsButton(text = stringResource(R.string.chores_export_new_only), onClick = { onExport(false) }, modifier = Modifier.fillMaxWidth())
        UrsOutlinedButton(text = stringResource(R.string.chores_export_everything), onClick = { onExport(true) }, modifier = Modifier.fillMaxWidth())
    }
}
