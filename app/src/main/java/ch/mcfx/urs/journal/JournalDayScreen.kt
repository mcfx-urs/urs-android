package ch.mcfx.urs.journal

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.chores.ChoreIconView
import ch.mcfx.urs.chores.parseChoreColor
import ch.mcfx.urs.data.local.TrackerEventEntity
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsDiscardChangesDialog
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val HourHeight = 48.dp
private val GridStart = 44.dp
private val LabelHeight = 18.dp
private const val MinEventMinutes = 30
private val CalendarLabelFontSize = 13.sp
private val dayHeaderFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

/**
 * Journal's own day view (GitHub issue #83), replacing Chores' DayDetailSheet
 * bottom sheet with its own route: a fixed all-day/multi-day strip at the
 * top, a scrollable 00:00-24:00 hourly grid below for timed events. No
 * back-arrow — the system nav bar's own back button covers it.
 */
@Composable
fun JournalDayScreen(date: LocalDate, viewModel: JournalViewModel = viewModel(factory = JournalViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var entryEditor by remember { mutableStateOf<JournalEntryEditorTarget?>(null) }
    var entryEditorDirty by remember { mutableStateOf(false) }
    var confirmingDiscardEntryEditor by remember { mutableStateOf(false) }
    LaunchedEffect(entryEditor) { entryEditorDirty = false }

    var shownDateIso by rememberSaveable { mutableStateOf(date.toString()) }
    val shownDate = LocalDate.parse(shownDateIso)
    val gridScrollState = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.l, vertical = Spacing.xs)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            UrsText(
                shownDate.format(dayHeaderFormat),
                style = UrsTheme.typography.cardTitle,
                modifier = Modifier.weight(1f).padding(start = 48.dp, top = Spacing.xs, bottom = Spacing.xs),
            )
            UrsIconButton(
                onClick = { entryEditor = JournalEntryEditorTarget.New(shownDate) },
                contentDescription = stringResource(R.string.journal_add_entry),
                imageVector = Icons.Filled.Add,
            )
        }

        JournalSwipePager(
            key = shownDate,
            step = { day, delta -> day.plusDays(delta.toLong()) },
            onSettled = { shownDateIso = it.toString() },
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { day ->
            JournalDayPage(
                date = day,
                state = state,
                gridScrollState = gridScrollState,
                onEditEntry = { entryEditor = JournalEntryEditorTarget.Edit(it) },
                onSlotClick = { time -> entryEditor = JournalEntryEditorTarget.New(day, time) },
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
                onDelete = (target as? JournalEntryEditorTarget.Edit)?.let { edit -> { viewModel.deleteEvent(edit.event.id); entryEditor = null } },
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


/** One day's content (all-day strip plus hourly grid), rendered per page by the swipe pager. */
@Composable
private fun JournalDayPage(
    date: LocalDate,
    state: JournalUiState,
    gridScrollState: ScrollState,
    onEditEntry: (TrackerEventEntity) -> Unit,
    onSlotClick: (String) -> Unit,
) {
    val dayIso = date.toString()
    val dayEvents = remember(state.events, dayIso) {
        state.events.filter { event ->
            val end = event.occurredOnEnd ?: event.occurredOn
            event.occurredOn <= dayIso && end >= dayIso
        }
    }
    val allDayEvents = dayEvents.filter { it.occurredAt == null }
    val timedEvents = dayEvents.filter { it.occurredAt != null }

    Column(modifier = Modifier.fillMaxSize()) {
        if (allDayEvents.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                allDayEvents.forEach { event ->
                    val type = state.typesByPublicId[event.trackerTypeId]
                    val domain = type?.domainId?.let { state.domainsByPublicId[it] }
                    val barColor = domain?.color?.let(::parseChoreColor) ?: UrsTheme.colors.onSurfaceMuted
                    val contentColor = readableOn(barColor)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(barColor)
                            .clickable { onEditEntry(event) }
                            .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        if (type != null) ChoreIconView(token = type.icon, tint = contentColor, size = 16.dp)
                        UrsText(
                            type?.name.orEmpty(),
                            style = UrsTheme.typography.caption.copy(fontSize = CalendarLabelFontSize),
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Vertical padding leaves room for the first/last hour label, which is centered on its line.
        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = Spacing.xs).verticalScroll(gridScrollState).padding(vertical = LabelHeight / 2)) {
            Box(modifier = Modifier.fillMaxWidth().height(HourHeight * 24)) {
                Box(
                    modifier = Modifier
                        .padding(start = GridStart)
                        .fillMaxSize()
                        .background(UrsTheme.colors.onSurfaceMuted.copy(alpha = 0.08f))
                        .pointerInput(Unit) {
                            detectTapGestures { position ->
                                val slot = ((position.y / HourHeight.toPx() * 2).toInt()).coerceIn(0, 47)
                                onSlotClick("%02d:%02d".format(slot / 2, (slot % 2) * 30))
                            }
                        },
                )
                for (halfHour in 0..48) {
                    val isHour = halfHour % 2 == 0
                    Box(
                        modifier = Modifier
                            .padding(start = GridStart)
                            .fillMaxWidth()
                            .height(1.dp)
                            .offset(y = HourHeight * (halfHour / 2f))
                            .background(UrsTheme.colors.onSurfaceMuted.copy(alpha = if (isHour) 0.5f else 0.2f)),
                    )
                }
                for (hour in 0 until 24) {
                    UrsText(
                        text = "%02d:00".format(hour),
                        style = UrsTheme.typography.caption,
                        color = UrsTheme.colors.onSurfaceMuted,
                        modifier = Modifier
                            .width(GridStart - 4.dp)
                            .height(LabelHeight)
                            .offset(y = HourHeight * hour - LabelHeight / 2),
                    )
                }
                if (date == LocalDate.now()) {
                    val nowMinutes by produceState(currentMinutes()) {
                        while (true) {
                            value = currentMinutes()
                            delay(30_000)
                        }
                    }
                    val accent = UrsTheme.colors.accent
                    Box(
                        modifier = Modifier
                            .padding(start = GridStart)
                            .fillMaxWidth()
                            .height(2.dp)
                            .offset(y = HourHeight * (nowMinutes / 60f) - 1.dp)
                            .background(accent),
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = GridStart - 4.dp, y = HourHeight * (nowMinutes / 60f) - 4.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                }
                BoxWithConstraints(modifier = Modifier.padding(start = GridStart, end = 4.dp).fillMaxSize()) {
                    layoutOverlaps(timedEvents, dayIso).forEach { placement ->
                        TimedEventBar(
                            placement = placement,
                            state = state,
                            totalWidth = maxWidth,
                            scrollState = gridScrollState,
                            onClick = { onEditEntry(placement.event) },
                        )
                    }
                }
            }
        }
    }
}

private data class TimedPlacement(val event: TrackerEventEntity, val startMinutes: Int, val endMinutes: Int, val column: Int, val columns: Int)

/**
 * Splits events that overlap in time into side-by-side columns: events are
 * grouped into clusters of transitively overlapping ones, each cluster's
 * events take the first free column, and all events of a cluster share the
 * cluster's column count as their width divisor.
 */
private fun layoutOverlaps(events: List<TrackerEventEntity>, dayIso: String): List<TimedPlacement> {
    val items = events.map { event ->
        // A timed entry spanning several days covers the whole day except at its first and last day.
        val start = if (event.occurredOn == dayIso) event.occurredAt?.let(::minutesOf) ?: 0 else 0
        val endsToday = (event.occurredOnEnd ?: event.occurredOn) == dayIso
        val end = if (endsToday) {
            maxOf(event.occurredAtEnd?.let(::minutesOf) ?: (start + MinEventMinutes), start + MinEventMinutes)
        } else {
            24 * 60
        }
        Triple(event, start, minOf(end, 24 * 60))
    }.sortedWith(compareBy({ it.second }, { it.third }))

    val result = mutableListOf<TimedPlacement>()
    var index = 0
    while (index < items.size) {
        val cluster = mutableListOf<Triple<TrackerEventEntity, Int, Int>>()
        var clusterEnd = Int.MIN_VALUE
        while (index < items.size && (cluster.isEmpty() || items[index].second < clusterEnd)) {
            cluster.add(items[index])
            clusterEnd = maxOf(clusterEnd, items[index].third)
            index++
        }
        val columnEnds = mutableListOf<Int>()
        val columnOf = cluster.map { (_, start, end) ->
            val free = columnEnds.indexOfFirst { it <= start }
            if (free >= 0) { columnEnds[free] = end; free } else { columnEnds.add(end); columnEnds.lastIndex }
        }
        cluster.forEachIndexed { i, (event, start, end) ->
            result.add(TimedPlacement(event, start, end, columnOf[i], columnEnds.size))
        }
    }
    return result
}

@Composable
private fun TimedEventBar(
    placement: TimedPlacement,
    state: JournalUiState,
    totalWidth: androidx.compose.ui.unit.Dp,
    scrollState: ScrollState,
    onClick: () -> Unit,
) {
    val event = placement.event
    val type = state.typesByPublicId[event.trackerTypeId]
    val domain = type?.domainId?.let { state.domainsByPublicId[it] }
    val barColor = domain?.color?.let(::parseChoreColor) ?: UrsTheme.colors.onSurfaceMuted
    val contentColor = readableOn(barColor)
    val columnWidth = totalWidth / placement.columns

    val barHeight = HourHeight * ((placement.endMinutes - placement.startMinutes) / 60f)
    val barTopPx = with(LocalDensity.current) { (HourHeight * (placement.startMinutes / 60f)).toPx() }
    val labelPx = with(LocalDensity.current) { (LabelHeight + 2.dp).toPx() }
    val barHeightPx = with(LocalDensity.current) { barHeight.toPx() }
    val gridPaddingPx = with(LocalDensity.current) { (LabelHeight / 2).toPx() }

    Box(
        modifier = Modifier
            .offset(x = columnWidth * placement.column, y = HourHeight * (placement.startMinutes / 60f))
            .width(columnWidth - 2.dp)
            .height(barHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(barColor)
            .clickable(onClick = onClick),
    ) {
        // The title row stays on the bar's top edge and, while the bar extends past the top of the viewport,
        // slides down with the scroll (never past the bar's bottom) so a long block stays identifiable.
        Row(
            modifier = Modifier
                .offset {
                    val viewportTop = scrollState.value - gridPaddingPx
                    IntOffset(0, (viewportTop - barTopPx).coerceIn(0f, (barHeightPx - labelPx).coerceAtLeast(0f)).roundToInt())
                }
                .padding(horizontal = Spacing.xs, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            JournalTypeDot(type?.color?.let(::parseChoreColor) ?: UrsTheme.colors.onSurfaceMuted, contentColor, size = 10.dp)
            UrsText(
                type?.name.orEmpty(),
                style = UrsTheme.typography.caption.copy(fontSize = CalendarLabelFontSize),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

private fun currentMinutes(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

private fun minutesOf(time: String): Int {
    val (h, m) = time.split(":").map { it.toIntOrNull() ?: 0 }
    return h * 60 + m
}
