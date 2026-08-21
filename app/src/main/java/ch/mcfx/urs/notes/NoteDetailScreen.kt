package ch.mcfx.urs.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.NoteRepository
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsCheckbox
import ch.mcfx.urs.ui.components.UrsDateField
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.components.UrsTimeField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

// No "error" role in the design system's palette yet — same local-constant
// pattern already used elsewhere (e.g. WorkTimeAddScreen).
private val FormErrorColor = Color(0xFFD64545)

@Composable
fun NoteDetailScreen(
    onDone: () -> Unit,
    /** Null creates a new note; set edits that note (its publicId, see [ch.mcfx.urs.data.local.publicId]). */
    noteId: String? = null,
    viewModel: NoteDetailViewModel = viewModel(factory = NoteDetailViewModel.Factory),
) {
    val form by viewModel.formState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { if (noteId != null) viewModel.loadForEdit(noteId) else viewModel.startNew() }
    LaunchedEffect(form.finished) { if (form.finished) onDone() }

    var confirmingDelete by remember { mutableStateOf(false) }

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
}

@Composable
private fun NoteForm(form: NoteDetailFormState, viewModel: NoteDetailViewModel, onRequestDelete: () -> Unit) {
    val nextFieldAction = KeyboardActions(onNext = {})
    val isEditing = form.localId != null

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl)
            .padding(top = Spacing.xl)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(
            stringResource(if (isEditing) R.string.note_detail_title_edit else R.string.note_detail_title_new),
            style = UrsTheme.typography.screenTitle,
        )

        UrsTextField(
            value = form.title,
            onValueChange = viewModel::setTitle,
            label = stringResource(R.string.note_field_title),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = nextFieldAction,
            modifier = Modifier.fillMaxWidth(),
        )

        UrsTextField(
            value = form.content,
            onValueChange = viewModel::setContent,
            label = stringResource(R.string.note_field_content),
            singleLine = false,
            minLines = 4,
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
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
