package ch.mcfx.urs.shoppinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.CatalogRepository
import ch.mcfx.urs.data.OpenFoodFactsRepository
import ch.mcfx.urs.data.ShoppingListRepository
import ch.mcfx.urs.data.alphabeticSortKey
import ch.mcfx.urs.data.local.CatalogCategoryEntity
import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.data.remote.CatalogImageDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The three browse tabs — HÄUFIG/ZULETZT/KATEGORIEN, overridden by [AddProductViewModel.query] whenever it's non-blank. */
enum class AddProductTab { POPULAR, RECENT, CATEGORIES }

/**
 * Drives the post-add confirmation bar (Undo + Edit) — [addedItemLocalId] is
 * the just-created [ch.mcfx.urs.data.local.ListItemEntity]'s local row id,
 * passed to [ShoppingListRepository.deleteItem] on Undo, or handed to
 * [ListDetailScreen] on Edit so it can open its existing item editor for that
 * row.
 */
data class AddedFeedback(val productName: String, val addedItemLocalId: Long)

/**
 * Add-product picker, redesigned around three tabs plus a search box that
 * overrides all three when non-blank. Every result is a shared
 * `catalog_product` directly — there's no more separate "household product"
 * search half, since a list item now always points straight at the catalog
 * (see [ShoppingListRepository.addCatalogProduct]'s doc comment). Creating a
 * genuinely new product (search found nothing) calls the new manual-creation
 * endpoint directly (see [CatalogRepository.createProduct]) — no inventory
 * interaction at all.
 *
 * Selecting a result adds it to the list immediately (no note, quantity
 * unset, not on sale) — no separate confirm step. The post-add confirmation
 * bar offers Undo and an Edit action; Edit is handled by [ListDetailScreen],
 * which opens its existing long-press item editor for the just-added row.
 */
