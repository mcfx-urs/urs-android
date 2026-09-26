package ch.mcfx.urs.shoppinglist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ch.mcfx.urs.R
import ch.mcfx.urs.data.local.CatalogCategoryEntity
import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.settings.PortraitCaptureActivity
import ch.mcfx.urs.ui.components.CatalogImagePicker
import ch.mcfx.urs.ui.components.UrsBottomSheet
import ch.mcfx.urs.ui.components.UrsButton
import ch.mcfx.urs.ui.components.UrsCard
import ch.mcfx.urs.ui.components.UrsCheckbox
import ch.mcfx.urs.ui.components.UrsConfirmBar
import ch.mcfx.urs.ui.components.UrsFilterChip
import ch.mcfx.urs.ui.components.UrsIconButton
import ch.mcfx.urs.ui.components.UrsOutlinedButton
import ch.mcfx.urs.ui.components.UrsSquareTile
import ch.mcfx.urs.ui.components.UrsText
import ch.mcfx.urs.ui.components.UrsTextField
import ch.mcfx.urs.ui.theme.UrsTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import ch.mcfx.urs.ui.tokens.Radius
import ch.mcfx.urs.ui.tokens.Spacing
import kotlinx.coroutines.delay

// How long the "added to the list" confirmation bar stays up before it
// dismisses itself if the user takes neither action.
private const val ConfirmBarTimeoutMillis = 4000L

/**
 * Content of the "add a product to this list" panel, opened from
 * [ListDetailScreen]'s FAB — three tabs (HÄUFIG/ZULETZT/KATEGORIEN) plus a
 * search box that overrides all three when non-blank. Tapping a result adds
 * it to the list immediately (see [AddProductViewModel.selectResult]) — a
 * confirmation bar pinned at the panel's bottom then offers Undo, and Edit
 * (via [onEditAdded], handled by [ListDetailScreen]'s own item editor).
 */
@Composable
fun AddProductScreen(
    listId: String,
    onClose: () -> Unit,
    onEditAdded: (Long) -> Unit,
    viewModel: AddProductViewModel = viewModel(factory = AddProductViewModel.factory(listId)),
) {
    val lastAdded by viewModel.lastAdded.collectAsStateWithLifecycle()
    val addedMessageFormat = stringResource(R.string.shoppinglist_add_product_added)
    val imageSuggestionsOpen by viewModel.imageSuggestionsOpen.collectAsStateWithLifecycle()
    val suggestionImages by viewModel.images.collectAsStateWithLifecycle()
    val suggestionQuery by viewModel.query.collectAsStateWithLifecycle()

    // A custom bar rather than a material3 Snackbar: it needs two actions
    // (Undo + Edit), and Snackbar carries only one. Dismisses itself after a
    // short window if neither is used.
    LaunchedEffect(lastAdded) {
        if (lastAdded == null) return@LaunchedEffect
        delay(ConfirmBarTimeoutMillis)
        viewModel.dismissAddedFeedback()
    }

    Column(
        modifier = Modifier.padding(horizontal = Spacing.xl).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UrsText(text = stringResource(R.string.shoppinglist_add_product_title), style = UrsTheme.typography.screenTitle)
            UrsIconButton(onClick = onClose, contentDescription = stringResource(R.string.close), imageVector = Icons.Filled.Close)
        }

        BrowseMode(viewModel = viewModel, modifier = Modifier.weight(1f))

        lastAdded?.let { added ->
            AddedConfirmBar(
                message = String.format(addedMessageFormat, added.productName),
                onUndo = viewModel::undoLastAdd,
                onEdit = {
                    onEditAdded(added.addedItemLocalId)
                    viewModel.dismissAddedFeedback()
                },
            )
        }
    }

    if (imageSuggestionsOpen) {
        UrsBottomSheet(onDismissRequest = viewModel::cancelImageSuggestions) {
            UrsText(
                text = stringResource(R.string.product_image_suggestions_title),
                style = UrsTheme.typography.cardTitle,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            )
            CatalogImagePicker(
                images = suggestionImages,
                initialQuery = suggestionQuery,
                onSelect = viewModel::confirmQuickCreateWithImage,
            )
            UrsOutlinedButton(
                text = stringResource(R.string.product_image_suggestions_skip),
                onClick = { viewModel.confirmQuickCreateWithImage(null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m),
            )
        }
    }
}

