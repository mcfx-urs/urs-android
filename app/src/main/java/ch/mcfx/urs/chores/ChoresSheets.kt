package ch.mcfx.urs.chores

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.TrackerEventEntity
import ch.mcfx.urs.data.local.TrackerTypeEntity
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCheckbox
import ch.mcfx.urs.ui.components.UrsDateField
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.components.UrsTimeField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

sealed interface TypeEditorTarget {
    data object New : TypeEditorTarget
    data class Edit(val type: TrackerTypeEntity) : TypeEditorTarget
}

sealed interface EventEditorTarget {
    data class New(val date: LocalDate) : EventEditorTarget
    data class Edit(val event: TrackerEventEntity) : EventEditorTarget
}

private val dayHeaderFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

// --- Day detail ---

@Composable
fun DayDetailSheet(
    date: LocalDate,
    events: List<TrackerEventEntity>,
    typesByPublicId: Map<String, TrackerTypeEntity>,
    onAddEvent: () -> Unit,
    onEditEvent: (TrackerEventEntity) -> Unit,
    onDeleteEvent: (Long) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(date.format(dayHeaderFormat), style = UrsTheme.typography.cardTitle)

        if (events.isEmpty()) {
            UrsText(
                stringResource(R.string.chores_day_empty),
                style = UrsTheme.typography.body,
                color = UrsTheme.colors.onSurfaceMuted,
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                events.sortedBy { it.occurredAt ?: "" }.forEach { event ->
                    val type = typesByPublicId[event.trackerTypeId]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.row))
                            .background(UrsTheme.colors.surface)
                            .clickable { onEditEvent(event) }
                            .padding(Spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(parseChoreColor(type?.color ?: "")))
                        if (type != null) ChoreIconView(token = type.icon, tint = UrsTheme.colors.onSurface, size = 18.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            UrsText(type?.name ?: stringResource(R.string.chores_unknown_type), style = UrsTheme.typography.body)
                            val sub = listOfNotNull(event.occurredAt?.ifBlank { null }, event.note?.ifBlank { null }).joinToString(" · ")
                            if (sub.isNotEmpty()) {
                                UrsText(sub, style = UrsTheme.typography.caption, color = UrsTheme.colors.onSurfaceMuted)
                            }
                        }
                        UrsIconButton(
                            onClick = { onDeleteEvent(event.id) },
                            contentDescription = stringResource(R.string.chores_delete_event),
                            imageVector = Icons.Filled.Delete,
                            tint = UrsTheme.colors.onSurfaceMuted,
                        )
                    }
                }
            }
        }

        UrsButton(
            text = stringResource(R.string.chores_log_here),
            onClick = onAddEvent,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// --- Event editor ---

@Composable
fun EventEditorSheet(
    target: EventEditorTarget,
    types: List<TrackerTypeEntity>,
    onSave: (typeId: String, date: String, time: String?, note: String?) -> Unit,
) {
    if (types.isEmpty()) {
        Column(modifier = Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsText(stringResource(R.string.chores_need_a_type), style = UrsTheme.typography.body, color = UrsTheme.colors.onSurfaceMuted)
        }
        return
    }

    val initial = remember(target) {
        when (target) {
            is EventEditorTarget.New -> EventDraft(types.first().publicId, target.date.toString(), "", "")
            is EventEditorTarget.Edit -> EventDraft(
                target.event.trackerTypeId,
                target.event.occurredOn,
                target.event.occurredAt.orEmpty(),
                target.event.note.orEmpty(),
            )
        }
    }
    var typeId by remember { mutableStateOf(initial.typeId) }
    var date by remember { mutableStateOf(initial.date) }
    var withTime by remember { mutableStateOf(initial.time.isNotBlank()) }
    var time by remember { mutableStateOf(initial.time) }
    var note by remember { mutableStateOf(initial.note) }

    val selectedType = types.firstOrNull { it.publicId == typeId } ?: types.first()

    Column(
        modifier = Modifier
            .padding(horizontal = Spacing.l)
            .padding(bottom = Spacing.l)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(
            stringResource(if (target is EventEditorTarget.Edit) R.string.chores_edit_event else R.string.chores_new_event),
            style = UrsTheme.typography.cardTitle,
        )

        UrsDropdownField(
            label = stringResource(R.string.chores_type_label),
            options = types,
            selectedLabel = selectedType.name,
            optionLabel = { it.name },
            onSelect = { typeId = it.publicId },
        )

        UrsDateField(value = date, onValueChange = { date = it }, label = stringResource(R.string.chores_date_label))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            UrsCheckbox(checked = withTime, onCheckedChange = { withTime = it; if (!it) time = "" })
            UrsText(stringResource(R.string.chores_add_time), style = UrsTheme.typography.body)
        }
        if (withTime) {
            UrsTimeField(value = time, onValueChange = { time = it }, label = stringResource(R.string.chores_time_label))
        }

        UrsTextField(
            value = note,
            onValueChange = { note = it },
            label = stringResource(R.string.chores_note_label),
            singleLine = false,
            minLines = 2,
        )

        UrsButton(
            text = stringResource(R.string.save),
            onClick = { onSave(typeId, date, if (withTime) time else null, note) },
            enabled = date.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private data class EventDraft(val typeId: String, val date: String, val time: String, val note: String)

// --- Type editor ---

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun TypeEditorSheet(
    target: TypeEditorTarget,
    onSave: (name: String, color: String, icon: String) -> Unit,
    onArchive: () -> Unit,
) {
    val editing = target as? TypeEditorTarget.Edit
    var name by remember { mutableStateOf(editing?.type?.name.orEmpty()) }
    var color by remember { mutableStateOf(editing?.type?.color?.takeIf { it.isNotBlank() } ?: defaultChoreColor) }
    var icon by remember { mutableStateOf(editing?.type?.icon?.takeIf { it.isNotBlank() } ?: defaultChoreIcon) }
    var emojiTab by remember { mutableStateOf(trackerIconMaterialVector(icon) == null) }
    var typedEmoji by remember { mutableStateOf(if (trackerIconMaterialVector(icon) == null) icon else "") }
    val colors = UrsTheme.colors

    Column(
        modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(
            stringResource(if (editing != null) R.string.chores_edit_type else R.string.chores_new_type),
            style = UrsTheme.typography.cardTitle,
        )

        UrsTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.chores_type_name_label))

        UrsText(stringResource(R.string.chores_color_label), style = UrsTheme.typography.caption, color = colors.onSurfaceMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            choreColorPalette.forEach { swatch ->
                val selected = swatch.equals(color, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(parseChoreColor(swatch))
                        .then(if (selected) Modifier.border(3.dp, colors.onSurface, CircleShape) else Modifier)
                        .clickable { color = swatch },
                )
            }
        }

        UrsText(stringResource(R.string.chores_icon_label), style = UrsTheme.typography.caption, color = colors.onSurfaceMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            SegChip(stringResource(R.string.chores_icon_tab_material), selected = !emojiTab) { emojiTab = false }
            SegChip(stringResource(R.string.chores_icon_tab_emoji), selected = emojiTab) { emojiTab = true }
        }

        Column(modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
            if (!emojiTab) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    choreMaterialIcons.forEach { (key, vector) ->
                        val token = trackerIconToken(key)
                        val selected = token == icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) colors.accent.copy(alpha = 0.15f) else Color.Transparent)
                                .then(if (selected) Modifier.border(1.dp, colors.accent, RoundedCornerShape(10.dp)) else Modifier)
                                .clickable { icon = token }
                                .padding(Spacing.s),
                            contentAlignment = Alignment.Center,
                        ) {
                            UrsIcon(imageVector = vector, contentDescription = key, tint = colors.onSurface, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        choreEmojiChoices.forEach { emoji ->
                            val selected = emoji == icon
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) colors.accent.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { icon = emoji; typedEmoji = emoji }
                                    .padding(Spacing.s),
                                contentAlignment = Alignment.Center,
                            ) {
                                UrsText(emoji, style = TextStyle(fontSize = 20.sp))
                            }
                        }
                    }
                    UrsTextField(
                        value = typedEmoji,
                        onValueChange = { typedEmoji = it; if (it.isNotBlank()) icon = it.trim() },
                        label = stringResource(R.string.chores_icon_custom_emoji),
                    )
                }
            }
        }

        UrsButton(
            text = stringResource(R.string.save),
            onClick = { onSave(name, color, icon) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (editing != null) {
            UrsOutlinedButton(
                text = stringResource(R.string.chores_archive_type),
                onClick = onArchive,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SegChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = UrsTheme.colors
    UrsText(
        text = label,
        style = UrsTheme.typography.body,
        color = if (selected) colors.onAccent else colors.onSurface,
        modifier = Modifier
            .clip(Radius.pill)
            .background(if (selected) colors.accent else Color.Transparent)
            .then(if (selected) Modifier else Modifier.border(1.dp, colors.onSurfaceMuted.copy(alpha = 0.3f), Radius.pill))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
    )
}
