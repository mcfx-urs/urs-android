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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.bakingTemplatesByKey
import ch.mcfx.urs.data.local.BakePlanEntity
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsDateField
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTimeField
import ch.mcfx.urs.ui.components.ursScreenContentPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FabIconStyle = TextStyle(fontSize = 28.sp)
private val PlanDisplayFormat = DateTimeFormatter.ofPattern("EEE, d MMM · HH:mm")

@Composable
fun BakingHubScreen(
    onOpenPlan: (BakePlanEntity) -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: BakingHubViewModel = viewModel(factory = BakingHubViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            BakingHubUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))
            is BakingHubUiState.Data -> Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.l),
                    horizontalArrangement = Arrangement.End,
                ) {
                    UrsText(
                        text = stringResource(R.string.baking_history),
                        style = UrsTheme.typography.body,
                        color = UrsTheme.colors.accent,
                        modifier = Modifier.clickable(onClick = onOpenHistory),
                    )
                }
                PlanList(state.plans, onOpenPlan)
            }
        }

        UrsFab(
            onClick = viewModel::openCreateForm,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
        ) {
            UrsText(text = "+", style = FabIconStyle, color = UrsTheme.colors.onAccent)
        }
    }

    if (showForm) {
        UrsBottomSheet(onDismissRequest = viewModel::closeForm) {
            CreatePlanForm(
                formState = formState,
                onDateChange = viewModel::setDate,
                onTimeChange = viewModel::setTime,
                onSubmit = viewModel::submit,
            )
        }
    }
}

@Composable
private fun PlanList(plans: List<BakePlanEntity>, onOpenPlan: (BakePlanEntity) -> Unit) {
    if (plans.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            UrsText(
                stringResource(R.string.baking_empty),
                modifier = Modifier.align(Alignment.Center),
                color = UrsTheme.colors.onSurfaceMuted,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = ursScreenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(plans, key = { it.id }) { plan ->
            UrsCard(modifier = Modifier.fillMaxWidth().clickable { onOpenPlan(plan) }) {
                UrsText(plan.templateName(), style = UrsTheme.typography.cardTitle)
                UrsText(
                    Instant.ofEpochMilli(plan.anchorAtMillis).atZone(ZoneId.systemDefault()).format(PlanDisplayFormat),
                    style = UrsTheme.typography.body,
                    color = UrsTheme.colors.onSurfaceMuted,
                )
            }
        }
    }
}

private fun BakePlanEntity.templateName(): String = bakingTemplatesByKey[templateKey]?.name ?: templateKey

@Composable
private fun CreatePlanForm(
    formState: BakingPlanFormState,
    onDateChange: (String) -> Unit,
    onTimeChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.baking_create_title), style = UrsTheme.typography.cardTitle)
        UrsText(
            stringResource(R.string.baking_template_sourdough),
            style = UrsTheme.typography.body,
            color = UrsTheme.colors.onSurfaceMuted,
        )
        UrsDateField(value = formState.date, onValueChange = onDateChange, label = stringResource(R.string.baking_anchor_date))
        UrsTimeField(value = formState.time, onValueChange = onTimeChange, label = stringResource(R.string.baking_anchor_time))
        if (formState.submitFailed) {
            UrsText(stringResource(R.string.baking_create_failed), color = UrsTheme.colors.onSurfaceMuted)
        }
        UrsButton(
            text = stringResource(R.string.baking_create_submit),
            onClick = onSubmit,
            enabled = formState.isValid && !formState.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
