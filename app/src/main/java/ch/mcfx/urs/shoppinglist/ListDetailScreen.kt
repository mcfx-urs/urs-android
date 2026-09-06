package ch.mcfx.urs.shoppinglist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.ShoppingListItemDetail
import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsDockedPanel
import ch.mcfx.urs.ui.components.UrsFab
import ch.mcfx.urs.ui.components.UrsProgressIndicator
import ch.mcfx.urs.ui.components.UrsSectionDivider
import ch.mcfx.urs.ui.components.UrsSquareTile
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.components.ursScreenContentPadding
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

private val FabIconStyle = TextStyle(fontSize = 28.sp)

@Composable
fun ListDetailScreen(
    listId: String,
    viewModel: ListDetailViewModel = viewModel(
        factory = ListDetailViewModel.factory(listId, stringResource(R.string.inventory_uncategorized)),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showAddProduct by viewModel.showAddProduct.collectAsStateWithLifecycle()
    val noteForm by viewModel.noteForm.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (val state = uiState) {
            ListDetailUiState.Loading -> UrsProgressIndicator(Modifier.align(Alignment.Center))

            is ListDetailUiState.Data -> Column(Modifier.fillMaxSize()) {
                UrsText(
                    state.listName,
                    style = UrsTheme.typography.screenTitle,
                    color = UrsTheme.colors.accent,
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
                )
                ItemGrid(
                    groups = state.groups,
                    recentlyUsed = state.recentlyUsed,
                    onRemove = viewModel::removeItem,
                    onLongPress = viewModel::openNoteForm,
                    onAddRecentlyUsed = viewModel::addRecentlyUsed,
                )
            }
        }

        if (uiState is ListDetailUiState.Data && !showAddProduct) {
            UrsFab(
                onClick = viewModel::openAddProduct,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.l),
            ) {
                UrsText(text = "+", style = FabIconStyle, color = UrsTheme.colors.onAccent)
            }
        }
    }

    if (showAddProduct) {
        UrsDockedPanel(onDismissRequest = viewModel::closeAddProduct) {
            AddProductScreen(
                listId = listId,
                onClose = viewModel::closeAddProduct,
                onEditAdded = viewModel::openNoteFormFor,
            )
        }
    }

    if (noteForm.item != null) {
        UrsBottomSheet(onDismissRequest = viewModel::closeNoteForm) {
            NoteForm(form = noteForm, viewModel = viewModel)
        }
    }
}

@Composable
private fun ItemGrid(
    groups: List<ShoppingListCategoryGroup>,
    recentlyUsed: List<CatalogProductEntity>,
    onRemove: (ShoppingListItemDetail) -> Unit,
    onLongPress: (ShoppingListItemDetail) -> Unit,
    onAddRecentlyUsed: (CatalogProductEntity) -> Unit,
) {
    if (groups.isEmpty() && recentlyUsed.isEmpty()) {
        Box(Modifier.fillMaxSize()) {
            UrsText(
                stringResource(R.string.shoppinglist_items_empty),
                modifier = Modifier.align(Alignment.Center),
                color = UrsTheme.colors.onSurfaceMuted,
            )
        }
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = ursScreenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        groups.forEach { group ->
            fullWidthItem(key = "header-${group.categoryName}") {
                UrsText(
                    group.categoryName,
                    style = UrsTheme.typography.body,
                    color = UrsTheme.colors.accent,
                    modifier = Modifier.padding(top = Spacing.l, bottom = Spacing.xs),
                )
            }
            items(group.items, key = { it.item.id }) { detail ->
                ItemTile(detail = detail, onRemove = onRemove, onLongPress = onLongPress)
            }
        }

        if (recentlyUsed.isNotEmpty()) {
            if (groups.isNotEmpty()) {
                fullWidthItem(key = "divider-recently-used") {
                    UrsSectionDivider(modifier = Modifier.padding(top = Spacing.s))
                }
            }
            fullWidthItem(key = "recently-used-header") {
                UrsText(
                    stringResource(R.string.shoppinglist_recently_used),
                    style = UrsTheme.typography.caption,
                    color = UrsTheme.colors.accent,
                    modifier = Modifier.padding(top = Spacing.l, bottom = Spacing.xs),
                )
            }
            items(recentlyUsed, key = { "recent-${it.id}" }) { product ->
                UrsSquareTile(
                    title = product.name,
                    catalogImageId = product.catalogImageId,
                    mutedTitle = true,
                    onClick = { onAddRecentlyUsed(product) },
                )
            }
        }
    }
}

private fun LazyGridScope.fullWidthItem(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun ItemTile(
    detail: ShoppingListItemDetail,
    onRemove: (ShoppingListItemDetail) -> Unit,
    onLongPress: (ShoppingListItemDetail) -> Unit,
) {
    UrsSquareTile(
        title = detail.productName,
        catalogImageId = detail.catalogImageId,
        onClick = { onRemove(detail) },
        onLongClick = { onLongPress(detail) },
        topEndBadge = detail.item.quantity?.let { stringResource(R.string.shoppinglist_item_quantity_badge, it) },
        hazardBorder = detail.item.onSale,
    )
}

@Composable
private fun NoteForm(form: AddNoteFormState, viewModel: ListDetailViewModel) {
    val detail = form.item ?: return
    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(detail.productName, style = UrsTheme.typography.screenTitle)
        UrsTextField(
            value = form.note,
            onValueChange = viewModel::setNote,
            label = stringResource(R.string.shoppinglist_item_note),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        QuantityAndOnSaleRow(
            quantity = form.quantity,
            onSale = form.onSale,
            onIncrement = viewModel::incrementQuantity,
            onDecrement = viewModel::decrementQuantity,
            onToggleOnSale = viewModel::toggleOnSale,
        )
        UrsButton(
            text = stringResource(R.string.save),
            onClick = viewModel::saveNote,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
