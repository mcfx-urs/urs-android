package ch.mcfx.urs.journal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.mcfx.urs.R
import ch.mcfx.urs.chores.ChoreIconView
import ch.mcfx.urs.chores.choreColorPalette
import ch.mcfx.urs.chores.choreEmojiChoices
import ch.mcfx.urs.chores.choreMaterialIcons
import ch.mcfx.urs.chores.defaultChoreColor
import ch.mcfx.urs.chores.defaultChoreIcon
import ch.mcfx.urs.chores.parseChoreColor
import ch.mcfx.urs.chores.trackerIconMaterialVector
import ch.mcfx.urs.chores.trackerIconToken
import ch.mcfx.urs.data.local.TrackerDomainEntity
import ch.mcfx.urs.data.local.TrackerEventEntity
import ch.mcfx.urs.data.local.TrackerTypeEntity
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCheckbox
import ch.mcfx.urs.ui.components.UrsDateField
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.components.UrsTimeField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import java.time.LocalDate

sealed interface JournalTypeEditorTarget {
    data class New(val domainId: String) : JournalTypeEditorTarget
    data class Edit(val type: TrackerTypeEntity) : JournalTypeEditorTarget
}

sealed interface JournalEntryEditorTarget {
    data class New(val date: LocalDate) : JournalEntryEditorTarget
    data class Edit(val event: TrackerEventEntity) : JournalEntryEditorTarget
}

// --- Options / filter (GitHub issue #83) ---

// Opens directly to the filter (no separate landing step) - domains listed
// with checkboxes, each expanding to its types as individually toggleable
// chips; unchecking a domain hides every type inside it. Applies live,
// without closing the sheet - same interaction shape as Chores'
// ChoreTypeFilterSheet, one level deeper (domain > type instead of just type).
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun JournalFilterSheet(
    domains: List<TrackerDomainEntity>,
    activeTypes: List<TrackerTypeEntity>,
    hiddenTypeIds: Set<String>,
    onToggleType: (String) -> Unit,
    onToggleDomain: (List<String>) -> Unit,
    onAddDomain: () -> Unit,
    onAddType: (domainId: String) -> Unit,
    onEditType: (TrackerTypeEntity) -> Unit,
    onOpenOverview: () -> Unit,
) {
    val colors = UrsTheme.colors
    val typesByDomain = remember(activeTypes) { activeTypes.groupBy { it.domainId } }
    val ungrouped = typesByDomain[null].orEmpty()

    Column(
        modifier = Modifier
            .padding(horizontal = Spacing.l)
            .padding(bottom = Spacing.l)
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            UrsText(stringResource(R.string.journal_filter_title), style = UrsTheme.typography.cardTitle)
            UrsText(
                text = stringResource(R.string.journal_overview_link),
                style = UrsTheme.typography.body,
                color = colors.accent,
                modifier = Modifier.clip(Radius.pill).clickable(onClick = onOpenOverview).padding(horizontal = Spacing.s, vertical = Spacing.xs),
            )
        }

        if (domains.isEmpty()) {
            UrsText(stringResource(R.string.journal_no_domains), style = UrsTheme.typography.body, color = colors.onSurfaceMuted)
        }

        domains.forEach { domain ->
            val domainTypes = typesByDomain[domain.publicId].orEmpty()
            val typeIds = domainTypes.map { it.publicId }
            val allHidden = typeIds.isNotEmpty() && typeIds.all { it in hiddenTypeIds }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    UrsCheckbox(checked = !allHidden, onCheckedChange = { onToggleDomain(typeIds) })
                    UrsText(domain.icon, style = TextStyle(fontSize = 16.sp))
                    UrsText(domain.name, style = UrsTheme.typography.body, modifier = Modifier.weight(1f))
                }
                FlowRow(
                    modifier = Modifier.padding(start = Spacing.xl),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    domainTypes.forEach { type ->
                        val selected = type.publicId !in hiddenTypeIds
                        val bg = if (selected) colors.accent else Color.Transparent
                        val fg = if (selected) colors.onAccent else colors.onSurface
                        Row(
                            modifier = Modifier
                                .clip(Radius.pill)
                                .background(bg)
                                .then(if (selected) Modifier else Modifier.border(1.dp, colors.onSurfaceMuted.copy(alpha = 0.3f), Radius.pill))
                                .combinedClickable(onClick = { onToggleType(type.publicId) }, onLongClick = { onEditType(type) })
                                .padding(horizontal = Spacing.m, vertical = Spacing.s),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(parseChoreColor(type.color)))
                            ChoreIconView(token = type.icon, tint = fg, size = 16.dp)
                            UrsText(type.name, style = UrsTheme.typography.body, color = fg)
                        }
                    }
                    UrsText(
                        text = stringResource(R.string.journal_add_type),
                        style = UrsTheme.typography.body,
                        color = colors.accent,
                        modifier = Modifier
                            .clip(Radius.pill)
                            .border(1.dp, colors.onSurfaceMuted.copy(alpha = 0.3f), Radius.pill)
                            .clickable { onAddType(domain.publicId) }
                            .padding(horizontal = Spacing.m, vertical = Spacing.s),
                    )
                }
            }
        }

        if (ungrouped.isNotEmpty()) {
            UrsText(stringResource(R.string.journal_ungrouped_types), style = UrsTheme.typography.caption, color = colors.onSurfaceMuted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                ungrouped.forEach { type ->
                    val selected = type.publicId !in hiddenTypeIds
                    Row(
                        modifier = Modifier
                            .clip(Radius.pill)
                            .background(if (selected) colors.accent else Color.Transparent)
                            .then(if (selected) Modifier else Modifier.border(1.dp, colors.onSurfaceMuted.copy(alpha = 0.3f), Radius.pill))
                            .combinedClickable(onClick = { onToggleType(type.publicId) }, onLongClick = { onEditType(type) })
                            .padding(horizontal = Spacing.m, vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        UrsText(type.name, style = UrsTheme.typography.body, color = if (selected) colors.onAccent else colors.onSurface)
                    }
                }
            }
        }

        UrsOutlinedButton(text = stringResource(R.string.journal_add_domain), onClick = onAddDomain, modifier = Modifier.fillMaxWidth())
    }
}

