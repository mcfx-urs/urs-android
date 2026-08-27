package ch.mcfx.urs.fuel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.remote.FillingStationDto
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.components.ursScreenContentPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

// Same reasoning as FuelScreen's FabIconStyle — the type scale has no "big
// FAB glyph" size of its own.
private val FabIconStyle = TextStyle(fontSize = 28.sp)

// No "error" role in the design system's palette yet (see Color.kt) — this
// mirrors ProductListScreen's own local warning-color constants: already
// decided, doesn't need to wait on the broader token set.
private val FormErrorColor = Color(0xFFD64545)

@Composable
fun FuelStationsScreen(
    viewModel: StationsViewModel = viewModel(factory = StationsViewModel.Factory),
    onOpenMapConfirm: () -> Unit = {},
    // Editing an existing station is gated to super users — the
    // backend rejects the PUT for anyone else too, this just hides the
    // affordance for callers who'd get a 403 anyway.
    isSuperUser: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()
    val pendingStations by viewModel.pendingStations.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.openMapConfirm.collect { onOpenMapConfirm() }
    }

    // Only a super user can act on the ad-hoc review queue, and the endpoint
    // is super-user-gated anyway — never fetch it for anyone else.
    LaunchedEffect(isSuperUser) {
        if (isSuperUser) viewModel.loadPendingStations()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            StationsUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))

            is StationsUiState.Error -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                UrsText(stringResource(R.string.error_load), style = UrsTheme.typography.body)
                Spacer(Modifier.height(Spacing.l))
                UrsButton(text = stringResource(R.string.retry), onClick = viewModel::load)
            }

            is StationsUiState.Data -> StationList(
                stations = state.stations,
                pendingStations = pendingStations,
                isSuperUser = isSuperUser,
                onEdit = viewModel::openFormForEdit,
            )
        }

        if (uiState is StationsUiState.Data) {
            UrsFab(
                onClick = viewModel::openForm,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
            ) {
                UrsText(text = "+", style = FabIconStyle, color = UrsTheme.colors.onAccent)
            }
        }
    }

    if (showForm) {
        UrsBottomSheet(onDismissRequest = viewModel::closeForm) {
            StationForm(form = formState, viewModel = viewModel, onOpenMapConfirm = onOpenMapConfirm)
        }
    }
}

@Composable
private fun StationList(
    stations: List<FillingStationDto>,
    pendingStations: List<FillingStationDto>,
    isSuperUser: Boolean,
    onEdit: (FillingStationDto) -> Unit,
) {
    val showPending = isSuperUser && pendingStations.isNotEmpty()

    if (stations.isEmpty() && !showPending) {
        Box(Modifier.fillMaxSize()) {
            UrsText(
                stringResource(R.string.stations_empty),
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
        if (showPending) {
            item(key = "pending_header") { SectionHeader(stringResource(R.string.stations_pending_review_title)) }
            items(pendingStations, key = { "pending_${it.id}" }) { station ->
                // Ad-hoc rows are always tap-to-edit (this section only shows for super users).
                StationRow(station = station, clickable = true, onEdit = onEdit)
            }
            if (stations.isNotEmpty()) {
                item(key = "all_header") { SectionHeader(stringResource(R.string.stations_all_title)) }
            }
        }
        items(stations, key = { it.id }) { station ->
            StationRow(station = station, clickable = isSuperUser, onEdit = onEdit)
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    UrsText(
        text = text,
        style = UrsTheme.typography.caption,
        color = UrsTheme.colors.onSurfaceMuted,
        modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.xs),
    )
}

@Composable
private fun StationRow(station: FillingStationDto, clickable: Boolean, onEdit: (FillingStationDto) -> Unit) {
    val rowModifier = if (clickable) {
        Modifier.fillMaxWidth().clickable { onEdit(station) }
    } else {
        Modifier.fillMaxWidth()
    }
    UrsCard(radius = Radius.row, modifier = rowModifier) {
        UrsText(station.name, style = UrsTheme.typography.cardTitle)
        if (station.address.isNotBlank()) {
            UrsText(
                station.address,
                style = UrsTheme.typography.body,
                color = UrsTheme.colors.onSurfaceMuted,
            )
        }
    }
}

@Composable
private fun StationForm(form: StationFormState, viewModel: StationsViewModel, onOpenMapConfirm: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(
            stringResource(if (form.editingId != null) R.string.station_edit else R.string.station_add),
            style = UrsTheme.typography.screenTitle,
        )

        UrsTextField(
            value = form.name,
            onValueChange = viewModel::setName,
            label = stringResource(R.string.station_name),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        UrsTextField(
            value = form.address,
            onValueChange = viewModel::setAddress,
            label = stringResource(R.string.station_address),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (form.latitude != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UrsText(stringResource(R.string.station_position_set), style = UrsTheme.typography.body)
                UrsOutlinedButton(text = stringResource(R.string.station_position_adjust), onClick = onOpenMapConfirm)
            }
        } else {
            UrsOutlinedButton(
                text = stringResource(if (form.geocoding) R.string.station_searching_position else R.string.station_search_position),
                onClick = viewModel::searchPosition,
                enabled = form.address.isNotBlank() && !form.geocoding,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (form.geocodeFailed) {
            UrsText(
                stringResource(R.string.station_position_not_found),
                color = FormErrorColor,
                style = UrsTheme.typography.body,
            )
            UrsOutlinedButton(
                text = stringResource(R.string.station_position_set_manually),
                onClick = onOpenMapConfirm,
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
