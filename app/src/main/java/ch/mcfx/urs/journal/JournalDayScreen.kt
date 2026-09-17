package ch.mcfx.urs.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val HourHeight = 48.dp
private val CalendarLabelFontSize = 10.sp
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

    val dayIso = date.toString()
    val dayEvents = remember(state.events, dayIso) {
        state.events.filter { event ->
            val end = event.occurredOnEnd ?: event.occurredOn
            event.occurredOn <= dayIso && end >= dayIso
        }
    }
    val allDayEvents = dayEvents.filter { it.occurredAt == null }
    val timedEvents = dayEvents.filter { it.occurredAt != null }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.l, vertical = Spacing.m)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            UrsText(date.format(dayHeaderFormat), style = UrsTheme.typography.cardTitle, modifier = Modifier.weight(1f))
            UrsIconButton(
                onClick = { entryEditor = JournalEntryEditorTarget.New(date) },
                contentDescription = stringResource(R.string.journal_add_entry),
                imageVector = Icons.Filled.Add,
            )
        }

        if (allDayEvents.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.m),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                allDayEvents.forEach { event ->
                    val type = state.typesByPublicId[event.trackerTypeId]
                    val domain = type?.domainId?.let { state.domainsByPublicId[it] }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(domain?.color?.let(::parseChoreColor) ?: UrsTheme.colors.onSurfaceMuted)
                            .clickable { entryEditor = JournalEntryEditorTarget.Edit(event) }
                            .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        if (type != null) ChoreIconView(token = type.icon, tint = UrsTheme.colors.onAccent, size = 16.dp)
                        UrsText(
                            type?.name.orEmpty(),
                            style = UrsTheme.typography.caption.copy(fontSize = CalendarLabelFontSize),
                            color = UrsTheme.colors.onAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = Spacing.m).verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.fillMaxWidth().height(HourHeight * 24)) {
                for (hour in 0 until 24) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(HourHeight)
                            .offset(y = HourHeight * hour),
                    ) {
                        UrsText(
                            text = "%02d:00".format(hour),
                            style = UrsTheme.typography.caption,
                            color = UrsTheme.colors.onSurfaceMuted,
                            modifier = Modifier.width(40.dp),
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(UrsTheme.colors.onSurfaceMuted.copy(alpha = 0.15f)),
                        )
                    }
                }
                timedEvents.forEach { event -> TimedEventBar(event, state, onClick = { entryEditor = JournalEntryEditorTarget.Edit(event) }) }
            }
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

@Composable
private fun TimedEventBar(event: TrackerEventEntity, state: JournalUiState, onClick: () -> Unit) {
    val type = state.typesByPublicId[event.trackerTypeId]
    val domain = type?.domainId?.let { state.domainsByPublicId[it] }
    val startMinutes = event.occurredAt?.let(::minutesOf) ?: 0
    val endMinutes = event.occurredAtEnd?.let(::minutesOf) ?: (startMinutes + 30)
    val topDp = HourHeight * (startMinutes / 60f)
    val heightDp = (HourHeight * ((endMinutes - startMinutes).coerceAtLeast(15) / 60f))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 44.dp, end = 4.dp)
            .offset(y = topDp)
            .height(heightDp)
            .clip(RoundedCornerShape(6.dp))
            .background(domain?.color?.let(::parseChoreColor) ?: UrsTheme.colors.onSurfaceMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(modifier = Modifier.clip(CircleShape).background(UrsTheme.colors.onAccent).height(6.dp).width(6.dp))
        UrsText(
            type?.name.orEmpty(),
            style = UrsTheme.typography.caption.copy(fontSize = CalendarLabelFontSize),
            color = UrsTheme.colors.onAccent,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

private fun minutesOf(time: String): Int {
    val (h, m) = time.split(":").map { it.toIntOrNull() ?: 0 }
    return h * 60 + m
}
