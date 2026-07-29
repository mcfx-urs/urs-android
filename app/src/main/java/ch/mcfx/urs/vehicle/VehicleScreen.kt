package ch.mcfx.urs.vehicle

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.VehicleEntity
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.ursScreenContentPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

private val FabIconStyle = TextStyle(fontSize = 28.sp)

// Same "no error role in the palette yet" local-constant pattern used by
// FuelScreen/FuelAddScreen.
private val FormErrorColor = Color(0xFFD64545)

@Composable
fun VehicleScreen(
    onAddVehicle: () -> Unit,
    onEditVehicle: (String) -> Unit,
    viewModel: VehicleViewModel = viewModel(factory = VehicleViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionSheetVehicle by viewModel.actionSheetVehicle.collectAsStateWithLifecycle()
    val pendingDeleteVehicle by viewModel.pendingDeleteVehicle.collectAsStateWithLifecycle()
    val deleteFailure by viewModel.deleteFailure.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            VehicleUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))
            is VehicleUiState.Data -> VehicleList(state.vehicles, onLongPress = viewModel::openActionSheet)
        }

        UrsFab(
            onClick = onAddVehicle,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
        ) {
            UrsText(text = "+", style = FabIconStyle, color = UrsTheme.colors.onAccent)
        }
    }

    actionSheetVehicle?.let { vehicle ->
        UrsBottomSheet(onDismissRequest = viewModel::closeActionSheet) {
            VehicleActionSheet(
                onEdit = {
                    viewModel.closeActionSheet()
                    onEditVehicle(vehicle.id)
                },
                onDelete = viewModel::requestDelete,
            )
        }
    }

    if (pendingDeleteVehicle != null) {
        UrsBottomSheet(onDismissRequest = viewModel::cancelDelete) {
            DeleteVehicleConfirmSheet(
                failure = deleteFailure,
                onConfirm = viewModel::confirmDelete,
                onCancel = viewModel::cancelDelete,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VehicleList(vehicles: List<VehicleEntity>, onLongPress: (VehicleEntity) -> Unit) {
    if (vehicles.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            UrsText(
                stringResource(R.string.vehicle_list_empty),
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
        items(vehicles, key = { it.id }) { vehicle ->
            UrsCard(
                radius = Radius.row,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = { onLongPress(vehicle) }),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UrsText("${vehicle.brand} ${vehicle.model}", style = UrsTheme.typography.cardTitle)
                    UrsText(vehicle.year, style = UrsTheme.typography.body, color = UrsTheme.colors.onSurfaceMuted)
                }
                Spacer(Modifier.height(Spacing.xs))
                MfkBadge(vehicle.lastMfkDate)
            }
        }
    }
}

@Composable
private fun MfkBadge(lastMfkDate: String?) {
    val days = lastMfkDate?.let(::daysUntilNextMfk) ?: return
    if (days >= 0) {
        UrsPill(text = stringResource(R.string.vehicle_mfk_due_in_days, days))
    } else {
        UrsPill(
            text = stringResource(R.string.vehicle_mfk_overdue_days, -days),
            containerColor = FormErrorColor.copy(alpha = 0.15f),
            contentColor = FormErrorColor,
        )
    }
}

@Composable
private fun VehicleActionSheet(onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l)) {
        ActionSheetRow(label = stringResource(R.string.vehicle_edit), icon = Icons.Filled.Edit, onClick = onEdit)
        ActionSheetRow(
            label = stringResource(R.string.vehicle_delete),
            icon = Icons.Filled.Delete,
            onClick = onDelete,
            tint = FormErrorColor,
        )
    }
}

@Composable
private fun ActionSheetRow(label: String, icon: ImageVector, onClick: () -> Unit, tint: Color = UrsTheme.colors.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrsIcon(imageVector = icon, contentDescription = null, tint = tint)
        UrsText(label, style = UrsTheme.typography.cardTitle, color = tint)
    }
}

@Composable
private fun DeleteVehicleConfirmSheet(failure: VehicleFailure, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.vehicle_delete_confirm_title), style = UrsTheme.typography.cardTitle)
        UrsText(
            stringResource(R.string.vehicle_delete_confirm_body),
            style = UrsTheme.typography.body,
            color = UrsTheme.colors.onSurfaceMuted,
        )
        when (failure) {
            VehicleFailure.HAS_ENTRIES -> UrsText(
                stringResource(R.string.vehicle_delete_has_entries),
                style = UrsTheme.typography.body,
                color = FormErrorColor,
            )
            VehicleFailure.CONNECTIVITY -> UrsText(
                stringResource(R.string.login_error_connectivity),
                style = UrsTheme.typography.body,
                color = FormErrorColor,
            )
            VehicleFailure.UNKNOWN -> UrsText(
                stringResource(R.string.error_save),
                style = UrsTheme.typography.body,
                color = FormErrorColor,
            )
            VehicleFailure.NONE -> Unit
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsOutlinedButton(text = stringResource(R.string.cancel), onClick = onCancel, modifier = Modifier.weight(1f))
            UrsButton(text = stringResource(R.string.vehicle_delete), onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}
