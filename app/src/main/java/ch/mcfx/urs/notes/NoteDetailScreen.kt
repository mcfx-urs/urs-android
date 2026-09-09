package ch.mcfx.urs.notes

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.NoteRepository
import ch.mcfx.urs.notes.richtext.RichTextField
import ch.mcfx.urs.notes.richtext.RichTextFieldState
import ch.mcfx.urs.notes.richtext.RichTextLinkSheetHost
import ch.mcfx.urs.notes.richtext.rememberRichTextFieldState
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsCheckbox
import ch.mcfx.urs.ui.components.UrsDateField
import ch.mcfx.urs.ui.components.UrsDiscardChangesDialog
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.components.UrsTimeField
import ch.mcfx.urs.ui.components.ursFormScrollPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// No "error" role in the design system's palette yet — same local-constant
// pattern already used elsewhere (e.g. WorkTimeAddScreen).
private val FormErrorColor = Color(0xFFD64545)

@Composable
fun NoteDetailScreen(
    onDone: () -> Unit,
    /** Null creates a new note; set edits that note (its publicId, see [ch.mcfx.urs.data.local.publicId]). */
    noteId: String? = null,
    viewModel: NoteDetailViewModel = viewModel(factory = NoteDetailViewModel.Factory),
    onDirtyChanged: (Boolean) -> Unit = {},
) {
    val form by viewModel.formState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { if (noteId != null) viewModel.loadForEdit(noteId) else viewModel.startNew() }
    LaunchedEffect(form.finished) { if (form.finished) onDone() }
    LaunchedEffect(form.dirty) { onDirtyChanged(form.dirty) }

    var confirmingDelete by remember { mutableStateOf(false) }
    var confirmingDiscard by remember { mutableStateOf(false) }
    val richTextState = rememberRichTextFieldState(form.content)

    BackHandler(enabled = form.dirty) { confirmingDiscard = true }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            form.loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))
            form.notFound -> UrsText(
                stringResource(R.string.note_not_found),
                modifier = Modifier.align(Alignment.Center),
                color = UrsTheme.colors.onSurfaceMuted,
            )
            else -> NoteForm(
                form = form,
                viewModel = viewModel,
                richTextState = richTextState,
                onRequestDelete = { confirmingDelete = true },
            )
        }
    }

    if (confirmingDelete) {
        UrsBottomSheet(onDismissRequest = { confirmingDelete = false }) {
            DeleteConfirmSheet(
                onConfirm = {
                    confirmingDelete = false
                    viewModel.delete()
                },
                onCancel = { confirmingDelete = false },
            )
        }
    }

    if (confirmingDiscard) {
        UrsDiscardChangesDialog(
            onDiscard = {
                confirmingDiscard = false
                onDone()
            },
            onKeepEditing = { confirmingDiscard = false },
        )
    }

    // Same screen-top-level placement reasoning as the delete-confirmation
    // sheet above — see RichTextLinkSheetHost's own doc comment for why this
    // can't live nested inside RichTextField/NoteForm instead.
    RichTextLinkSheetHost(state = richTextState, onValueChange = viewModel::setContent)
}