// --- Domain editor (create only - GitHub issue #83 has no edit/delete UI yet) ---

@Composable
fun JournalDomainEditorSheet(onSave: (name: String, color: String, icon: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf(defaultJournalDomainColor) }
    var icon by rememberSaveable { mutableStateOf(defaultJournalDomainIcon) }
    val colors = UrsTheme.colors

    Column(
        modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.journal_new_domain), style = UrsTheme.typography.cardTitle)
        UrsTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.journal_domain_name_label))

        UrsText(stringResource(R.string.chores_color_label), style = UrsTheme.typography.caption, color = colors.onSurfaceMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            journalDomainColorPalette.forEach { swatch ->
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            journalDomainIconChoices.forEach { emoji ->
                val selected = emoji == icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.accent.copy(alpha = 0.15f) else Color.Transparent)
                        .then(if (selected) Modifier.border(1.dp, colors.accent, RoundedCornerShape(10.dp)) else Modifier)
                        .clickable { icon = emoji }
                        .padding(Spacing.s),
                    contentAlignment = Alignment.Center,
                ) {
                    UrsText(emoji, style = TextStyle(fontSize = 20.sp))
                }
            }
        }

        UrsButton(
            text = stringResource(R.string.save),
            onClick = { onSave(name.trim(), color, icon) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// --- Type editor (adapted from Chores' TypeEditorSheet, domain picker added) ---

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun JournalTypeEditorSheet(
    target: JournalTypeEditorTarget,
    domains: List<TrackerDomainEntity>,
    onSave: (name: String, color: String, icon: String, domainId: String, calendar: String, expectedIntervalDays: Int?) -> Unit,
    onArchive: () -> Unit,
    notifyOverdueEnabled: Boolean = false,
    onNotifyOverdueChange: ((Boolean) -> Unit)? = null,
    onDirtyChanged: (Boolean) -> Unit = {},
) {
    val editing = target as? JournalTypeEditorTarget.Edit
    val initialName = editing?.type?.name.orEmpty()
    val initialColor = editing?.type?.color?.takeIf { it.isNotBlank() } ?: defaultChoreColor
    val initialIcon = editing?.type?.icon?.takeIf { it.isNotBlank() } ?: defaultChoreIcon
    val initialDomainId = editing?.type?.domainId ?: (target as? JournalTypeEditorTarget.New)?.domainId ?: domains.firstOrNull()?.publicId.orEmpty()
    val initialCalendar = editing?.type?.calendar.orEmpty()
    val initialInterval = editing?.type?.expectedIntervalDays?.toString().orEmpty()
    var name by rememberSaveable(target) { mutableStateOf(initialName) }
    var color by rememberSaveable(target) { mutableStateOf(initialColor) }
    var icon by rememberSaveable(target) { mutableStateOf(initialIcon) }
    var domainId by rememberSaveable(target) { mutableStateOf(initialDomainId) }
    var calendar by rememberSaveable(target) { mutableStateOf(initialCalendar) }
    var interval by rememberSaveable(target) { mutableStateOf(initialInterval) }
    var emojiTab by rememberSaveable(target) { mutableStateOf(trackerIconMaterialVector(icon) == null) }
    var typedEmoji by rememberSaveable(target) { mutableStateOf(if (trackerIconMaterialVector(icon) == null) icon else "") }
    val colors = UrsTheme.colors
    val selectedDomain = domains.firstOrNull { it.publicId == domainId }

    LaunchedEffect(name, color, icon, domainId, calendar, interval) {
        val dirty = name != initialName || color != initialColor || icon != initialIcon || domainId != initialDomainId ||
            calendar != initialCalendar || interval != initialInterval
        onDirtyChanged(dirty)
    }

    Column(
        modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(
            stringResource(if (editing != null) R.string.chores_edit_type else R.string.chores_new_type),
            style = UrsTheme.typography.cardTitle,
        )

        UrsDropdownField(
            label = stringResource(R.string.journal_domain_label),
            options = domains,
            selectedLabel = selectedDomain?.let { "${it.icon} ${it.name}" }.orEmpty(),
            optionLabel = { "${it.icon} ${it.name}" },
            onSelect = { domainId = it.publicId },
        )

        UrsTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.chores_type_name_label))

        UrsTextField(value = calendar, onValueChange = { calendar = it }, label = stringResource(R.string.chores_type_calendar_label))
        UrsText(stringResource(R.string.chores_type_calendar_hint), style = UrsTheme.typography.caption, color = colors.onSurfaceMuted)

        UrsTextField(
            value = interval,
            onValueChange = { new -> interval = new.filter(Char::isDigit).take(4) },
            label = stringResource(R.string.chores_type_interval_label),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        UrsText(stringResource(R.string.chores_type_interval_hint), style = UrsTheme.typography.caption, color = colors.onSurfaceMuted)
        if (editing != null && onNotifyOverdueChange != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                UrsCheckbox(checked = notifyOverdueEnabled, onCheckedChange = onNotifyOverdueChange)
                UrsText(stringResource(R.string.chores_type_notify_overdue), style = UrsTheme.typography.body)
            }
        }

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
            JournalSegChip(stringResource(R.string.chores_icon_tab_material), selected = !emojiTab) { emojiTab = false }
            JournalSegChip(stringResource(R.string.chores_icon_tab_emoji), selected = emojiTab) { emojiTab = true }
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
            onClick = { onSave(name, color, icon, domainId, calendar, interval.toIntOrNull()?.takeIf { it > 0 }) },
            enabled = name.isNotBlank() && domainId.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (editing != null) {
            UrsOutlinedButton(text = stringResource(R.string.chores_archive_type), onClick = onArchive, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun JournalSegChip(label: String, selected: Boolean, onClick: () -> Unit) {
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

// --- Entry editor (domain -> type cascade, date range, time range/all-day) ---

private data class EntryDraft(
    val domainId: String,
    val typeId: String,
    val startDate: String,
    val endDate: String,
    val allDay: Boolean,
    val startTime: String,
    val endTime: String,
    val note: String,
)

@Composable
fun JournalEntryEditorSheet(
    target: JournalEntryEditorTarget,
    domains: List<TrackerDomainEntity>,
    activeTypes: List<TrackerTypeEntity>,
    onSave: (typeId: String, startDate: String, endDate: String?, startTime: String?, endTime: String?, note: String?) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDirtyChanged: (Boolean) -> Unit = {},
) {
    if (activeTypes.isEmpty() || domains.isEmpty()) {
        Column(modifier = Modifier.padding(Spacing.l)) {
            UrsText(stringResource(R.string.chores_need_a_type), style = UrsTheme.typography.body, color = UrsTheme.colors.onSurfaceMuted)
        }
        return
    }

    val initial = remember(target) {
        when (target) {
            is JournalEntryEditorTarget.New -> {
                val firstType = activeTypes.first()
                EntryDraft(firstType.domainId ?: domains.first().publicId, firstType.publicId, target.date.toString(), target.date.toString(), false, "09:00", "09:30", "")
            }
            is JournalEntryEditorTarget.Edit -> {
                val event = target.event
                val type = activeTypes.firstOrNull { it.publicId == event.trackerTypeId }
                EntryDraft(
                    domainId = type?.domainId ?: domains.first().publicId,
                    typeId = event.trackerTypeId,
                    startDate = event.occurredOn,
                    endDate = event.occurredOnEnd ?: event.occurredOn,
                    allDay = event.occurredAt == null,
                    startTime = event.occurredAt ?: "09:00",
                    endTime = event.occurredAtEnd ?: "09:30",
                    note = event.note.orEmpty(),
                )
            }
        }
    }
    var domainId by rememberSaveable(target) { mutableStateOf(initial.domainId) }
    var typeId by rememberSaveable(target) { mutableStateOf(initial.typeId) }
    var startDate by rememberSaveable(target) { mutableStateOf(initial.startDate) }
    var endDate by rememberSaveable(target) { mutableStateOf(initial.endDate) }
    var allDay by rememberSaveable(target) { mutableStateOf(initial.allDay) }
    var startTime by rememberSaveable(target) { mutableStateOf(initial.startTime) }
    var endTime by rememberSaveable(target) { mutableStateOf(initial.endTime) }
    var note by rememberSaveable(target) { mutableStateOf(initial.note) }

    val typesInDomain = activeTypes.filter { it.domainId == domainId }
    val effectiveTypeId = if (typesInDomain.any { it.publicId == typeId }) typeId else typesInDomain.firstOrNull()?.publicId.orEmpty()

    LaunchedEffect(domainId, typeId, startDate, endDate, allDay, startTime, endTime, note) {
        val current = EntryDraft(domainId, effectiveTypeId, startDate, endDate, allDay, startTime, endTime, note)
        onDirtyChanged(current != initial)
    }

    Column(
        modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(
            stringResource(if (target is JournalEntryEditorTarget.Edit) R.string.chores_edit_event else R.string.chores_new_event),
            style = UrsTheme.typography.cardTitle,
        )

        UrsDropdownField(
            label = stringResource(R.string.journal_domain_label),
            options = domains,
            selectedLabel = domains.firstOrNull { it.publicId == domainId }?.let { "${it.icon} ${it.name}" }.orEmpty(),
            optionLabel = { "${it.icon} ${it.name}" },
            onSelect = { domainId = it.publicId },
        )

        if (typesInDomain.isEmpty()) {
            UrsText(stringResource(R.string.journal_domain_has_no_types), style = UrsTheme.typography.body, color = UrsTheme.colors.onSurfaceMuted)
        } else {
            UrsDropdownField(
                label = stringResource(R.string.chores_type_label),
                options = typesInDomain,
                selectedLabel = typesInDomain.firstOrNull { it.publicId == effectiveTypeId }?.name.orEmpty(),
                optionLabel = { it.name },
                onSelect = { typeId = it.publicId },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            UrsDateField(value = startDate, onValueChange = { startDate = it; if (endDate < it) endDate = it }, label = stringResource(R.string.journal_start_date_label), modifier = Modifier.weight(1f))
            UrsDateField(value = endDate, onValueChange = { endDate = it }, label = stringResource(R.string.journal_end_date_label), modifier = Modifier.weight(1f))
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            UrsCheckbox(checked = allDay, onCheckedChange = { allDay = it })
            UrsText(stringResource(R.string.journal_all_day), style = UrsTheme.typography.body)
        }
        if (!allDay) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                UrsTimeField(
                    value = startTime,
                    onValueChange = { value ->
                        startTime = value
                        val (h, m) = value.split(":").map { it.toIntOrNull() ?: 0 }
                        val endMinutes = (h * 60 + m + 30).mod(24 * 60)
                        endTime = "%02d:%02d".format(endMinutes / 60, endMinutes % 60)
                    },
                    label = stringResource(R.string.chores_time_label),
                    modifier = Modifier.weight(1f),
                )
                UrsTimeField(value = endTime, onValueChange = { endTime = it }, label = stringResource(R.string.journal_end_time_label), modifier = Modifier.weight(1f))
            }
        }

        UrsTextField(value = note, onValueChange = { note = it }, label = stringResource(R.string.chores_note_label), singleLine = false, minLines = 2)

        UrsButton(
            text = stringResource(R.string.save),
            onClick = {
                onSave(
                    effectiveTypeId,
                    startDate,
                    endDate.takeIf { it != startDate },
                    if (allDay) null else startTime,
                    if (allDay) null else endTime,
                    note,
                )
            },
            enabled = startDate.isNotBlank() && endDate >= startDate && effectiveTypeId.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (target is JournalEntryEditorTarget.Edit && onDelete != null) {
            UrsOutlinedButton(text = stringResource(R.string.chores_delete_event), onClick = onDelete, modifier = Modifier.fillMaxWidth())
        }
    }
}
