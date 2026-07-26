package ch.mcfx.urs.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import ch.mcfx.urs.data.local.CatalogCategoryEntity
import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsFilterChip
import ch.mcfx.urs.ui.components.UrsIcon
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsSquareTile
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

// Same reasoning as WorkTimeScreen's own local text-style/color constants —
// the design system's type scale doesn't have a "big FAB glyph" size and no
// "error" role in its palette yet.
private val FabIconStyle = TextStyle(fontSize = 28.sp)
private val DeleteTintColor = Color(0xFFD64545)

/**
 * Settings → Product Management — create/edit/delete for
 * manually-created catalog products and categories, plus reusing an
 * existing catalog image. Products/Categories toggle mirrors
 * [ch.mcfx.urs.shoppinglist.AddProductScreen]'s tab chips; long-press →
 * action sheet → edit/delete mirrors [ch.mcfx.urs.worktime.WorkTimeScreen].
 * The PRODUCTS/CATEGORIES grids only ever list `source == "manual"` rows
 * (filtered in [ProductManagementViewModel]) — imported `external_catalog` catalog
 * data never appears there, so there's no per-row hidden-action state to
 * manage for those two tabs. [isSuperUser] additionally gates a third
 * "Katalog" tab (see [CatalogSearchTab]) that searches the *unfiltered*
 * catalog, letting a super user find and edit a `external_catalog` row too — the
 * backend now allows that (locks the row against the next external_catalog import).
 */
@Composable
fun ProductManagementScreen(
    viewModel: ProductManagementViewModel = viewModel(factory = ProductManagementViewModel.Factory),
    isSuperUser: Boolean = false,
    // Image Review used to be its own top-level Settings tile — moved here
    // (super-user only, same as the CATALOG_SEARCH tab) since it's
    // catalog-image-review work, not a general Settings destination.
    onNavigateToImageReview: () -> Unit = {},
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val manualProducts by viewModel.manualProducts.collectAsStateWithLifecycle()
    val manualCategories by viewModel.manualCategories.collectAsStateWithLifecycle()
    val catalogSearchQuery by viewModel.catalogSearchQuery.collectAsStateWithLifecycle()
    val catalogSearchResults by viewModel.catalogSearchResults.collectAsStateWithLifecycle()
    val actionSheetProduct by viewModel.actionSheetProduct.collectAsStateWithLifecycle()
    val actionSheetCategory by viewModel.actionSheetCategory.collectAsStateWithLifecycle()
    val pendingDeleteProduct by viewModel.pendingDeleteProduct.collectAsStateWithLifecycle()
    val pendingDeleteCategory by viewModel.pendingDeleteCategory.collectAsStateWithLifecycle()
    val productForm by viewModel.productForm.collectAsStateWithLifecycle()
    val categoryForm by viewModel.categoryForm.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    UrsFilterChip(
                        label = stringResource(R.string.product_management_tab_products),
                        selected = tab == ProductManagementTab.PRODUCTS,
                        onClick = { viewModel.selectTab(ProductManagementTab.PRODUCTS) },
                    )
                    UrsFilterChip(
                        label = stringResource(R.string.product_management_tab_categories),
                        selected = tab == ProductManagementTab.CATEGORIES,
                        onClick = { viewModel.selectTab(ProductManagementTab.CATEGORIES) },
                    )
                    if (isSuperUser) {
                        UrsFilterChip(
                            label = stringResource(R.string.product_management_tab_catalog_search),
                            selected = tab == ProductManagementTab.CATALOG_SEARCH,
                            onClick = { viewModel.selectTab(ProductManagementTab.CATALOG_SEARCH) },
                        )
                    }
                }
                if (isSuperUser) {
                    UrsIconButton(
                        onClick = onNavigateToImageReview,
                        contentDescription = stringResource(R.string.settings_tile_image_review),
                        imageVector = Icons.Filled.CheckCircle,
                    )
                }
            }

            when (tab) {
                ProductManagementTab.PRODUCTS -> ProductGrid(
                    products = manualProducts,
                    onLongPress = viewModel::openProductActionSheet,
                )

                ProductManagementTab.CATEGORIES -> CategoryGrid(
                    categories = manualCategories,
                    onLongPress = viewModel::openCategoryActionSheet,
                )

                ProductManagementTab.CATALOG_SEARCH -> CatalogSearchTab(
                    query = catalogSearchQuery,
                    onQueryChange = viewModel::setCatalogSearchQuery,
                    results = catalogSearchResults,
                    onSelect = viewModel::editCatalogSearchResult,
                )
            }
        }

        // No "create" FAB on the search tab — it only ever opens the
        // edit form for an existing (selected) product.
        if (tab != ProductManagementTab.CATALOG_SEARCH) {
            UrsFab(
                onClick = viewModel::openCreateForm,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
            ) {
                UrsText(text = "+", style = FabIconStyle, color = UrsTheme.colors.onAccent)
            }
        }
    }

    actionSheetProduct?.let {
        UrsBottomSheet(onDismissRequest = viewModel::closeProductActionSheet) {
            EntityActionSheet(onEdit = viewModel::editProductFromActionSheet, onDelete = viewModel::requestDeleteProduct)
        }
    }
    actionSheetCategory?.let {
        UrsBottomSheet(onDismissRequest = viewModel::closeCategoryActionSheet) {
            EntityActionSheet(onEdit = viewModel::editCategoryFromActionSheet, onDelete = viewModel::requestDeleteCategory)
        }
    }

    if (pendingDeleteProduct != null) {
        UrsBottomSheet(onDismissRequest = viewModel::cancelDeleteProduct) {
            DeleteConfirmSheet(
                title = stringResource(R.string.product_management_delete_product_confirm_title),
                onConfirm = viewModel::confirmDeleteProduct,
                onCancel = viewModel::cancelDeleteProduct,
            )
        }
    }
    if (pendingDeleteCategory != null) {
        UrsBottomSheet(onDismissRequest = viewModel::cancelDeleteCategory) {
            DeleteConfirmSheet(
                title = stringResource(R.string.product_management_delete_category_confirm_title),
                onConfirm = viewModel::confirmDeleteCategory,
                onCancel = viewModel::cancelDeleteCategory,
            )
        }
    }

    if (productForm != null) {
        CatalogProductFormSheet(viewModel = viewModel, onDismissRequest = viewModel::closeProductForm)
    }
    if (categoryForm != null) {
        CatalogCategoryFormSheet(viewModel = viewModel, onDismissRequest = viewModel::closeCategoryForm)
    }
}

