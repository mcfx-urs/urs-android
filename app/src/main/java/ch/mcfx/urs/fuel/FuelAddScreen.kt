package ch.mcfx.urs.fuel

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.CurrencyEntity
import ch.mcfx.urs.data.local.VehicleEntity
import ch.mcfx.urs.location.LOCATION_PERMISSIONS
import ch.mcfx.urs.location.hasLocationPermission
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCheckbox
import ch.mcfx.urs.ui.components.UrsDateField
import ch.mcfx.urs.ui.components.UrsDiscardChangesDialog
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.components.ursFormScrollPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing
import java.util.Locale

// No "error" role in the design system's palette yet (see Color.kt) — mirrors
// the same local-constant pattern already used in FuelStationsScreen /
// ProductListScreen, rather than waiting on the broader token set.
private val FormErrorColor = Color(0xFFD64545)

private fun formatDistanceKm(km: Double): String = String.format(Locale.US, "%.1f km", km)

// A dedicated full screen rather than the list's old bottom sheet —
// reachable both from the Fuel hub's "Add Fill-up" tile and from
// the Fill-ups list's FAB. Reuses FuelViewModel's existing form/submit
// logic unchanged; only the presentation (screen vs. sheet) differs.
@Composable
fun FuelAddScreen(
    onDone: () -> Unit,
    /** Null creates a new fill; set edits that existing local row (see [FuelViewModel.openFormForEdit]). */
    fillId: Long? = null,
    viewModel: FuelViewModel = viewModel(factory = FuelViewModel.Factory),
    onDirtyChanged: (Boolean) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { if (fillId != null) viewModel.openFormForEdit(fillId) else viewModel.openForm() }
    LaunchedEffect(formState.dirty) { onDirtyChanged(formState.dirty) }

    var hasOpened by remember { mutableStateOf(false) }
    LaunchedEffect(showForm) {
        if (showForm) {
            hasOpened = true
        } else if (hasOpened) {
            onDone()
        }
    }

    var confirmingDiscard by remember { mutableStateOf(false) }
    BackHandler(enabled = formState.dirty) { confirmingDiscard = true }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            FuelUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))

            is FuelUiState.Data -> if (showForm) {
                FillForm(
                    form = formState,
                    vehicles = state.vehicles,
                    pickerStations = state.pickerStations,
                    currencies = state.currencies,
                    viewModel = viewModel,
                )
            } else if (fillId != null) {
                // openFormForEdit is still loading the fill to pre-fill —
                // matches WorkTimeAddScreen's identical loading state.
                UrsProgressIndicator(Modifier.align(Alignment.Center))
            }
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
}

