package ch.mcfx.urs.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsCheckbox
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsSquareTile
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

private val FabIconStyle = TextStyle(fontSize = 28.sp)

@Composable
fun ProductListScreen(
    inventoryId: String,
    inventoryName: String,
    categoryId: String?,
    categoryName: String,
    viewModel: ProductsViewModel = viewModel(
        factory = ProductsViewModel.factory(inventoryId, inventoryName, categoryId, categoryName),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val showForm by viewModel.showForm.collectAsStateWithLifecycle()
    val settingsForm by viewModel.settingsForm.collectAsStateWithLifecycle()
    val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            UrsText(
                categoryName,
                style = UrsTheme.typography.screenTitle,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            )
            when (val state = uiState) {
                ProductsUiState.Loading -> Box(Modifier.fillMaxSize()) {
                    UrsProgressIndicator(Modifier.align(Alignment.Center))
                }

                is ProductsUiState.Data -> ProductGrid(products = state.products, onOpenSettings = viewModel::openSettings)
            }
        }

        if (uiState is ProductsUiState.Data) {
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
            AddInventoryProductForm(form = formState, query = query, results = results, viewModel = viewModel)
        }
    }

    if (showSettings) {
        UrsBottomSheet(onDismissRequest = viewModel::closeSettings) {
            ProductSettingsForm(form = settingsForm, viewModel = viewModel)
        }
    }
}

@Composable
private fun ProductGrid(products: List<InventoryProductTile>, onOpenSettings: (InventoryProductTile) -> Unit) {
    if (products.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            UrsText(
                stringResource(R.string.inventory_products_empty),
                modifier = Modifier.align(Alignment.Center),
                color = UrsTheme.colors.onSurfaceMuted,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        gridItems(products, key = { it.product.id }) { tile ->
            UrsSquareTile(
                title = tile.name,
                catalogImageId = tile.catalogImageId,
                // Current quantity as a small badge — no more inline
                // +/- stepper: a tap opens the quantity/settings sheet.
                quantityBadge = tile.product.quantity?.toString() ?: stringResource(R.string.inventory_product_not_tracked),
                onClick = { onOpenSettings(tile) },
            )
        }
    }
}

@Composable
private fun AddInventoryProductForm(
    form: AddProductFormState,
    query: String,
    results: List<CatalogProductEntity>,
    viewModel: ProductsViewModel,
) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).heightIn(max = 480.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.inventory_product_add), style = UrsTheme.typography.screenTitle)

        UrsTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            label = stringResource(R.string.inventory_product_name),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (query.isNotBlank()) {
            if (results.isEmpty()) {
                UrsText(
                    stringResource(R.string.shoppinglist_add_product_no_results),
                    color = UrsTheme.colors.onSurfaceMuted,
                    style = UrsTheme.typography.body,
                )
                if (form.submitFailed) {
                    UrsText(stringResource(R.string.error_save), color = UrsTheme.colors.onSurfaceMuted, style = UrsTheme.typography.body)
                }
                UrsButton(
                    text = stringResource(R.string.shoppinglist_add_custom_product),
                    onClick = viewModel::createAndTrackProduct,
                    enabled = !form.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    items(results, key = { it.id }) { result ->
                        CatalogResultRow(result = result, onClick = { viewModel.trackExistingProduct(result) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogResultRow(result: CatalogProductEntity, onClick: () -> Unit) {
    UrsCard(
        radius = Radius.row,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsIcon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = UrsTheme.colors.accent,
                modifier = Modifier.width(24.dp),
            )
            UrsText(result.name, style = UrsTheme.typography.body)
        }
    }
}

@Composable
private fun ProductSettingsForm(form: ProductSettingsFormState, viewModel: ProductsViewModel) {
    val tile = form.product ?: return
    val thresholdOrderError = stringResource(R.string.inventory_settings_error_threshold_order)
    val reminderFieldsError = stringResource(R.string.inventory_settings_error_reminder_fields)
    val reminderTitle = stringResource(R.string.inventory_settings_reminder_title, tile.name)
    val reminderBody = stringResource(R.string.inventory_settings_reminder_body, tile.name)

    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(
            stringResource(R.string.inventory_settings_title, tile.name),
            style = UrsTheme.typography.screenTitle,
        )

        UrsTextField(
            value = form.quantity,
            onValueChange = viewModel::setSettingsQuantity,
            label = stringResource(R.string.inventory_settings_quantity),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = form.firstThreshold,
            onValueChange = viewModel::setFirstThreshold,
            label = stringResource(R.string.inventory_settings_first_threshold),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        UrsTextField(
            value = form.secondThreshold,
            onValueChange = viewModel::setSecondThreshold,
            label = stringResource(R.string.inventory_settings_second_threshold),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            UrsCheckbox(checked = form.reminderEnabled, onCheckedChange = viewModel::setReminderEnabled)
            Spacer(Modifier.width(Spacing.s))
            UrsText(stringResource(R.string.inventory_settings_reminder_enable), style = UrsTheme.typography.body)
        }

        if (form.reminderEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                UrsTextField(
                    value = form.reminderHour,
                    onValueChange = viewModel::setReminderHour,
                    label = stringResource(R.string.inventory_settings_reminder_hour),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                UrsTextField(
                    value = form.reminderMinute,
                    onValueChange = viewModel::setReminderMinute,
                    label = stringResource(R.string.inventory_settings_reminder_minute),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            UrsTextField(
                value = form.reminderThreshold,
                onValueChange = viewModel::setReminderThreshold,
                label = stringResource(R.string.inventory_settings_reminder_threshold),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (form.error != null) {
            UrsText(form.error, color = UrsTheme.colors.onSurfaceMuted, style = UrsTheme.typography.body)
        }

        UrsButton(
            text = stringResource(if (form.submitting) R.string.saving else R.string.save),
            onClick = {
                viewModel.submitSettings(
                    thresholdOrderError = thresholdOrderError,
                    reminderFieldsError = reminderFieldsError,
                    reminderTitle = reminderTitle,
                    reminderBody = reminderBody,
                )
            },
            enabled = !form.submitting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
