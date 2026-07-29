package ch.mcfx.urs.service

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.remember
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
import ch.mcfx.urs.data.ServiceCategory
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.local.VehicleEntity
import ch.mcfx.urs.data.local.VehicleServiceTagEntity
import ch.mcfx.urs.data.local.VehicleServiceWithTags
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsDropdownField
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
// FuelScreen/VehicleScreen.
private val FormErrorColor = Color(0xFFD64545)

@Composable
fun ServiceScreen(
    onAddService: () -> Unit,
    onEditService: (Long) -> Unit,
    viewModel: ServiceViewModel = viewModel(factory = ServiceViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val vehicleFilter by viewModel.vehicleFilter.collectAsStateWithLifecycle()
    val actionSheetService by viewModel.actionSheetService.collectAsStateWithLifecycle()
    val pendingDeleteService by viewModel.pendingDeleteService.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            ServiceUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))
            is ServiceUiState.Data -> Column(modifier = Modifier.fillMaxSize()) {
                VehicleFilterDropdown(
                    vehicles = state.vehicles,
                    selectedVehicleId = vehicleFilter,
                    onSelect = viewModel::setVehicleFilter,
                )
                ServiceList(state, onLongPress = viewModel::openActionSheet)
            }
        }

        UrsFab(
            onClick = onAddService,
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
        ) {
            UrsText(text = "+", style = FabIconStyle, color = UrsTheme.colors.onAccent)
        }
    }

    actionSheetService?.let { service ->
        UrsBottomSheet(onDismissRequest = viewModel::closeActionSheet) {
            ServiceActionSheet(
                onEdit = {
                    viewModel.closeActionSheet()
                    onEditService(service.service.id)
                },
                onDelete = viewModel::requestDelete,
            )
        }
    }

    if (pendingDeleteService != null) {
        UrsBottomSheet(onDismissRequest = viewModel::cancelDelete) {
            DeleteServiceConfirmSheet(onConfirm = viewModel::confirmDelete, onCancel = viewModel::cancelDelete)
        }
    }
}

@Composable
private fun VehicleFilterDropdown(
    vehicles: List<VehicleEntity>,
    selectedVehicleId: String?,
    onSelect: (String?) -> Unit,
) {
    val allVehiclesLabel = stringResource(R.string.stats_all_vehicles)
    val options: List<VehicleEntity?> = listOf(null) + vehicles
    UrsDropdownField(
        label = stringResource(R.string.service_vehicle),
        options = options,
        selectedLabel = vehicles.firstOrNull { it.id == selectedVehicleId }?.let { "${it.brand} ${it.model}" }
            ?: allVehiclesLabel,
        optionLabel = { it?.let { v -> "${v.brand} ${v.model}" } ?: allVehiclesLabel },
        onSelect = { onSelect(it?.id) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l).padding(top = Spacing.l),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServiceList(state: ServiceUiState.Data, onLongPress: (VehicleServiceWithTags) -> Unit) {
    val vehicleNames = remember(state.vehicles) {
        state.vehicles.associate { it.id to "${it.brand} ${it.model}" }
    }

    if (state.services.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            UrsText(
                stringResource(R.string.service_list_empty),
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
        items(state.services, key = { it.service.id }) { entry ->
            UrsCard(
                radius = Radius.row,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = { onLongPress(entry) }),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UrsText(vehicleNames[entry.service.vehicleId] ?: entry.service.vehicleId, style = UrsTheme.typography.cardTitle)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SyncStatusPill(entry.service.syncStatus)
                        UrsText("${entry.service.costAmount} ${entry.service.currencyCode}", style = UrsTheme.typography.cardTitle)
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val providerLabel = if (entry.service.isDiy) {
                        stringResource(R.string.service_diy_label)
                    } else {
                        entry.service.provider.ifBlank { "–" }
                    }
                    UrsText(
                        "${entry.service.date} · $providerLabel",
                        style = UrsTheme.typography.body,
                        color = UrsTheme.colors.onSurfaceMuted,
                    )
                    UrsText(
                        stringResource(R.string.fill_odometer_km, entry.service.odometer),
                        style = UrsTheme.typography.body,
                        color = UrsTheme.colors.onSurfaceMuted,
                    )
                }
                if (entry.tags.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.xs))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        entry.tags.forEach { tag -> UrsPill(text = tagLabel(tag)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun tagLabel(tag: VehicleServiceTagEntity): String {
    if (tag.code == ServiceCategory.CUSTOM_CODE) return tag.label.orEmpty()
    val category = ServiceCategory.entries.firstOrNull { it.code == tag.code } ?: return tag.code
    return stringResource(category.labelRes)
}

@Composable
private fun SyncStatusPill(status: SyncStatus) {
    when (status) {
        SyncStatus.PENDING -> UrsPill(text = stringResource(R.string.fill_status_pending))
        SyncStatus.FAILED -> UrsPill(
            text = stringResource(R.string.fill_status_failed),
            containerColor = FormErrorColor.copy(alpha = 0.15f),
            contentColor = FormErrorColor,
        )
        SyncStatus.SYNCED -> Unit
    }
}

@Composable
private fun ServiceActionSheet(onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l)) {
        ActionSheetRow(label = stringResource(R.string.service_edit), icon = Icons.Filled.Edit, onClick = onEdit)
        ActionSheetRow(
            label = stringResource(R.string.service_delete),
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
private fun DeleteServiceConfirmSheet(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.service_delete_confirm_title), style = UrsTheme.typography.cardTitle)
        UrsText(
            stringResource(R.string.service_delete_confirm_body),
            style = UrsTheme.typography.body,
            color = UrsTheme.colors.onSurfaceMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsOutlinedButton(text = stringResource(R.string.cancel), onClick = onCancel, modifier = Modifier.weight(1f))
            UrsButton(text = stringResource(R.string.service_delete), onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}
