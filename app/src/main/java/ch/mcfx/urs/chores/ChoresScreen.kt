package ch.mcfx.urs.chores

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.TrackerEventEntity
import ch.mcfx.urs.data.local.TrackerTypeEntity
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.io.File
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val MAX_DOTS = 4
private const val MonthSwipeThresholdPx = 64f

@Composable
fun ChoresScreen(viewModel: ChoresViewModel = viewModel(factory = ChoresViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val hiddenTypeIds by viewModel.hiddenTypeIds.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var daySheet by remember { mutableStateOf<LocalDate?>(null) }
    var typeEditor by remember { mutableStateOf<TypeEditorTarget?>(null) }
    var eventEditor by remember { mutableStateOf<EventEditorTarget?>(null) }
    var exportSheet by remember { mutableStateOf(false) }

    val typesByPublicId = state.typesByPublicId
    val eventsByDate = remember(state.events) {
        state.events.groupBy { runCatching { LocalDate.parse(it.occurredOn) }.getOrNull() }
            .mapNotNull { (date, list) -> date?.let { it to list } }
            .toMap()
    }
    val lastDoneByType = remember(state.events) {
        val today = LocalDate.now()
        state.events
            .mapNotNull { e -> runCatching { LocalDate.parse(e.occurredOn) }.getOrNull()?.let { e.trackerTypeId to it } }
            .filter { !it.second.isAfter(today) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, dates) -> dates.max() }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            MonthHeader(
                month = month,
                onPrev = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
                onPick = viewModel::showMonth,
                onExport = { exportSheet = true },
            )

            TypeFilterRow(
                types = state.activeTypes,
                hiddenTypeIds = hiddenTypeIds,
                onToggle = viewModel::toggleTypeVisible,
                onAdd = { typeEditor = TypeEditorTarget.New },
                onEditType = { typeEditor = TypeEditorTarget.Edit(it) },
            )

            MonthGrid(
                month = month,
                eventsByDate = eventsByDate,
                typesByPublicId = typesByPublicId,
                hiddenTypeIds = hiddenTypeIds,
                onDayClick = { daySheet = it },
                onSwipePrev = viewModel::previousMonth,
                onSwipeNext = viewModel::nextMonth,
            )

            Spacer(Modifier.height(Spacing.xs))
            StatsStrip(types = state.activeTypes, lastDoneByType = lastDoneByType)
        }

        daySheet?.let { date ->
            UrsBottomSheet(onDismissRequest = { daySheet = null }) {
                DayDetailSheet(
                    date = date,
                    events = eventsByDate[date].orEmpty(),
                    typesByPublicId = typesByPublicId,
                    onAddEvent = { eventEditor = EventEditorTarget.New(date) },
                    onEditEvent = { eventEditor = EventEditorTarget.Edit(it) },
                    onDeleteEvent = viewModel::deleteEvent,
                )
            }
        }

        typeEditor?.let { target ->
            UrsBottomSheet(onDismissRequest = { typeEditor = null }) {
                TypeEditorSheet(
                    target = target,
                    onSave = { name, color, icon, calendar ->
                        when (target) {
                            TypeEditorTarget.New -> viewModel.createType(name, color, icon, calendar)
                            is TypeEditorTarget.Edit -> viewModel.updateType(target.type.id, name, color, icon, calendar)
                        }
                        typeEditor = null
                    },
                    onArchive = {
                        (target as? TypeEditorTarget.Edit)?.let { viewModel.archiveType(it.type.id) }
                        typeEditor = null
                    },
                )
            }
        }

        if (exportSheet) {
            UrsBottomSheet(onDismissRequest = { exportSheet = false }) {
                ExportSheet(
                    onExport = { exportAll ->
                        exportSheet = false
                        val export = viewModel.buildIcsExport(exportAll)
                        if (shareIcsExport(context, export)) {
                            viewModel.markExported(export.exportedTypeLocalIds)
                        }
                    },
                )
            }
        }

        eventEditor?.let { target ->
            UrsBottomSheet(onDismissRequest = { eventEditor = null }) {
                EventEditorSheet(
                    target = target,
                    types = state.activeTypes,
                    onSave = { typeId, date, time, note ->
                        when (target) {
                            is EventEditorTarget.New -> viewModel.logEvent(typeId, date, time, note)
                            is EventEditorTarget.Edit -> viewModel.updateEvent(target.event.id, typeId, date, time, note)
                        }
                        eventEditor = null
                    },
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPick: (YearMonth) -> Unit,
    onExport: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val label = "${month.month.getDisplayName(JavaTextStyle.FULL, Locale.getDefault())} ${month.year}"
    val yearNow = LocalDate.now().year
    val years = (yearNow - 3..yearNow + 1).toList()

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UrsIconButton(
                onClick = onPrev,
                contentDescription = stringResource(R.string.chores_prev_month),
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            )
            UrsText(
                text = label,
                style = UrsTheme.typography.cardTitle,
                color = UrsTheme.colors.accent,
                modifier = Modifier
                    .weight(1f)
                    .clip(Radius.pill)
                    .clickable { picking = !picking }
                    .padding(vertical = Spacing.xs),
            )
            UrsIconButton(
                onClick = onNext,
                contentDescription = stringResource(R.string.chores_next_month),
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            )
            UrsIconButton(
                onClick = onExport,
                contentDescription = stringResource(R.string.chores_export_calendar),
                imageVector = Icons.Filled.Share,
            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TypeFilterRow(
    types: List<TrackerTypeEntity>,
    hiddenTypeIds: Set<String>,
    onToggle: (String) -> Unit,
    onAdd: () -> Unit,
    onEditType: (TrackerTypeEntity) -> Unit,
) {
    val colors = UrsTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(Radius.pill)
                .background(colors.accent.copy(alpha = 0.15f))
                .clickable(onClick = onAdd)
                .padding(horizontal = Spacing.m, vertical = Spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            UrsIcon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.chores_add_type),
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
            UrsText(stringResource(R.string.chores_add_type), style = UrsTheme.typography.body, color = colors.accent)
        }

        types.forEach { type ->
            val selected = type.publicId !in hiddenTypeIds
            val bg = if (selected) colors.accent else Color.Transparent
            val fg = if (selected) colors.onAccent else colors.onSurface
            Row(
                modifier = Modifier
                    .clip(Radius.pill)
                    .background(bg)
                    .then(if (selected) Modifier else Modifier.border(1.dp, colors.onSurfaceMuted.copy(alpha = 0.3f), Radius.pill))
                    .combinedClickable(onClick = { onToggle(type.publicId) }, onLongClick = { onEditType(type) })
                    .padding(horizontal = Spacing.m, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(parseChoreColor(type.color)))
                ChoreIconView(token = type.icon, tint = fg, size = 16.dp)
                UrsText(type.name, style = UrsTheme.typography.body, color = fg)
            }
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    eventsByDate: Map<LocalDate, List<TrackerEventEntity>>,
    typesByPublicId: Map<String, TrackerTypeEntity>,
    hiddenTypeIds: Set<String>,
    onDayClick: (LocalDate) -> Unit,
    onSwipePrev: () -> Unit,
    onSwipeNext: () -> Unit,
) {
    val firstOfMonth = month.atDay(1)
    val gridStart = firstOfMonth.minusDays((firstOfMonth.dayOfWeek.value - 1).toLong())
    val weekdayLabels = remember {
        (1..7).map { DayOfWeek.of(it).getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()) }
    }
    var dragAccum by remember(month) { mutableStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(month) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onDragEnd = {
                        if (dragAccum <= -MonthSwipeThresholdPx) onSwipeNext()
                        else if (dragAccum >= MonthSwipeThresholdPx) onSwipePrev()
                        dragAccum = 0f
                    },
                    onDragCancel = { dragAccum = 0f },
                ) { change, amount ->
                    change.consume()
                    dragAccum += amount
                }
            },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(Modifier.fillMaxWidth()) {
            weekdayLabels.forEach { label ->
                UrsText(
                    text = label,
                    style = UrsTheme.typography.caption.copy(textAlign = TextAlign.Center),
                    color = UrsTheme.colors.onSurfaceMuted,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        for (week in 0 until 6) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                for (dow in 0 until 7) {
                    val date = gridStart.plusDays((week * 7 + dow).toLong())
                    val inMonth = YearMonth.from(date) == month
                    val dayEvents = eventsByDate[date].orEmpty().filter { it.trackerTypeId !in hiddenTypeIds }
                    DayCell(
                        date = date,
                        inMonth = inMonth,
                        events = dayEvents,
                        typesByPublicId = typesByPublicId,
                        onClick = { onDayClick(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    inMonth: Boolean,
    events: List<TrackerEventEntity>,
    typesByPublicId: Map<String, TrackerTypeEntity>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UrsTheme.colors
    val today = LocalDate.now()
    val numberColor = when {
        !inMonth -> colors.onSurfaceMuted.copy(alpha = 0.4f)
        date == today -> colors.accent
        else -> colors.onSurface
    }
    val dotColors = events.map { it.trackerTypeId }.distinct()
        .map { typesByPublicId[it]?.color?.let(::parseChoreColor) ?: Color(0xFF9E9E9E) }

    Column(
        modifier = modifier
            .aspectRatio(0.82f)
            .clip(RoundedCornerShape(10.dp))
            .then(if (date == today) Modifier.background(colors.accent.copy(alpha = 0.10f)) else Modifier)
            .clickable(onClick = onClick)
            .padding(Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        UrsText(text = date.dayOfMonth.toString(), style = UrsTheme.typography.caption, color = numberColor)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            dotColors.take(MAX_DOTS).forEach { c -> Box(Modifier.size(6.dp).clip(CircleShape).background(c)) }
        }
        if (dotColors.size > MAX_DOTS) {
            UrsText("+${dotColors.size - MAX_DOTS}", style = UrsTheme.typography.caption, color = colors.onSurfaceMuted)
        }
    }
}

@Composable
private fun ExportSheet(onExport: (exportAll: Boolean) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.chores_export_title), style = UrsTheme.typography.cardTitle)
        UrsText(
            stringResource(R.string.chores_export_body),
            style = UrsTheme.typography.body,
            color = UrsTheme.colors.onSurfaceMuted,
        )
        UrsButton(
            text = stringResource(R.string.chores_export_new_only),
            onClick = { onExport(false) },
            modifier = Modifier.fillMaxWidth(),
        )
        UrsOutlinedButton(
            text = stringResource(R.string.chores_export_everything),
            onClick = { onExport(true) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Writes each .ics into cacheDir/chores-ics/ and hands it to the share
// sheet through the app's FileProvider. Returns true once the chooser is
// launched, so the caller only then marks the types exported.
private fun shareIcsExport(context: Context, export: IcsExport): Boolean {
    if (export.files.isEmpty()) {
        Toast.makeText(context, R.string.chores_export_nothing, Toast.LENGTH_SHORT).show()
        return false
    }
    val dir = File(context.cacheDir, "chores-ics").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }

    val uris = ArrayList<android.net.Uri>()
    for (file in export.files) {
        val out = File(dir, file.fileName)
        out.writeText(file.content)
        uris += FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
    }

    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "text/calendar"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    return runCatching { context.startActivity(Intent.createChooser(intent, null)) }
        .onFailure { Toast.makeText(context, R.string.chores_export_no_app, Toast.LENGTH_SHORT).show() }
        .isSuccess
}

@Composable
private fun StatsStrip(types: List<TrackerTypeEntity>, lastDoneByType: Map<String, LocalDate>) {
    if (types.isEmpty()) return
    val today = LocalDate.now()
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        types.forEach { type ->
            val last = lastDoneByType[type.publicId]
            val text = when {
                last == null -> stringResource(R.string.chores_stat_never)
                last == today -> stringResource(R.string.chores_stat_today)
                else -> stringResource(R.string.chores_stat_days_ago, ChronoUnit.DAYS.between(last, today))
            }
            UrsCard {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(parseChoreColor(type.color)))
                        ChoreIconView(token = type.icon, tint = UrsTheme.colors.onSurface, size = 16.dp)
                        UrsText(type.name, style = UrsTheme.typography.body)
                    }
                    UrsText(text, style = UrsTheme.typography.caption, color = UrsTheme.colors.onSurfaceMuted)
                }
            }
        }
    }
}
