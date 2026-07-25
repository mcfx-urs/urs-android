package ch.mcfx.urs.service

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import ch.mcfx.urs.data.ServiceCategory
import ch.mcfx.urs.data.local.CurrencyEntity
import ch.mcfx.urs.data.local.VehicleEntity
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCheckbox
import ch.mcfx.urs.ui.components.UrsDateField
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsFilterChip
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

private val FormErrorColor = Color(0xFFD64545)

@Composable
fun ServiceAddScreen(
    onDone: () -> Unit,
    /** Null creates a new service entry; set edits that existing local row (see [ServiceViewModel.openFormForEdit]). */
    serviceId: Long? = null,
    viewModel: ServiceViewModel = viewModel(factory = ServiceViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { if (serviceId != null) viewModel.openFormForEdit(serviceId) else viewModel.openForm() }

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
            ServiceUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))
            is ServiceUiState.Data -> if (showForm) {
                ServiceForm(
                    form = formState,
                    vehicles = state.vehicles,
                    currencies = state.currencies,
                    viewModel = viewModel,
                )
            } else if (serviceId != null) {
                // openFormForEdit is still loading the entry to pre-fill —
                // matches FuelAddScreen's identical loading state.
                UrsProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun ServiceForm(
    form: ServiceFormState,
    vehicles: List<VehicleEntity>,
    currencies: List<CurrencyEntity>,
    viewModel: ServiceViewModel,
) {
    val focusManager = LocalFocusManager.current
    // "Next" on every field's IME action, instead of the default tick/done —
    // one shared instance since the behavior (move to the next field) is
    // identical everywhere in this form. Matches WorkTimeAddScreen.
    val nextFieldAction = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl)
            .padding(top = Spacing.xl)
            // Edge-to-edge means this screen draws behind the system nav bar
            // and (while a field is focused) the on-screen keyboard, unless
            // told otherwise — union rather than a separate imePadding() so
            // the two inset paddings don't stack additively while the
            // keyboard covers the nav bar (same reasoning as UrsBottomSheet).
            // Without this, the scrollable area's bottom never grows to
            // account for the keyboard, so fields/the Save button below the
            // focused field stay hidden behind it with no way to scroll them
            // into view.
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsDropdownField(
            label = stringResource(R.string.service_vehicle),
            options = vehicles,
            selectedLabel = form.vehicle?.let { "${it.brand} ${it.model}" },
            optionLabel = { "${it.brand} ${it.model}" },
            onSelect = viewModel::selectVehicle,
            modifier = Modifier.fillMaxWidth(),
        )

        UrsDateField(
            value = form.date,
            onValueChange = viewModel::setDate,
            label = stringResource(R.string.service_date),
            modifier = Modifier.fillMaxWidth(),
        )

        UrsTextField(
            value = form.odometer,
            onValueChange = viewModel::setOdometer,
            label = stringResource(R.string.service_odometer),
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
            UrsText(stringResource(R.string.service_diy), style = UrsTheme.typography.body)
            UrsCheckbox(checked = form.isDiy, onCheckedChange = viewModel::setIsDiy)
        }

        if (!form.isDiy) {
            UrsTextField(
                value = form.provider,
                onValueChange = viewModel::setProvider,
                label = stringResource(R.string.service_provider),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = nextFieldAction,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsTextField(
                value = form.costAmount,
                onValueChange = viewModel::setCostAmount,
                label = stringResource(R.string.service_cost),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = nextFieldAction,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            UrsDropdownField(
                label = stringResource(R.string.service_currency),
                options = currencies,
                selectedLabel = form.currencyCode,
                optionLabel = { it.code },
                onSelect = { viewModel.setCurrencyCode(it.code) },
                modifier = Modifier.weight(1f),
            )
        }

        UrsTextField(
            value = form.notes,
            onValueChange = viewModel::setNotes,
            label = stringResource(R.string.service_notes),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = nextFieldAction,
            modifier = Modifier.fillMaxWidth(),
        )

        UrsText(stringResource(R.string.service_categories), style = UrsTheme.typography.cardTitle)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            ServiceCategory.entries.forEach { category ->
                UrsFilterChip(
                    label = stringResource(category.labelRes),
                    selected = category.code in form.selectedFixedCategories,
                    onClick = { viewModel.toggleCategory(category.code) },
                )
            }
        }

        form.customTags.forEachIndexed { index, tag ->
            UrsTextField(
                value = tag,
                onValueChange = { viewModel.setCustomTag(index, it) },
                label = stringResource(R.string.service_custom_tag_hint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = nextFieldAction,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
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
