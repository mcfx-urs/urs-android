package ch.mcfx.urs.assets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import ch.mcfx.urs.data.AssetCategory
import ch.mcfx.urs.data.local.AssetEntity
import ch.mcfx.urs.data.toRaw
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsDropdownField
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.ursScreenContentPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import java.util.Locale

private val FabIconStyle = TextStyle(fontSize = 28.sp)
private val FormErrorColor = Color(0xFFD64545)

@Composable
fun AssetsScreen(
    onAddAsset: () -> Unit,
    onEditAsset: (Long) -> Unit,
    viewModel: AssetsViewModel = viewModel(factory = AssetsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val actionSheetAsset by viewModel.actionSheetAsset.collectAsStateWithLifecycle()
    val pendingDeleteAsset by viewModel.pendingDeleteAsset.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            AssetsUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))
            is AssetsUiState.Data -> Column(modifier = Modifier.fillMaxSize()) {
                FilterRow(
                    categoryFilter = categoryFilter,
                    statusFilter = statusFilter,
                    onCategorySelect = viewModel::setCategoryFilter,
                    onStatusSelect = viewModel::setStatusFilter,
                )
                TotalValueBar(state.assets)
                AssetList(state.assets, onLongPress = viewModel::openActionSheet)
            }
        }

        UrsFab(onClick = onAddAsset, modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l)) {
            UrsText(text = "+", style = FabIconStyle, color = UrsTheme.colors.onAccent)
        }
    }

    actionSheetAsset?.let { asset ->
        UrsBottomSheet(onDismissRequest = viewModel::closeActionSheet) {
            AssetActionSheet(
                onEdit = {
                    viewModel.closeActionSheet()
                    onEditAsset(asset.id)
                },
                onDelete = viewModel::requestDelete,
            )
        }
    }

    if (pendingDeleteAsset != null) {
        UrsBottomSheet(onDismissRequest = viewModel::cancelDelete) {
            DeleteAssetConfirmSheet(onConfirm = viewModel::confirmDelete, onCancel = viewModel::cancelDelete)
        }
    }
}

@Composable
private fun FilterRow(
    categoryFilter: AssetCategory?,
    statusFilter: String?,
    onCategorySelect: (AssetCategory?) -> Unit,
    onStatusSelect: (String?) -> Unit,
) {
    val allLabel = stringResource(R.string.assets_filter_all)
    val statusOptions = listOf<String?>(null, AssetEntity.STATUS_ACTIVE, AssetEntity.STATUS_SOLD, AssetEntity.STATUS_DISPOSED, AssetEntity.STATUS_LOST)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l).padding(top = Spacing.l),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsDropdownField(
            label = stringResource(R.string.assets_filter_category),
            options = listOf<AssetCategory?>(null) + AssetCategory.entries,
            selectedLabel = categoryFilter?.toRaw() ?: allLabel,
            optionLabel = { it?.toRaw() ?: allLabel },
            onSelect = onCategorySelect,
            modifier = Modifier.weight(1f),
        )
        val statusLabels = assetStatusLabels()
        UrsDropdownField(
            label = stringResource(R.string.assets_filter_status),
            options = statusOptions,
            selectedLabel = statusFilter?.let { statusLabels.getValue(it) } ?: allLabel,
            optionLabel = { it?.let { s -> statusLabels.getValue(s) } ?: allLabel },
            onSelect = onStatusSelect,
            modifier = Modifier.weight(1f),
        )
    }
}

// Plain lookup map, not a per-call @Composable function — UrsDropdownField's
// optionLabel is a plain (T) -> String lambda, which can't invoke
// stringResource itself (see AssetAddScreen's identical helper).
@Composable
private fun assetStatusLabels(): Map<String, String> = mapOf(
    AssetEntity.STATUS_ACTIVE to stringResource(R.string.assets_status_active),
    AssetEntity.STATUS_SOLD to stringResource(R.string.assets_status_sold),
    AssetEntity.STATUS_DISPOSED to stringResource(R.string.assets_status_disposed),
    AssetEntity.STATUS_LOST to stringResource(R.string.assets_status_lost),
)

@Composable
private fun assetStatusLabel(status: String): String = assetStatusLabels().getValue(status)

@Composable
private fun TotalValueBar(assets: List<AssetEntity>) {
    val total = assets.sumOf { it.totalValue.toDoubleOrNull() ?: 0.0 }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l).padding(top = Spacing.m),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        UrsText(stringResource(R.string.assets_total_value), style = UrsTheme.typography.caption, color = UrsTheme.colors.onSurfaceMuted)
        UrsText(String.format(Locale.US, "CHF %.2f", total), style = UrsTheme.typography.cardTitle)
    }
}

@Composable
private fun AssetList(assets: List<AssetEntity>, onLongPress: (AssetEntity) -> Unit) {
    if (assets.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            UrsText(
                stringResource(R.string.assets_list_empty),
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
        items(assets, key = { it.id }) { asset ->
            AssetRow(asset, onLongPress)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssetRow(asset: AssetEntity, onLongPress: (AssetEntity) -> Unit) {
    UrsCard(
        radius = Radius.row,
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = { onLongPress(asset) }),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsText(asset.name, style = UrsTheme.typography.cardTitle)
            UrsText(String.format(Locale.US, "CHF %.2f", asset.totalValue.toDoubleOrNull() ?: 0.0), style = UrsTheme.typography.cardTitle)
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            UrsText(
                "${asset.category.toRaw()}${if (asset.location.isNotBlank()) " · ${asset.location}" else ""}",
                style = UrsTheme.typography.body,
                color = UrsTheme.colors.onSurfaceMuted,
            )
            if (asset.status != AssetEntity.STATUS_ACTIVE) {
                UrsText(assetStatusLabel(asset.status), style = UrsTheme.typography.body, color = UrsTheme.colors.onSurfaceMuted)
            }
        }
    }
}

@Composable
private fun AssetActionSheet(onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l)) {
        ActionSheetRow(label = stringResource(R.string.assets_edit), icon = Icons.Filled.Edit, onClick = onEdit)
        ActionSheetRow(label = stringResource(R.string.assets_delete), icon = Icons.Filled.Delete, onClick = onDelete, tint = FormErrorColor)
    }
}

@Composable
private fun ActionSheetRow(label: String, icon: ImageVector, onClick: () -> Unit, tint: Color = UrsTheme.colors.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrsIcon(imageVector = icon, contentDescription = null, tint = tint)
        UrsText(label, style = UrsTheme.typography.cardTitle, color = tint)
    }
}

@Composable
private fun DeleteAssetConfirmSheet(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.assets_delete_confirm_title), style = UrsTheme.typography.cardTitle)
        UrsText(
            stringResource(R.string.assets_delete_confirm_body),
            style = UrsTheme.typography.body,
            color = UrsTheme.colors.onSurfaceMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsOutlinedButton(text = stringResource(R.string.cancel), onClick = onCancel, modifier = Modifier.weight(1f))
            UrsButton(text = stringResource(R.string.assets_delete), onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}
