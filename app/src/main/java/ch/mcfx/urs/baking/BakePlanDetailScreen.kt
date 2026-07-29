package ch.mcfx.urs.baking

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.BakingRepository
import ch.mcfx.urs.data.bakingTemplatesByKey
import ch.mcfx.urs.data.local.BakePlanStepEntity
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsCheckbox
import ch.mcfx.urs.ui.components.UrsDateField
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTimeField
import ch.mcfx.urs.ui.components.ursScreenContentPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val StepTimeFormat = DateTimeFormatter.ofPattern("EEE HH:mm")

@Composable
fun BakePlanDetailScreen(
    planId: String,
    viewModel: BakePlanDetailViewModel = viewModel(factory = BakePlanDetailViewModel.factory(planId)),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snoozeStep by viewModel.showSnoozeFor.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            BakePlanDetailUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))
            BakePlanDetailUiState.NotFound -> UrsText(
                stringResource(R.string.baking_plan_not_found),
                modifier = Modifier.align(Alignment.Center),
                color = UrsTheme.colors.onSurfaceMuted,
            )
            is BakePlanDetailUiState.Data -> Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.l),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    UrsText(
                        bakingTemplatesByKey[state.plan.templateKey]?.name ?: state.plan.templateKey,
                        style = UrsTheme.typography.screenTitle,
                        color = UrsTheme.colors.accent,
                    )
                    if (state.plan.status == BakingRepository.STATUS_ACTIVE) {
                        UrsText(
                            stringResource(R.string.baking_cancel_plan),
                            style = UrsTheme.typography.body,
                            color = UrsTheme.colors.onSurfaceMuted,
                            modifier = Modifier.clickable(onClick = viewModel::cancelPlan),
                        )
                    }
                }
                StepList(state.steps, onToggleDone = viewModel::toggleStepDone, onSnooze = viewModel::openSnooze)
            }
        }
    }

    snoozeStep?.let { step ->
        UrsBottomSheet(onDismissRequest = viewModel::closeSnooze) {
            SnoozeForm(step = step, onConfirm = viewModel::confirmSnooze, onCancel = viewModel::closeSnooze)
        }
    }
}

@Composable
private fun StepList(
    steps: List<BakePlanStepEntity>,
    onToggleDone: (BakePlanStepEntity) -> Unit,
    onSnooze: (BakePlanStepEntity) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ursScreenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(steps, key = { it.id }) { step ->
            UrsCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        UrsCheckbox(checked = step.doneAtMillis != null, onCheckedChange = { onToggleDone(step) })
                        Column {
                            UrsText(step.label, style = UrsTheme.typography.cardTitle)
                            UrsText(
                                Instant.ofEpochMilli(step.snoozedAtMillis ?: step.plannedAtMillis)
                                    .atZone(ZoneId.systemDefault())
                                    .format(StepTimeFormat),
                                style = UrsTheme.typography.body,
                                color = UrsTheme.colors.onSurfaceMuted,
                            )
                        }
                    }
                    if (step.doneAtMillis == null) {
                        UrsText(
                            "+",
                            style = UrsTheme.typography.cardTitle,
                            color = UrsTheme.colors.accent,
                            modifier = Modifier.clickable { onSnooze(step) }.padding(Spacing.s),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SnoozeForm(step: BakePlanStepEntity, onConfirm: (Long) -> Unit, onCancel: () -> Unit) {
    val initial = remember(step.id) {
        Instant.ofEpochMilli(step.snoozedAtMillis ?: step.plannedAtMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    }
    var date by remember(step.id) { mutableStateOf(initial.toLocalDate().toString()) }
    var time by remember(step.id) { mutableStateOf(initial.toLocalTime().toString().take(5)) }

    Column(modifier = Modifier.padding(horizontal = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        UrsText(stringResource(R.string.baking_snooze_title, step.label), style = UrsTheme.typography.cardTitle)
        UrsDateField(value = date, onValueChange = { date = it }, label = stringResource(R.string.baking_anchor_date))
        UrsTimeField(value = time, onValueChange = { time = it }, label = stringResource(R.string.baking_anchor_time))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsOutlinedButton(text = stringResource(R.string.cancel), onClick = onCancel, modifier = Modifier.weight(1f))
            UrsButton(
                text = stringResource(R.string.baking_snooze_submit),
                onClick = {
                    val millis = LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    onConfirm(millis)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
