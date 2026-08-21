package ch.mcfx.urs.worktime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.computeTotals
import ch.mcfx.urs.data.dailyHoursWorked
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCheckbox
import ch.mcfx.urs.ui.components.UrsDateField
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.components.ursFormScrollPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

// No "error" role in the design system's palette yet — same local-constant
// pattern already used in FuelAddScreen.
private val FormErrorColor = Color(0xFFD64545)

@Composable
fun WorkTimeAddScreen(
    onDone: () -> Unit,
    /** Null creates a new entry; set edits that existing local row (see [WorkTimeViewModel.openFormForEdit]). */
    entryId: Long? = null,
    viewModel: WorkTimeViewModel = viewModel(factory = WorkTimeViewModel.Factory),
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userDefaultTargetHours = (uiState as? WorkTimeUiState.Data)?.userDefaultTargetHours

    LaunchedEffect(Unit) { if (entryId != null) viewModel.openFormForEdit(entryId) else viewModel.openForm() }

    var hasOpened by remember { mutableStateOf(false) }
    LaunchedEffect(showForm) {
        if (showForm) {
            hasOpened = true
        } else if (hasOpened) {
            onDone()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showForm) {
            EntryForm(form = formState, userDefaultTargetHours = userDefaultTargetHours, viewModel = viewModel)
        } else {
            UrsProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun EntryForm(form: WorkTimeFormState, userDefaultTargetHours: String?, viewModel: WorkTimeViewModel) {
    val focusManager = LocalFocusManager.current
    // "Next" on every field's IME action, instead of the default tick/done —
    // one shared instance since the behavior (move to the next field) is
    // identical everywhere in this form.
    val nextFieldAction = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })

    // Keyed by break id (not list position) so a request survives breaks
    // being added/removed elsewhere in the list — see pendingFocusBreakId.
    val breakStartFocusRequesters = remember { mutableStateMapOf<Long, FocusRequester>() }
    var pendingFocusBreakId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(pendingFocusBreakId) {
        val id = pendingFocusBreakId ?: return@LaunchedEffect
        breakStartFocusRequesters[id]?.requestFocus()
        pendingFocusBreakId = null
    }

    Column(
        modifier = Modifier
            .ursFormScrollPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        val isEditing = form.editingEntryId != null
        UrsDateField(
            value = form.date,
            // The backend's update endpoint doesn't support moving an entry
            // to a different day — disabled once editing an existing entry
            // rather than letting the dialog open just to discard the pick.
            onValueChange = viewModel::setDate,
            label = stringResource(R.string.worktime_date),
            enabled = !isEditing,
            modifier = Modifier.fillMaxWidth(),
        )
        if (isEditing) {
            UrsText(
                text = stringResource(R.string.worktime_date_locked),
                style = UrsTheme.typography.caption,
                color = UrsTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(start = Spacing.m),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsTextField(
                value = timeFieldValue(form.workStart),
                onValueChange = { viewModel.setWorkStart(formatTimeInput(it).text) },
                label = stringResource(R.string.worktime_work_start),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = nextFieldAction,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            UrsTextField(
                value = timeFieldValue(form.workEnd),
                onValueChange = { viewModel.setWorkEnd(formatTimeInput(it).text) },
                label = stringResource(R.string.worktime_work_end),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                keyboardActions = nextFieldAction,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        UrsTextField(
            value = form.targetDailyHours,
            onValueChange = viewModel::setTargetDailyHours,
            label = stringResource(R.string.worktime_target_hours),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            keyboardActions = nextFieldAction,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsText(
                text = stringResource(R.string.worktime_paid_break),
                style = UrsTheme.typography.body,
                modifier = Modifier.weight(1f).padding(end = Spacing.m),
            )
            UrsCheckbox(checked = form.paidBreak, onCheckedChange = viewModel::setPaidBreak)
        }

        UrsText(stringResource(R.string.worktime_breaks_title), style = UrsTheme.typography.cardTitle)

        form.breaks.forEach { breakDraft ->
            val startFocusRequester = remember(breakDraft.id) { FocusRequester() }
            SideEffect { breakStartFocusRequesters[breakDraft.id] = startFocusRequester }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrsTextField(
                    value = timeFieldValue(breakDraft.startTime),
                    onValueChange = { viewModel.setBreakStart(breakDraft.id, formatTimeInput(it).text) },
                    label = stringResource(R.string.worktime_break_start),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = nextFieldAction,
                    focusRequester = startFocusRequester,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                UrsTextField(
                    value = timeFieldValue(breakDraft.endTime),
                    onValueChange = { viewModel.setBreakEnd(breakDraft.id, formatTimeInput(it).text) },
                    label = stringResource(R.string.worktime_break_end),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    keyboardActions = nextFieldAction,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                UrsIconButton(
                    onClick = {
                        breakStartFocusRequesters.remove(breakDraft.id)
                        viewModel.removeBreak(breakDraft.id)
                    },
                    contentDescription = stringResource(R.string.worktime_remove_break),
                    imageVector = Icons.Filled.Close,
                    // Excluded from "Next" traversal — a non-text button in
                    // the middle of the tab order would otherwise strand
                    // focus there with no keyboard action to press.
                    modifier = Modifier.focusProperties { canFocus = false },
                )
            }
        }

        UrsOutlinedButton(
            text = stringResource(R.string.worktime_add_break),
            // Saves the caller a tap: the newly added break's start-time
            // field is focused immediately, works for every add since it's
            // keyed by the new break's own id, not by list position.
            onClick = { pendingFocusBreakId = viewModel.addBreak() },
            modifier = Modifier.fillMaxWidth(),
        )

        // Live preview of the same totals the monthly overview shows after
        // saving — reuses dailyHoursWorked()/computeTotals() unchanged
        // (WorkTimeCalculations.kt), just fed from the in-progress form
        // instead of a saved entity. Hidden until both times parse as a full
        // "HH:mm" (dailyHoursWorked returns null otherwise) rather than
        // showing a partial/misleading number while typing.
        val totals = remember(form.workStart, form.workEnd, form.paidBreak, form.breaks, form.targetDailyHours, userDefaultTargetHours) {
            computeTotals(
                dailyHoursWorked(
                    workStart = "${form.workStart}:00",
                    workEnd = "${form.workEnd}:00",
                    paidBreak = form.paidBreak,
                    breaks = form.breaks.map { "${it.startTime}:00" to "${it.endTime}:00" },
                ),
                form.targetDailyHours,
                userDefaultTargetHours,
            )
        }
        totals.dailyTotalHours?.let { dailyTotal ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrsText(
                    stringResource(R.string.worktime_daily_total, formatHours(dailyTotal)),
                    style = UrsTheme.typography.cardTitle,
                )
                totals.overUndertimeHours?.let {
                    UrsText(
                        formatSignedHours(it),
                        style = UrsTheme.typography.body,
                        color = if (it < 0) FormErrorColor else UrsTheme.colors.onSurfaceMuted,
                    )
                }
            }
        }

        if (form.submitFailed) {
            UrsText(
                stringResource(R.string.error_save),
                color = FormErrorColor,
                style = UrsTheme.typography.body,
            )
        }

        UrsButton(
            text = stringResource(if (form.submitting) R.string.saving else R.string.save),
            onClick = viewModel::submit,
            enabled = form.isValid && !form.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
