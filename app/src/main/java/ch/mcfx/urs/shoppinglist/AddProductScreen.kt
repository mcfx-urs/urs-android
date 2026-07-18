package ch.mcfx.urs.shoppinglist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.CatalogCategoryEntity
import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsFilterChip
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsSquareTile
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Content of the "add a product to this list" panel, opened from
 * [ListDetailScreen]'s FAB — redesigned around three tabs (HÄUFIG/ZULETZT/
 * KATEGORIEN) plus a search box that overrides all three when non-blank
 *. A single [ch.mcfx.urs.ui.components.UrsDockedPanel] instance
 * switches between "browse/search" and "confirm-note" modes driven by
 * [AddProductViewModel]'s state, same "no nested overlay" reasoning as the
 * pre- screen this replaces — the panel's own fixed height (set by its
 * caller) is what keeps both modes, and every tab within browse mode, the
 * exact same size; this composable only ever fills that given height, never
 * measures its own.
 */
@Composable
fun AddProductScreen(
    listId: String,
    onClose: () -> Unit,
    viewModel: AddProductViewModel = viewModel(factory = AddProductViewModel.factory(listId)),
) {
    val noteInput by viewModel.noteInput.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsText(
                text = noteInput?.product?.name ?: stringResource(R.string.shoppinglist_add_product_title),
                style = UrsTheme.typography.screenTitle,
            )
            UrsIconButton(onClick = onClose, contentDescription = stringResource(R.string.close), imageVector = Icons.Filled.Close)
        }

        if (noteInput != null) {
            NoteInputMode(state = noteInput, viewModel = viewModel, modifier = Modifier.weight(1f))
        } else {
            BrowseMode(viewModel = viewModel, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BrowseMode(viewModel: AddProductViewModel, modifier: Modifier = Modifier) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val listProductIds by viewModel.listProductIds.collectAsStateWithLifecycle()
    val quantityOnHand by viewModel.quantityOnHand.collectAsStateWithLifecycle()
    val quickCreating by viewModel.quickCreating.collectAsStateWithLifecycle()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            label = stringResource(R.string.shoppinglist_add_product_search),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (query.isBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                UrsFilterChip(
                    label = stringResource(R.string.shoppinglist_tab_popular),
                    selected = selectedTab == AddProductTab.POPULAR,
                    onClick = { viewModel.selectTab(AddProductTab.POPULAR) },
                )
                UrsFilterChip(
                    label = stringResource(R.string.shoppinglist_tab_recent),
                    selected = selectedTab == AddProductTab.RECENT,
                    onClick = { viewModel.selectTab(AddProductTab.RECENT) },
                )
                UrsFilterChip(
                    label = stringResource(R.string.shoppinglist_tab_categories),
                    selected = selectedTab == AddProductTab.CATEGORIES,
                    onClick = { viewModel.selectTab(AddProductTab.CATEGORIES) },
                )
            }
        }

        when {
            query.isBlank() && selectedTab == AddProductTab.CATEGORIES && selectedCategory == null ->
                CategoryList(categories = categories, onSelect = viewModel::selectCategory)

            else -> {
                if (query.isBlank() && selectedTab == AddProductTab.CATEGORIES && selectedCategory != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::clearSelectedCategory),
                    ) {
                        UrsText(
                            "‹ ${selectedCategory?.name.orEmpty()}",
                            style = UrsTheme.typography.body,
                            color = UrsTheme.colors.accent,
                        )
                    }
                }

                if (query.isNotBlank() && results.isEmpty()) {
                    UrsText(
                        stringResource(R.string.shoppinglist_add_product_no_results),
                        color = UrsTheme.colors.onSurfaceMuted,
                        style = UrsTheme.typography.body,
                    )
                    UrsButton(
                        text = stringResource(if (quickCreating) R.string.saving else R.string.shoppinglist_add_custom_product),
                        onClick = viewModel::quickCreate,
                        enabled = !quickCreating,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    ProductTileGrid(
                        products = results,
                        listProductIds = listProductIds,
                        quantityOnHand = quantityOnHand,
                        onSelect = viewModel::selectResult,
                        onVisible = viewModel::loadQuantityOnHand,
                    )
                }
            }
        }
    }
}

// Alphabetical rows, not tiles — categories have no product photo, so an
// image-shaped tile would only ever show the basket placeholder icon; a
// plain list reads better for text-only entries (already alphabetically
// sorted by AddProductViewModel).
@Composable
private fun CategoryList(categories: List<CatalogCategoryEntity>, onSelect: (CatalogCategoryEntity) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        items(categories, key = { it.id }) { category ->
            CategoryRow(category = category, onSelect = onSelect)
        }
    }
}

@Composable
private fun CategoryRow(category: CatalogCategoryEntity, onSelect: (CatalogCategoryEntity) -> Unit) {
    UrsCard(
        radius = Radius.row,
        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m),
        modifier = Modifier.fillMaxWidth().clickable(onClick = { onSelect(category) }),
    ) {
        UrsText(category.name, style = UrsTheme.typography.cardTitle)
    }
}

@Composable
private fun ProductTileGrid(
    products: List<CatalogProductEntity>,
    listProductIds: Set<String>,
    quantityOnHand: Map<String, Int?>,
    onSelect: (CatalogProductEntity) -> Unit,
    onVisible: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        gridItems(products, key = { it.id }) { product ->
            LaunchedEffect(product.id) { onVisible(product.id) }
            val quantity = quantityOnHand[product.id]
            UrsSquareTile(
                title = product.name,
                catalogImageId = product.catalogImageId,
                dimmed = product.id in listProductIds,
                quantityBadge = quantity?.toString(),
                onClick = { onSelect(product) },
            )
        }
    }
}

@Composable
private fun NoteInputMode(state: NoteInputState?, viewModel: AddProductViewModel, modifier: Modifier = Modifier) {
    if (state == null) return

    val recentNotes = listOfNotNull(state.product.recentNote1, state.product.recentNote2, state.product.recentNote3)
        .filter { it.isNotBlank() }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsTextField(
            value = state.note,
            onValueChange = viewModel::setNote,
            label = stringResource(R.string.shoppinglist_item_note),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Tapping a chip only fills the text field above, it never submits
        // by itself — the user can still edit the note before confirming,
        // and picking one doesn't reorder the history unless it's actually
        // resubmitted (the normal add-with-note flow below does that).
        if (recentNotes.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                recentNotes.forEach { note ->
                    UrsFilterChip(label = note, selected = false, onClick = { viewModel.setNote(note) })
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            UrsOutlinedButton(
                text = stringResource(R.string.cancel),
                onClick = viewModel::closeNoteInput,
                modifier = Modifier.weight(1f),
            )
            UrsButton(
                text = stringResource(R.string.shoppinglist_add_product_add),
                onClick = { viewModel.confirmAdd() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
