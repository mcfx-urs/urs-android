package ch.mcfx.urs.inventory

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import ch.mcfx.urs.data.local.InventoryEntity
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsPill
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsShareSheet
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.components.ursScreenContentPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

private val FabIconStyle = TextStyle(fontSize = 28.sp)
private val FormErrorColor = Color(0xFFD64545)

/** Mirrors [ch.mcfx.urs.shoppinglist.ShoppingListsScreen] exactly — see [InventoriesViewModel]'s own doc comment. */
@Composable
fun InventoriesScreen(
    onOpenInventory: (InventoryEntity) -> Unit,
    viewModel: InventoriesViewModel = viewModel(factory = InventoriesViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()
    val actionSheetInventory by viewModel.actionSheetInventory.collectAsStateWithLifecycle()
    val pendingDeleteInventory by viewModel.pendingDeleteInventory.collectAsStateWithLifecycle()
    val shareState by viewModel.shareState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            InventoriesUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))

            is InventoriesUiState.Data -> InventoriesList(
                inventories = state.inventories,
                onOpenInventory = onOpenInventory,
                onLongPress = viewModel::openActionSheet,
            )
        }

        if (uiState is InventoriesUiState.Data) {
            UrsFab(
                onClick = viewModel::openCreateForm,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
            ) {
                UrsText(text = "+", style = FabIconStyle, color = UrsTheme.colors.onAccent)
            }
        }
    }

    if (showForm) {
        UrsBottomSheet(onDismissRequest = viewModel::closeForm) {
            InventoryForm(form = formState, viewModel = viewModel)
        }
    }

    actionSheetInventory?.let { inventory ->
        UrsBottomSheet(onDismissRequest = viewModel::closeActionSheet) {
            InventoryActionSheet(
                isFavorite = inventory.isFavorite,
                onRename = { viewModel.openRenameForm(inventory) },
                onShare = { viewModel.openShareSheet(inventory) },
                onToggleFavorite = viewModel::toggleFavorite,
                onDelete = viewModel::requestDelete,
            )
        }
    }

    if (pendingDeleteInventory != null) {
        UrsBottomSheet(onDismissRequest = viewModel::cancelDelete) {
            DeleteConfirmSheet(onConfirm = viewModel::confirmDelete, onCancel = viewModel::cancelDelete)
        }
    }

    if (shareState.inventory != null) {
        UrsBottomSheet(onDismissRequest = viewModel::closeShareSheet) {
            UrsShareSheet(
                title = stringResource(R.string.inventory_share_title, shareState.inventory?.name.orEmpty()),
                members = shareState.members,
                sharedUserIds = shareState.sharedUserIds,
                onToggleShare = viewModel::toggleShare,
            )
        }
    }
}

@Composable
private fun InventoriesList(
    inventories: List<InventoryEntity>,
    onOpenInventory: (InventoryEntity) -> Unit,
    onLongPress: (InventoryEntity) -> Unit,
) {
    if (inventories.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            UrsText(
                stringResource(R.string.inventory_inventories_empty),
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
        items(inventories, key = { it.id }) { inventory ->
            InventoryRow(inventory = inventory, onOpenInventory = onOpenInventory, onLongPress = onLongPress)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InventoryRow(inventory: InventoryEntity, onOpenInventory: (InventoryEntity) -> Unit, onLongPress: (InventoryEntity) -> Unit) {
    UrsCard(
        radius = Radius.row,
        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.l),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { onOpenInventory(inventory) }, onLongClick = { onLongPress(inventory) }),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsText(
                inventory.name,
                style = UrsTheme.typography.cardTitle,
                color = UrsTheme.colors.accent,
                modifier = Modifier.weight(1f),
            )
            InventorySyncStatusPill(inventory.syncStatus)
        }
    }
}

@Composable
private fun InventorySyncStatusPill(status: SyncStatus) {
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
private fun InventoryForm(form: InventoryFormState, viewModel: InventoriesViewModel) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(
            stringResource(if (form.editingInventoryId != null) R.string.inventory_rename else R.string.inventory_add),
            style = UrsTheme.typography.screenTitle,
        )

        UrsTextField(
            value = form.name,
            onValueChange = viewModel::setName,
            label = stringResource(R.string.inventory_name),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (form.submitFailed) {
            UrsText(stringResource(R.string.error_save), color = FormErrorColor, style = UrsTheme.typography.body)
        }

        UrsButton(
            text = stringResource(if (form.submitting) R.string.saving else R.string.save),
            onClick = viewModel::submit,
            enabled = form.isValid && !form.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun InventoryActionSheet(
    isFavorite: Boolean,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l)) {
        ActionSheetRow(label = stringResource(R.string.inventory_rename), icon = Icons.Filled.Edit, onClick = onRename)
        ActionSheetRow(label = stringResource(R.string.inventory_share), icon = Icons.Filled.Share, onClick = onShare)
        ActionSheetRow(
            label = stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
            icon = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
            onClick = onToggleFavorite,
        )
        ActionSheetRow(
            label = stringResource(R.string.inventory_delete),
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
private fun DeleteConfirmSheet(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.inventory_delete_confirm_title), style = UrsTheme.typography.cardTitle)
        UrsText(
            stringResource(R.string.inventory_delete_confirm_body),
            style = UrsTheme.typography.body,
            color = UrsTheme.colors.onSurfaceMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsOutlinedButton(text = stringResource(R.string.cancel), onClick = onCancel, modifier = Modifier.weight(1f))
            UrsButton(text = stringResource(R.string.inventory_delete), onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}