@Composable
private fun AddedConfirmBar(
    message: String,
    onUndo: () -> Unit,
    onEdit: () -> Unit,
) {
    UrsConfirmBar(message = message) {
        UrsText(
            stringResource(R.string.undo),
            style = UrsTheme.typography.body,
            color = UrsTheme.colors.accent,
            modifier = Modifier.clickable(onClick = onUndo).padding(Spacing.s),
        )
        UrsText(
            stringResource(R.string.edit),
            style = UrsTheme.typography.body,
            color = UrsTheme.colors.accent,
            modifier = Modifier.clickable(onClick = onEdit).padding(Spacing.s),
        )
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
    val scanning by viewModel.scanning.collectAsStateWithLifecycle()
    val barcodeNotFound by viewModel.barcodeNotFound.collectAsStateWithLifecycle()

    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    // Tapping "+" to open this picker should land the user straight in the
    // search field with the keyboard already up — one less tap before
    // typing, since searching is the single most common thing done here.
    // Re-requested whenever a scan finishes too (not just on first entry) —
    // the scanner activity takes focus/keyboard away, and a scan that comes
    // back with nothing to prefill needs the user typing right away, not
    // landing on an unfocused, seemingly unchanged screen (mcfx-urs/urs-android#101).
    LaunchedEffect(scanning) {
        if (scanning) return@LaunchedEffect
        searchFocusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        UrsTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            label = stringResource(R.string.shoppinglist_add_product_search),
            focusRequester = searchFocusRequester,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
            result.contents?.let { viewModel.onBarcodeScanned(it) }
        }
        UrsOutlinedButton(
            text = stringResource(if (scanning) R.string.product_scanning else R.string.product_scan_barcode),
            enabled = !scanning,
            onClick = {
                scanLauncher.launch(
                    ScanOptions()
                        .setDesiredBarcodeFormats(ScanOptions.PRODUCT_CODE_TYPES)
                        .setBeepEnabled(false)
                        .setOrientationLocked(true)
                        .setCaptureActivity(PortraitCaptureActivity::class.java),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (barcodeNotFound) {
            UrsText(
                stringResource(R.string.product_barcode_not_found),
                style = UrsTheme.typography.body,
                color = UrsTheme.colors.onSurfaceMuted,
            )
        }

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

// Amount-needed stepper (+/-, no keyboard entry — "-" while unset) next to
// the "only buy on sale" toggle, both shown together since they're the two
// structured fields distinct from the free-text note above. Not private —
// reused by ListDetailScreen's NoteForm for editing an already-added item.
@Composable
fun QuantityAndOnSaleRow(
    quantity: Int?,
    onSale: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onToggleOnSale: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UrsText(stringResource(R.string.shoppinglist_item_quantity), style = UrsTheme.typography.body)
            UrsIconButton(
                onClick = onDecrement,
                enabled = quantity != null,
                contentDescription = stringResource(R.string.shoppinglist_item_quantity_decrement),
                imageVector = Icons.Filled.Remove,
            )
            UrsText(
                quantity?.toString() ?: stringResource(R.string.shoppinglist_item_quantity_unset),
                style = UrsTheme.typography.cardTitle.copy(textAlign = TextAlign.Center),
                modifier = Modifier.width(24.dp),
            )
            UrsIconButton(
                onClick = onIncrement,
                contentDescription = stringResource(R.string.shoppinglist_item_quantity_increment),
                imageVector = Icons.Filled.Add,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            UrsText(stringResource(R.string.shoppinglist_item_on_sale), style = UrsTheme.typography.body)
            UrsCheckbox(
                checked = onSale,
                onCheckedChange = { onToggleOnSale() },
                modifier = Modifier.padding(start = Spacing.s),
            )
        }
    }
}
