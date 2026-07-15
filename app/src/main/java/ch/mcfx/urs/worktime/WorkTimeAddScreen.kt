package ch.mcfx.urs.worktime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close

// No "error" role in the design system's palette yet — same local-constant
// pattern already used in FuelAddScreen.
private val FormErrorColor = Color(0xFFD64545)

@Composable
fun WorkTimeAddScreen(
    onDone: () -> Unit,
    viewModel: WorkTimeViewModel = viewModel(factory = WorkTimeViewModel.Factory),
) {
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.openForm() }

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
            EntryForm(form = formState, viewModel = viewModel)
        } else {
            UrsProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun EntryForm(form: WorkTimeFormState, viewModel: WorkTimeViewModel) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).padding(vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsTextField(
            value = form.date,
            onValueChange = viewModel::setDate,
            label = stringResource(R.string.worktime_date),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsTextField(
                value = timeFieldValue(form.workStart),
                onValueChange = { viewModel.setWorkStart(formatTimeInput(it).text) },
                label = stringResource(R.string.worktime_work_start),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            UrsTextField(
                value = timeFieldValue(form.workEnd),
                onValueChange = { viewModel.setWorkEnd(formatTimeInput(it).text) },
                label = stringResource(R.string.worktime_work_end),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        UrsTextField(
            value = form.targetDailyHours,
            onValueChange = viewModel::setTargetDailyHours,
            label = stringResource(R.string.worktime_target_hours),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        UrsText(stringResource(R.string.worktime_breaks_title), style = UrsTheme.typography.cardTitle)

        form.breaks.forEach { breakDraft ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrsTextField(
                    value = timeFieldValue(breakDraft.startTime),
                    onValueChange = { viewModel.setBreakStart(breakDraft.id, formatTimeInput(it).text) },
                    label = stringResource(R.string.worktime_break_start),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                UrsTextField(
                    value = timeFieldValue(breakDraft.endTime),
                    onValueChange = { viewModel.setBreakEnd(breakDraft.id, formatTimeInput(it).text) },
                    label = stringResource(R.string.worktime_break_end),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                UrsIconButton(
                    onClick = { viewModel.removeBreak(breakDraft.id) },
                    contentDescription = stringResource(R.string.worktime_remove_break),
                    imageVector = Icons.Filled.Close,
                )
            }
        }

        UrsOutlinedButton(
            text = stringResource(R.string.worktime_add_break),
            onClick = viewModel::addBreak,
            modifier = Modifier.fillMaxWidth(),
        )

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