class AddProductViewModel(
    private val shoppingListRepository: ShoppingListRepository,
    private val catalogRepository: CatalogRepository,
    private val openFoodFactsRepository: OpenFoodFactsRepository,
    private val listId: String,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedTab = MutableStateFlow(AddProductTab.POPULAR)
    val selectedTab: StateFlow<AddProductTab> = _selectedTab.asStateFlow()

    private val _selectedCategory = MutableStateFlow<CatalogCategoryEntity?>(null)
    val selectedCategory: StateFlow<CatalogCategoryEntity?> = _selectedCategory.asStateFlow()

    private val _categories = MutableStateFlow<List<CatalogCategoryEntity>>(emptyList())
    val categories: StateFlow<List<CatalogCategoryEntity>> = _categories.asStateFlow()

    private val _results = MutableStateFlow<List<CatalogProductEntity>>(emptyList())
    val results: StateFlow<List<CatalogProductEntity>> = _results.asStateFlow()

    // Catalog product ids already on this list — drives UrsSquareTile's
    // dimmed "already on this list" visual state.
    private val _listProductIds = MutableStateFlow<Set<String>>(emptySet())
    val listProductIds: StateFlow<Set<String>> = _listProductIds.asStateFlow()

    private val _lastAdded = MutableStateFlow<AddedFeedback?>(null)
    val lastAdded: StateFlow<AddedFeedback?> = _lastAdded.asStateFlow()

    // Lazily populated per visible tile (see AddProductScreen) rather than
    // batched up front — a search/tab result set can be large, and most of
    // it never scrolls into view.
    private val _quantityOnHand = MutableStateFlow<Map<String, Int?>>(emptyMap())
    val quantityOnHand: StateFlow<Map<String, Int?>> = _quantityOnHand.asStateFlow()

    private val _quickCreating = MutableStateFlow(false)
    val quickCreating: StateFlow<Boolean> = _quickCreating.asStateFlow()

    // Set once a barcode scan resolved to "no local match" — carried into
    // quickCreate() so the eventual new product still gets the scanned
    // barcode attached (mcfx-urs/urs-android#91). Cleared on every
    // selectResult()/quickCreate() so a later manual search isn't mistaken
    // for a continuation of an earlier scan.
    private val _scannedBarcode = MutableStateFlow<String?>(null)
    val scannedBarcode: StateFlow<String?> = _scannedBarcode.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    // Set when a scan resolves to neither a local/backend catalog match nor
    // an Open Food Facts result — otherwise the screen silently falls back to
    // whatever browse tab was showing before the scan, with no indication a
    // scan happened at all (mcfx-urs/urs-android#101). Cleared as soon as the
    // user acts on it (typing, adding, cancelling).
    private val _barcodeNotFound = MutableStateFlow(false)
    val barcodeNotFound: StateFlow<Boolean> = _barcodeNotFound.asStateFlow()

    // Image-suggestion step (mcfx-urs/urs-android#91 point 4) — only entered
    // from quickCreate() when the product being created came from a barcode
    // scan (_scannedBarcode != null); a plain manual quick-create (typed
    // name, no scan) skips straight to performQuickCreate as before.
    private val _imageSuggestionsOpen = MutableStateFlow(false)
    val imageSuggestionsOpen: StateFlow<Boolean> = _imageSuggestionsOpen.asStateFlow()

    private val _images = MutableStateFlow<List<CatalogImageDto>>(emptyList())
    val images: StateFlow<List<CatalogImageDto>> = _images.asStateFlow()

    private var pendingCreateName: String? = null
    private var pendingCreateCategoryId: String? = null

    init {
        viewModelScope.launch {
            shoppingListRepository.observeItems(listId).collect { items ->
                _listProductIds.value = items.map { it.item.catalogProductId }.toSet()
            }
        }
        viewModelScope.launch {
            catalogRepository.observeCategories()
                .collect { _categories.value = it.sortedBy { category -> category.name.alphabeticSortKey() } }
        }

        // collectLatest (not flatMapLatest) so a fast typist's/tab-switcher's
        // stale in-flight combine() is cancelled cleanly without an
        // experimental coroutines opt-in this codebase doesn't otherwise use
        // anywhere (see the earlier AddProductViewModel this replaces).
        viewModelScope.launch {
            combine(_query, _selectedTab, _selectedCategory) { q, tab, category -> Triple(q, tab, category) }
                .collectLatest { (q, tab, category) -> sourceFor(q, tab, category).collect { _results.value = it } }
        }
        load()
    }

    private fun sourceFor(query: String, tab: AddProductTab, category: CatalogCategoryEntity?): Flow<List<CatalogProductEntity>> =
        when {
            query.isNotBlank() -> catalogRepository.search(query)
            tab == AddProductTab.POPULAR -> catalogRepository.observeMostPopular()
            tab == AddProductTab.RECENT -> shoppingListRepository.observeRecentlyUsed(listId)
            tab == AddProductTab.CATEGORIES && category != null -> catalogRepository.observeByCategory(category.id)
            else -> flowOf(emptyList())
        }

    fun load() {
        viewModelScope.launch { catalogRepository.refreshFromBackend() }
        viewModelScope.launch { shoppingListRepository.refreshRecentlyUsed(listId) }
    }

    fun setQuery(value: String) {
        _query.value = value
        _barcodeNotFound.value = false
    }

    fun selectTab(tab: AddProductTab) {
        _selectedTab.value = tab
        _selectedCategory.value = null
    }

    fun selectCategory(category: CatalogCategoryEntity) {
        _selectedCategory.value = category
    }

    fun clearSelectedCategory() {
        _selectedCategory.value = null
    }

    /** Best-effort, cached once resolved — see this field's own doc comment. */
    fun loadQuantityOnHand(catalogProductId: String) {
        if (_quantityOnHand.value.containsKey(catalogProductId)) return
        viewModelScope.launch {
            val quantity = catalogRepository.quantityOnHand(catalogProductId)
            _quantityOnHand.update { it + (catalogProductId to quantity) }
        }
    }

    // Clears the search so the browse view is immediately ready for the next
    // pick, same reasoning as the confirm step this replaces: adding several
    // different results in a row never needs the FAB to be tapped again.
    fun selectResult(product: CatalogProductEntity) {
        _query.value = ""
        _scannedBarcode.value = null
        _barcodeNotFound.value = false
        viewModelScope.launch {
            val localId = shoppingListRepository.addCatalogProduct(listId, product, note = null, quantity = null, onSale = false)
            _lastAdded.value = AddedFeedback(product.name, localId)
        }
    }

    /** Undo for the Snackbar shown after [selectResult] — removes the just-added row again. */
    fun undoLastAdd() {
        val added = _lastAdded.value ?: return
        _lastAdded.value = null
        viewModelScope.launch { shoppingListRepository.deleteItem(added.addedItemLocalId) }
    }

    fun dismissAddedFeedback() {
        _lastAdded.value = null
    }

    /**
     * "Search found nothing" quick-create path — a direct, synchronous REST
     * call (see [CatalogRepository.createProduct]'s doc comment), tied to
     * whichever category was being browsed when this fires (if any) — then
     * added to the list immediately, same as [selectResult]. A scanned
     * barcode with no catalog match routes through the image-suggestion step
     * first (mcfx-urs/urs-android#91 point 4) instead of creating right away.
     */
    fun quickCreate() {
        val name = _query.value.trim()
        if (name.isBlank() || _quickCreating.value) return
        val barcode = _scannedBarcode.value
        if (barcode != null) {
            pendingCreateName = name
            pendingCreateCategoryId = _selectedCategory.value?.id
            viewModelScope.launch {
                _images.value = try {
                    catalogRepository.getImages()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
                _imageSuggestionsOpen.value = true
            }
            return
        }
        performQuickCreate(name, _selectedCategory.value?.id, barcode = null, imageId = null)
    }

    /** Image-suggestion sheet's outcome — `imageId` null on "Skip", the picked [CatalogImageDto.id] otherwise. */
    fun confirmQuickCreateWithImage(imageId: String?) {
        val name = pendingCreateName ?: return
        val categoryId = pendingCreateCategoryId
        _imageSuggestionsOpen.value = false
        pendingCreateName = null
        pendingCreateCategoryId = null
        performQuickCreate(name, categoryId, barcode = _scannedBarcode.value, imageId = imageId)
    }

    /** Closes the image-suggestion sheet without creating anything — e.g. a back-press/scrim dismiss. */
    fun cancelImageSuggestions() {
        _imageSuggestionsOpen.value = false
        pendingCreateName = null
        pendingCreateCategoryId = null
    }

    private fun performQuickCreate(name: String, categoryId: String?, barcode: String?, imageId: String?) {
        viewModelScope.launch {
            _quickCreating.value = true
            try {
                val product = catalogRepository.createProduct(name = name, catalogCategoryId = categoryId, barcode = barcode)
                if (imageId != null) {
                    catalogRepository.updateProduct(product.id, product.name, product.catalogCategoryId, imageId, barcode)
                }
                _query.value = ""
                _scannedBarcode.value = null
                _barcodeNotFound.value = false
                val localId = shoppingListRepository.addCatalogProduct(listId, product, note = null, quantity = null, onSale = false)
                _lastAdded.value = AddedFeedback(product.name, localId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort: the user can just retry the tap.
            } finally {
                _quickCreating.value = false
            }
        }
    }

    /**
     * Barcode scan result (mcfx-urs/urs-android#91) — same shape as
     * [ch.mcfx.urs.inventory.ProductsViewModel.onBarcodeScanned]: a local/
     * backend catalog match adds immediately (same outcome as
     * [selectResult]); no match queries Open Food Facts to prefill the
     * search/quick-create query, storing the scanned barcode either way for
     * [quickCreate] to attach.
     */
    fun onBarcodeScanned(barcode: String) {
        _scanning.value = true
        _barcodeNotFound.value = false
        viewModelScope.launch {
            val existing = try {
                catalogRepository.lookupByBarcode(barcode)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (existing != null) {
                _scanning.value = false
                selectResult(existing)
                return@launch
            }
            val offResult = openFoodFactsRepository.lookup(barcode)
            _scannedBarcode.value = barcode
            _scanning.value = false
            if (offResult != null) {
                _query.value = offResult.name
            } else {
                // No local/backend match and no Open Food Facts match either —
                // surface that explicitly instead of silently reverting to
                // whatever browse tab was showing (mcfx-urs/urs-android#101).
                _barcodeNotFound.value = true
            }
        }
    }

    companion object {
        fun factory(listId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                AddProductViewModel(
                    app.container.shoppingListRepository,
                    app.container.catalogRepository,
                    app.container.openFoodFactsRepository,
                    listId,
                )
            }
        }
    }
}