@Composable
private fun ProductGrid(products: List<CatalogProductEntity>, onLongPress: (CatalogProductEntity) -> Unit) {
    if (products.isEmpty()) {
        UrsText(
            text = stringResource(R.string.product_management_empty_products),
            color = UrsTheme.colors.onSurfaceMuted,
            modifier = Modifier.padding(top = Spacing.l),
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(products, key = { it.id }) { product ->
            UrsSquareTile(
                title = product.name,
                catalogImageId = product.catalogImageId,
                onClick = {},
                onLongClick = { onLongPress(product) },
            )
        }
    }
}

@Composable
private fun CategoryGrid(categories: List<CatalogCategoryEntity>, onLongPress: (CatalogCategoryEntity) -> Unit) {
    if (categories.isEmpty()) {
        UrsText(
            text = stringResource(R.string.product_management_empty_categories),
            color = UrsTheme.colors.onSurfaceMuted,
            modifier = Modifier.padding(top = Spacing.l),
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(categories, key = { it.id }) { category ->
            UrsSquareTile(
                title = category.name,
                catalogImageId = category.catalogImageId,
                onClick = {},
                onLongClick = { onLongPress(category) },
            )
        }
    }
}

/**
 * Super-user-only "Katalog" tab — searches the unfiltered catalog (manual
 * *and* external_catalog) and opens the same edit form as a long-pressed manual tile,
 * for any result. The "external_catalog" badge on a non-manual result is the visual
 * cue that editing it will lock it against the next external_catalog import, so it's
 * worth pointing out before the user commits to that.
 */
@Composable
private fun CatalogSearchTab(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<CatalogProductEntity>,
    onSelect: (CatalogProductEntity) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        UrsTextField(
            value = query,
            onValueChange = onQueryChange,
            label = stringResource(R.string.product_management_catalog_search_label),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            query.isBlank() -> UrsText(
                text = stringResource(R.string.product_management_catalog_search_hint),
                color = UrsTheme.colors.onSurfaceMuted,
            )
            results.isEmpty() -> UrsText(
                text = stringResource(R.string.product_management_catalog_search_empty),
                color = UrsTheme.colors.onSurfaceMuted,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(results, key = { it.id }) { product ->
                    UrsSquareTile(
                        title = product.name,
                        catalogImageId = product.catalogImageId,
                        onClick = { onSelect(product) },
                        topEndBadge = if (product.source == "manual") null else product.source,
                    )
                }
            }
        }
    }
}

@Composable
private fun EntityActionSheet(onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l)) {
        ActionSheetRow(label = stringResource(R.string.product_management_edit), icon = Icons.Filled.Edit, onClick = onEdit)
        ActionSheetRow(
            label = stringResource(R.string.product_management_delete),
            icon = Icons.Filled.Delete,
            onClick = onDelete,
            tint = DeleteTintColor,
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
private fun DeleteConfirmSheet(title: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(title, style = UrsTheme.typography.cardTitle)
        UrsText(
            stringResource(R.string.product_management_delete_confirm_body),
            style = UrsTheme.typography.body,
            color = UrsTheme.colors.onSurfaceMuted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsOutlinedButton(text = stringResource(R.string.cancel), onClick = onCancel, modifier = Modifier.weight(1f))
            UrsButton(text = stringResource(R.string.product_management_delete), onClick = onConfirm, modifier = Modifier.weight(1f))
        }
    }
}
