package ch.mcfx.urs.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.VehicleEntity
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCheckbox
import ch.mcfx.urs.ui.components.UrsDateField
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.components.ursFormScrollPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import java.util.Locale

private val FormErrorColor = Color(0xFFD64545)

/**
 * Pouring fuel from a container into a real vehicle's tank — see
 * [TransferViewModel]'s doc comment for why this is a separate screen from
 * [FuelAddScreen] rather than a mode within the same form.
 */
@Composable
fun TransferAddScreen(
    onDone: () -> Unit,
    viewModel: TransferViewModel = viewModel(factory = TransferViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
        when (val state = uiState) {
            TransferUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))
            is TransferUiState.Data -> if (showForm) {
                TransferForm(form = formState, containers = state.containers, destinations = state.destinations, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun TransferForm(
    form: TransferFormState,
    containers: List<VehicleEntity>,
    destinations: List<VehicleEntity>,
    viewModel: TransferViewModel,
) {
    val focusManager = LocalFocusManager.current
    val nextFieldAction = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })

    Column(
        modifier = Modifier.ursFormScrollPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (containers.isEmpty()) {
            UrsText(stringResource(R.string.transfer_no_containers), style = UrsTheme.typography.body)
            return@Column
        }

        UrsDropdownField(
            label = stringResource(R.string.transfer_source),
            options = containers,
            selectedLabel = form.source?.let { "${it.brand} ${it.model}" },
            optionLabel = { "${it.brand} ${it.model}" },
            onSelect = viewModel::selectSource,
            modifier = Modifier.fillMaxWidth(),
        )

        form.sourceStock?.let { stock ->
            UrsText(
                stringResource(
                    R.string.transfer_current_stock,
                    String.format(Locale.US, "%.2f", stock.liters),
                    String.format(Locale.US, "%.2f", stock.averagePricePerLiter),
                ),
                style = UrsTheme.typography.caption,
                color = UrsTheme.colors.onSurfaceMuted,
            )
        }

        UrsDropdownField(
            label = stringResource(R.string.transfer_destination),
            options = destinations,
            selectedLabel = form.destination?.let { "${it.brand} ${it.model}" },
            optionLabel = { "${it.brand} ${it.model}" },
            onSelect = viewModel::selectDestination,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsTextField(
                value = form.weightBeforeKg,
                onValueChange = viewModel::setWeightBeforeKg,
                label = stringResource(R.string.transfer_weight_before_kg),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = nextFieldAction,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            UrsTextField(
                value = form.weightAfterKg,
                onValueChange = viewModel::setWeightAfterKg,
                label = stringResource(R.string.transfer_weight_after_kg),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = nextFieldAction,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        if (form.source != null && form.weighedKg != null && form.sourceDensityKgPerLiter == null) {
            UrsText(
                stringResource(R.string.transfer_density_missing),
                color = FormErrorColor,
                style = UrsTheme.typography.body,
            )
        }

        form.liters?.let { liters ->
            UrsText(
                stringResource(R.string.transfer_liters, String.format(Locale.US, "%.2f", liters)),
                style = UrsTheme.typography.body,
            )
        }

        UrsTextField(
            value = form.odometer,
            onValueChange = viewModel::setOdometer,
            label = stringResource(R.string.fill_odometer),
            supportingText = form.lastOdometer?.let { stringResource(R.string.fill_last_odometer, it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            keyboardActions = nextFieldAction,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsText(stringResource(R.string.fill_is_full_tank), style = UrsTheme.typography.body)
            UrsCheckbox(
                checked = form.isFullTank,
                onCheckedChange = viewModel::setIsFullTank,
            )
        }

        UrsDateField(
            value = form.date,
            onValueChange = viewModel::setDate,
            label = stringResource(R.string.fill_date),
            modifier = Modifier.fillMaxWidth(),
        )

        if (form.submitFailed) {
            UrsText(
                text = stringResource(R.string.error_save),
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