@Composable
private fun FillForm(
    form: FillFormState,
    vehicles: List<VehicleEntity>,
    pickerStations: List<StationPickerOption>,
    currencies: List<CurrencyEntity>,
    viewModel: FuelViewModel,
) {
    val context = LocalContext.current
    var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(context)) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasLocationPermission = result.values.any { it }
        if (hasLocationPermission) viewModel.captureLocation()
    }

    val focusManager = LocalFocusManager.current
    // "Next" on every field's IME action, instead of the default tick/done —
    // one shared instance since the behavior (move to the next field) is
    // identical everywhere in this form. Matches WorkTimeAddScreen.
    val nextFieldAction = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })

    Column(
        modifier = Modifier
            .ursFormScrollPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsDropdownField(
            label = stringResource(R.string.fill_vehicle),
            options = vehicles,
            selectedLabel = form.vehicle?.let { "${it.brand} ${it.model}" },
            optionLabel = { "${it.brand} ${it.model}" },
            onSelect = viewModel::selectVehicle,
            modifier = Modifier.fillMaxWidth(),
        )

        // Hidden once editing an already-synced fill — PUT /api/v1/fill/{id}
        // has no ad-hoc-station-creation branch, so a known station is
        // required at that point (see FillFormState.editingIsSynced).
        if (!form.editingIsSynced) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrsText(stringResource(R.string.fill_no_station), style = UrsTheme.typography.body)
                UrsCheckbox(
                    checked = form.useGps,
                    onCheckedChange = { checked ->
                        viewModel.setUseGps(checked)
                        // Requested lazily, only once the user actually picks
                        // "no station" — not upfront at launch.
                        if (checked && hasLocationPermission) viewModel.captureLocation()
                    },
                )
            }
        }

        if (form.useGps) {
            if (!hasLocationPermission) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UrsText(
                        stringResource(R.string.fill_location_permission_needed),
                        style = UrsTheme.typography.body,
                        color = FormErrorColor,
                        modifier = Modifier.weight(1f),
                    )
                    UrsOutlinedButton(
                        text = stringResource(R.string.fill_grant),
                        onClick = { locationPermissionLauncher.launch(LOCATION_PERMISSIONS) },
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UrsText(
                        when {
                            form.capturingLocation -> stringResource(R.string.fill_capturing_location)
                            form.gpsLatitude != null && form.gpsLongitude != null ->
                                stringResource(R.string.fill_location_captured, form.gpsLatitude, form.gpsLongitude)
                            else -> stringResource(R.string.fill_location_missing)
                        },
                        style = UrsTheme.typography.body,
                        color = UrsTheme.colors.onSurfaceMuted,
                        modifier = Modifier.weight(1f),
                    )
                    UrsOutlinedButton(
                        text = stringResource(R.string.fill_capture_location),
                        onClick = viewModel::captureLocation,
                        enabled = !form.capturingLocation,
                    )
                }
            }
        } else {
            UrsDropdownField(
                label = stringResource(R.string.fill_station),
                options = pickerStations,
                selectedLabel = form.station?.name,
                optionLabel = { it.station.name },
                // Right-aligned distance column, formatted "%.1f km" — always
                // exactly one digit after the decimal point and a constant
                // " km" suffix, so the decimal point lands at the same offset
                // from the row's right edge regardless of the integer part's
                // digit count (Roboto's tabular figures keep every digit the
                // same advance width) — no manual padding needed for the
                // columns to visually line up.
                optionTrailingLabel = { it.distanceKm?.let(::formatDistanceKm) },
                onSelect = { viewModel.selectStation(it.station) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (form.editingIsSynced) {
                UrsText(
                    stringResource(R.string.fill_station_locked),
                    style = UrsTheme.typography.caption,
                    color = UrsTheme.colors.onSurfaceMuted,
                )
            }
        }

        // A container has no odometer/consumption meaning — its own
        // "fill-up" is a container purchase, not a drivable-vehicle refuel
        // (see mcfx-urs/urs-android#87).
        if (form.vehicle?.isContainer != true) {
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
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsTextField(
                value = form.pricePerLiter,
                onValueChange = viewModel::setPricePerLiter,
                label = stringResource(R.string.fill_price_per_liter),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = nextFieldAction,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            UrsTextField(
                value = form.liters,
                onValueChange = viewModel::setLiters,
                label = stringResource(R.string.fill_liters),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                keyboardActions = nextFieldAction,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        if (form.vehicle?.isContainer != true) {
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
        }

        UrsDropdownField(
            label = stringResource(R.string.fill_currency),
            options = currencies,
            selectedLabel = form.currencyCode,
            optionLabel = { it.code },
            onSelect = { viewModel.setCurrencyCode(it.code) },
            modifier = Modifier.fillMaxWidth(),
        )

        UrsDateField(
            value = form.date,
            onValueChange = viewModel::setDate,
            label = stringResource(R.string.fill_date),
            modifier = Modifier.fillMaxWidth(),
        )

        form.totalCost?.let {
            UrsText(
                stringResource(R.string.fill_total, it),
                style = UrsTheme.typography.cardTitle,
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