@Composable
private fun NoteForm(
    form: NoteDetailFormState,
    viewModel: NoteDetailViewModel,
    richTextState: RichTextFieldState,
    onRequestDelete: () -> Unit,
) {
    val nextFieldAction = KeyboardActions(onNext = {})
    val isEditing = form.localId != null
    val formScrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = Modifier.ursFormScrollPadding(scrollState = formScrollState),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsText(
                stringResource(if (isEditing) R.string.note_detail_title_edit else R.string.note_detail_title_new),
                style = UrsTheme.typography.screenTitle,
                modifier = Modifier.weight(1f),
            )
            NoteExportMenu(
                onShareAsText = { shareNoteAsText(context, form) },
                onCreateCalendarEvent = { createNoteCalendarEvent(context, form) },
            )
        }

        UrsTextField(
            value = form.title,
            onValueChange = viewModel::setTitle,
            label = stringResource(R.string.note_field_title),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = nextFieldAction,
            modifier = Modifier.fillMaxWidth(),
        )

        RichTextField(
            state = richTextState,
            onValueChange = viewModel::setContent,
            label = stringResource(R.string.note_field_content),
            formScrollState = formScrollState,
            modifier = Modifier.fillMaxWidth(),
        )

        TagsEditor(form = form, viewModel = viewModel)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsText(
                text = stringResource(R.string.note_reminder_enable),
                style = UrsTheme.typography.body,
                modifier = Modifier.weight(1f).padding(end = Spacing.m),
            )
            UrsCheckbox(checked = form.reminderEnabled, onCheckedChange = viewModel::setReminderEnabled)
        }
        if (form.reminderEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                UrsDateField(
                    value = form.reminderDate,
                    onValueChange = viewModel::setReminderDate,
                    label = stringResource(R.string.note_reminder_date),
                    modifier = Modifier.weight(1f),
                )
                UrsTimeField(
                    value = form.reminderTime,
                    onValueChange = viewModel::setReminderTime,
                    label = stringResource(R.string.note_reminder_time),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (form.submitFailed) {
            UrsText(stringResource(R.string.error_save), color = FormErrorColor, style = UrsTheme.typography.body)
        }

        UrsButton(
            text = stringResource(if (form.submitting) R.string.saving else R.string.save),
            onClick = viewModel::submit,
            enabled = form.isValid && !form.submitting,
            modifier = Modifier.fillMaxWidth(),
        )

        if (isEditing) {
            UrsOutlinedButton(
                text = stringResource(if (form.status == NoteRepository.STATUS_ACTIVE) R.string.note_complete else R.string.note_reopen),
                onClick = viewModel::toggleStatus,
                modifier = Modifier.fillMaxWidth(),
            )
            UrsOutlinedButton(
                text = stringResource(R.string.note_delete),
                onClick = onRequestDelete,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TagsEditor(form: NoteDetailFormState, viewModel: NoteDetailViewModel) {
    // The suggestion cards render below the tag input as a sibling, appearing only once
    // `tagSuggestions` becomes non-empty — nothing about the input field itself changes
    // when they show up, so the field's own focus-driven scroll doesn't know to reveal
    // them; ask explicitly once they're actually part of the layout.
    val suggestionsBringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(form.tagSuggestions) {
        if (form.tagSuggestions.isNotEmpty()) suggestionsBringIntoViewRequester.bringIntoView()
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (form.tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                form.tags.forEach { tag ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UrsPill(text = tag)
                        UrsIconButton(
                            onClick = { viewModel.removeTag(tag) },
                            contentDescription = stringResource(R.string.note_remove_tag, tag),
                            imageVector = Icons.Filled.Close,
                        )
                    }
                }
            }
        }
        UrsTextField(
            value = form.tagInput,
            onValueChange = viewModel::setTagInput,
            label = stringResource(R.string.note_field_tags),
            supportingText = stringResource(R.string.note_tags_add_hint),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.addTag(form.tagInput) }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (form.tagSuggestions.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.bringIntoViewRequester(suggestionsBringIntoViewRequester),
            ) {
                form.tagSuggestions.forEach { suggestion ->
                    UrsCard(modifier = Modifier.fillMaxWidth().clickable { viewModel.addTag(suggestion) }) {
                        UrsText(suggestion, style = UrsTheme.typography.body)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteConfirmSheet(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.note_delete_confirm_title), style = UrsTheme.typography.cardTitle)
        UrsText(
            stringResource(R.string.note_delete_confirm_body),
            style = UrsTheme.typography.body,
            color = UrsTheme.colors.onSurfaceMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsOutlinedButton(text = stringResource(R.string.cancel), onClick = onCancel, modifier = Modifier.weight(1f))
            UrsButton(text = stringResource(R.string.note_delete), onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Share icon that drops a two-item menu below itself. Built on [Popup] with
 * the same elevated-surface treatment as [ch.mcfx.urs.ui.components.UrsDropdownField]'s
 * option list rather than `material3.DropdownMenu`, which this app
 * deliberately doesn't pull in outside the date/time pickers.
 */
@Composable
private fun NoteExportMenu(
    onShareAsText: () -> Unit,
    onCreateCalendarEvent: () -> Unit,
) {
    val colors = UrsTheme.colors
    val shape = RoundedCornerShape(Radius.row)
    var expanded by remember { mutableStateOf(false) }
    val dropBelowPx = with(LocalDensity.current) { 48.dp.roundToPx() }

    Box {
        UrsIconButton(
            onClick = { expanded = true },
            contentDescription = stringResource(R.string.note_share),
            imageVector = Icons.Filled.Share,
        )
        if (expanded) {
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, dropBelowPx),
                onDismissRequest = { expanded = false },
            ) {
                Column(
                    modifier = Modifier
                        .shadow(
                            elevation = 10.dp,
                            shape = shape,
                            ambientColor = colors.shadowColor.copy(alpha = colors.shadowAlpha),
                            spotColor = colors.shadowColor.copy(alpha = colors.shadowAlpha),
                        )
                        .clip(shape)
                        .background(colors.surface)
                        .then(if (colors.border.alpha > 0f) Modifier.border(1.dp, colors.border, shape) else Modifier)
                        .padding(vertical = Spacing.xs),
                ) {
                    NoteExportMenuItem(stringResource(R.string.note_export_share_text)) {
                        expanded = false
                        onShareAsText()
                    }
                    NoteExportMenuItem(stringResource(R.string.note_export_calendar_event)) {
                        expanded = false
                        onCreateCalendarEvent()
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteExportMenuItem(text: String, onClick: () -> Unit) {
    UrsText(
        text = text,
        style = UrsTheme.typography.body,
        color = UrsTheme.colors.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
    )
}

/**
 * Builds title + blank line + plain-text content, followed by a footer line
 * with the reminder date and time when the note has a reminder set, and
 * hands it to the system share sheet.
 */
private fun shareNoteAsText(context: Context, form: NoteDetailFormState) {
    val body = buildString {
        append(form.title)
        append("\n\n")
        append(noteMarkupToPlainText(form.content))
        if (form.reminderMillis != null) {
            append("\n\n—\n")
            append(
                context.getString(
                    R.string.note_export_reminder_label,
                    "${form.reminderDate} ${form.reminderTime}",
                ),
            )
        }
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, form.title)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startExportActivity(Intent.createChooser(send, null))
}

/**
 * Opens the calendar app's unsaved "new event" editor pre-filled from the
 * note. Begins at the reminder time when one is set, otherwise the next full
 * hour, and runs for one hour.
 */
private fun createNoteCalendarEvent(context: Context, form: NoteDetailFormState) {
    val begin = form.reminderMillis ?: LocalDateTime.now()
        .truncatedTo(ChronoUnit.HOURS)
        .plusHours(1)
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
    val insert = Intent(Intent.ACTION_INSERT).apply {
        data = CalendarContract.Events.CONTENT_URI
        putExtra(CalendarContract.Events.TITLE, form.title)
        putExtra(CalendarContract.Events.DESCRIPTION, noteMarkupToPlainText(form.content))
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 60L * 60L * 1000L)
    }
    context.startExportActivity(insert)
}

private fun Context.startExportActivity(intent: Intent) {
    runCatching { startActivity(intent) }
        .onFailure { Toast.makeText(this, R.string.note_export_no_app, Toast.LENGTH_SHORT).show() }
}
