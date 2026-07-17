package ch.mcfx.urs.shoppinglist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.CatalogCategoryEntity
import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsFilterChip
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsSquareTile
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import ch.mcfx.urs.ui.tokens.Spacing

/**
 * Content of the "add a product to this list" sheet, opened from
 * [ListDetailScreen]'s FAB — redesigned around three tabs (HÄUFIG/ZULETZT/
 * KATEGORIEN) plus a search box that overrides all three when non-blank
 *. A single sheet instance switches between "browse/search" and
 * "confirm-note" modes driven by [AddProductViewModel]'s state, same
 * "no nested UrsBottomSheet" reasoning as the pre- screen this
 * replaces.
 */
@Composable
fun AddProductScreen(
    listId: String,
    viewModel: AddProductViewModel = viewModel(factory = AddProductViewModel.factory(listId)),
) {
    val noteInput by viewModel.noteInput.collectAsStateWithLifecycle()

    if (noteInput != null) {
        NoteInputMode(state = noteInput, viewModel = viewModel)
    } else {
        BrowseMode(viewModel = viewModel)
    }
}

@Composable
private fun BrowseMode(viewModel: AddProductViewModel) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val listProductIds by viewModel.listProductIds.collectAsStateWithLifecycle()
    val quantityOnHand by viewModel.quantityOnHand.collectAsStateWithLifecycle()
    val quickCreating by viewModel.quickCreating.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).heightIn(max = 520.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(stringResource(R.string.shoppinglist_add_product_title), style = UrsTheme.typography.screenTitle)

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
                CategoryTileGrid(categories = categories, onSelect = viewModel::selectCategory)

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

@Composable
private fun CategoryTileGrid(categories: List<CatalogCategoryEntity>, onSelect: (CatalogCategoryEntity) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(categories, key = { it.id }) { category ->
            UrsSquareTile(title = category.name, catalogImageId = null, onClick = { onSelect(category) })
        }
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
        items(products, key = { it.id }) { product ->
            LaunchedEffect(product.id) { onVisible(product.id) }
            val quantity = quantityOnHand[product.id]
            UrsSquareTile(
                title = product.name,
                catalogImageId = product.catalogImageId,
                dimmed = product.id in listProductIds,
                quantityBadge = quantity?.toString(),
                showAddAffordance = true,
                onClick = { onSelect(product) },
            )
        }
    }
}

@Composable
private fun NoteInputMode(state: NoteInputState?, viewModel: AddProductViewModel) {
    if (state == null) return

    val recentNotes = listOfNotNull(state.product.recentNote1, state.product.recentNote2, state.product.recentNote3)
        .filter { it.isNotBlank() }

    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).padding(bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsText(state.product.name, style = UrsTheme.typography.screenTitle)

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
