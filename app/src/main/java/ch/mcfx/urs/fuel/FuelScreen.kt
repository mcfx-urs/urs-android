package ch.mcfx.urs.fuel

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
import ch.mcfx.urs.data.local.FillEntity
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import java.util.Locale

// Same reasoning as FuelHubScreen's/HomeScreen's own local text-style
// constants — the design system's type scale doesn't have a "big FAB glyph"
// size, so this is a one-off rather than a new shared token.
private val FabIconStyle = TextStyle(fontSize = 28.sp)

// No "error" role in the design system's palette yet (see Color.kt) — same
// local-constant pattern already used elsewhere (FuelAddScreen, FuelStationsScreen).
private val FormErrorColor = Color(0xFFD64545)

@Composable
fun FuelScreen(
    onAddFillUp: () -> Unit,
    onEditFillUp: (Long) -> Unit,
    viewModel: FuelViewModel = viewModel(factory = FuelViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionSheetFill by viewModel.actionSheetFill.collectAsStateWithLifecycle()
    val pendingDeleteFill by viewModel.pendingDeleteFill.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            FuelUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))
            is FuelUiState.Data -> FillList(state, onLongPress = viewModel::openActionSheet)
        }

        if (uiState is FuelUiState.Data) {
            UrsFab(
                onClick = onAddFillUp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
            ) {
                UrsText(text = "+", style = FabIconStyle, color = UrsTheme.colors.onAccent)
            }
        }
    }

    // Long-press → Edit/Delete: same UrsBottomSheet mechanic used for the
    // delete-confirmation sheet below it, just with different content —
    // mirrors WorkTimeScreen's identical pattern.
    actionSheetFill?.let { fill ->
        UrsBottomSheet(onDismissRequest = viewModel::closeActionSheet) {
            FillActionSheet(
                onEdit = {
                    viewModel.closeActionSheet()
                    onEditFillUp(fill.id)
                },
                onDelete = viewModel::requestDelete,
            )
        }
    }

    if (pendingDeleteFill != null) {
        UrsBottomSheet(onDismissRequest = viewModel::cancelDelete) {
            DeleteFillConfirmSheet(onConfirm = viewModel::confirmDelete, onCancel = viewModel::cancelDelete)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FillList(state: FuelUiState.Data, onLongPress: (FillEntity) -> Unit) {
    val vehicleNames = remember(state.vehicles) {
        state.vehicles.associate { it.id to "${it.brand} ${it.model}" }
    }
    val stationNames = remember(state.stations) {
        state.stations.associate { it.id to it.name }
    }

    if (state.fills.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            UrsText(
                stringResource(R.string.fills_empty),
                modifier = Modifier.align(Alignment.Center),
                color = UrsTheme.colors.onSurfaceMuted,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(state.fills, key = { it.id }) { fill ->
            UrsCard(
                radius = Radius.row,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = {}, onLongClick = { onLongPress(fill) }),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UrsText(vehicleNames[fill.vehicleId] ?: fill.vehicleId, style = UrsTheme.typography.cardTitle)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SyncStatusPill(fill.syncStatus)
                        UrsText(totalCost(fill.pricePerLiter, fill.liters), style = UrsTheme.typography.cardTitle)
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val stationLabel = fill.stationId?.let { stationNames[it] ?: it }
                        ?: stringResource(R.string.fill_pending_location)
                    UrsText(
                        "${fill.date.substringBefore(' ')} · $stationLabel",
                        style = UrsTheme.typography.body,
                        color = UrsTheme.colors.onSurfaceMuted,
                    )
                    UrsText(
                        stringResource(R.string.fill_amount_at_price, fill.liters, fill.pricePerLiter),
                        style = UrsTheme.typography.body,
                        color = UrsTheme.colors.onSurfaceMuted,
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                UrsText(
                    stringResource(R.string.fill_odometer_km, fill.odometer),
                    style = UrsTheme.typography.caption,
                    color = UrsTheme.colors.onSurfaceMuted,
                )
            }
        }
    }
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
private fun FillActionSheet(onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l)) {
        ActionSheetRow(label = stringResource(R.string.fill_edit), icon = Icons.Filled.Edit, onClick = onEdit)
        ActionSheetRow(
            label = stringResource(R.string.fill_delete),
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
private fun DeleteFillConfirmSheet(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.fill_delete_confirm_title), style = UrsTheme.typography.cardTitle)
        UrsText(
            stringResource(R.string.fill_delete_confirm_body),
            style = UrsTheme.typography.body,
            color = UrsTheme.colors.onSurfaceMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsOutlinedButton(text = stringResource(R.string.cancel), onClick = onCancel, modifier = Modifier.weight(1f))
            UrsButton(text = stringResource(R.string.fill_delete), onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

internal fun totalCost(price: String, liters: String): String {
    val p = price.toFloatOrNull()
    val l = liters.toFloatOrNull()
    return if (p != null && l != null) String.format(Locale.US, "%.2f", p * l) else "–"
}
