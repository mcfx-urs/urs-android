package ch.mcfx.urs.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.chores.ChoreIconView
import ch.mcfx.urs.chores.isChoreOverdue
import ch.mcfx.urs.chores.parseChoreColor
import ch.mcfx.urs.data.local.TrackerTypeEntity
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val OverdueBadgeColor = Color(0xFFD64545)

/**
 * Journal's own destination for the relocated per-type list (GitHub issue
 * #83) — same content/behaviour as Chores' StatsStrip (icon, name, "since"
 * text, drag-to-reorder), just its own screen instead of a section at the
 * bottom of the calendar. Reached from the options menu's link.
 */
@Composable
fun JournalOverviewScreen(viewModel: JournalViewModel = viewModel(factory = JournalViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lastDoneByType = remember(state.events) {
        val today = LocalDate.now()
        state.events
            .mapNotNull { e -> runCatching { LocalDate.parse(e.occurredOn) }.getOrNull()?.let { e.trackerTypeId to it } }
            .filter { !it.second.isAfter(today) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, dates) -> dates.max() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.l, vertical = Spacing.m).verticalScroll(rememberScrollState())) {
        UrsText(stringResource(R.string.journal_overview_title), style = UrsTheme.typography.cardTitle)
        Column(Modifier.padding(top = Spacing.m)) {
            JournalStatsStrip(types = state.activeTypes, lastDoneByType = lastDoneByType, onReorder = viewModel::reorderTypes)
        }
    }
}

@Composable
private fun JournalStatsStrip(
    types: List<TrackerTypeEntity>,
    lastDoneByType: Map<String, LocalDate>,
    onReorder: (List<String>) -> Unit,
) {
    if (types.isEmpty()) return
    val today = LocalDate.now()
    val spacingPx = with(LocalDensity.current) { Spacing.s.toPx() }

    var orderedIds by remember(types.map { it.publicId }) { mutableStateOf(types.map { it.publicId }) }
    val byId = remember(types) { types.associateBy { it.publicId } }
    val itemHeights = remember { mutableStateMapOf<String, Int>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        orderedIds.forEach { id ->
            val type = byId[id] ?: return@forEach
            val isDragging = id == draggingId
            val last = lastDoneByType[type.publicId]
            val text = when {
                last == null -> stringResource(R.string.chores_stat_never)
                last == today -> stringResource(R.string.chores_stat_today)
                else -> stringResource(R.string.chores_stat_days_ago, ChronoUnit.DAYS.between(last, today))
            }
            val overdue = isChoreOverdue(type.expectedIntervalDays, last, today)

            UrsCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { itemHeights[id] = it.size.height }
                    .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                    .zIndex(if (isDragging) 1f else 0f)
                    .pointerInput(id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { draggingId = id; dragOffset = 0f },
                            onDragEnd = { draggingId = null; dragOffset = 0f; onReorder(orderedIds) },
                            onDragCancel = { draggingId = null; dragOffset = 0f },
                            onDrag = { change, delta ->
                                change.consume()
                                dragOffset += delta.y
                                val step = (itemHeights[id] ?: 0) + spacingPx
                                if (step <= 0f) return@detectDragGesturesAfterLongPress
                                val currentIndex = orderedIds.indexOf(id)
                                val targetIndex = (currentIndex + (dragOffset / step).toInt()).coerceIn(0, orderedIds.lastIndex)
                                if (targetIndex != currentIndex) {
                                    orderedIds = orderedIds.toMutableList().apply { add(targetIndex, removeAt(currentIndex)) }
                                    dragOffset -= (targetIndex - currentIndex) * step
                                }
                            },
                        )
                    },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(parseChoreColor(type.color)))
                    ChoreIconView(token = type.icon, tint = UrsTheme.colors.onSurface, size = 16.dp)
                    UrsText(type.name, style = UrsTheme.typography.body, modifier = Modifier.weight(1f))
                    if (overdue) {
                        UrsText(
                            text = stringResource(R.string.chores_stat_overdue),
                            style = UrsTheme.typography.caption,
                            color = UrsTheme.colors.onAccent,
                            modifier = Modifier.clip(Radius.pill).background(OverdueBadgeColor).padding(horizontal = Spacing.s, vertical = 2.dp),
                        )
                    }
                    UrsText(text, style = UrsTheme.typography.caption, color = UrsTheme.colors.onSurfaceMuted)
                }
            }
        }
    }
}
