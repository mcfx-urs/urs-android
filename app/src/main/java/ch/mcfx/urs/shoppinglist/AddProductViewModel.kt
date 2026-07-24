package ch.mcfx.urs.shoppinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.CatalogRepository
import ch.mcfx.urs.data.ShoppingListRepository
import ch.mcfx.urs.data.alphabeticSortKey
import ch.mcfx.urs.data.local.CatalogCategoryEntity
import ch.mcfx.urs.data.local.CatalogProductEntity
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

data class NoteInputState(
    val product: CatalogProductEntity,
    val note: String = "",
    // Null = unset ("-" in the popup, no badge on the tile). Distinct from
    // `note`, which stays free text (brand, color, ...).
    val quantity: Int? = null,
    // "Only buy this on sale" — stock-up items that should stay on the list
    // until a promo price comes along.
    val onSale: Boolean = false,
)

/**
 * Add-product picker, redesigned around three tabs plus a search box that
 * overrides all three when non-blank. Every result is a shared
 * `catalog_product` directly — there's no more separate "household product"
 * search half, since a list item now always points straight at the catalog
 * (see [ShoppingListRepository.addCatalogProduct]'s doc comment). Creating a
 * genuinely new product (search found nothing) calls the new manual-creation
 * endpoint directly (see [CatalogRepository.createProduct]) — no inventory
 * interaction at all.
 */
class AddProductViewModel(
    private val shoppingListRepository: ShoppingListRepository,
    private val catalogRepository: CatalogRepository,
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

    private val _noteInput = MutableStateFlow<NoteInputState?>(null)
    val noteInput: StateFlow<NoteInputState?> = _noteInput.asStateFlow()

    // Lazily populated per visible tile (see AddProductScreen) rather than
    // batched up front — a search/tab result set can be large, and most of
    // it never scrolls into view.
    private val _quantityOnHand = MutableStateFlow<Map<String, Int?>>(emptyMap())
    val quantityOnHand: StateFlow<Map<String, Int?>> = _quantityOnHand.asStateFlow()

    private val _quickCreating = MutableStateFlow(false)
    val quickCreating: StateFlow<Boolean> = _quickCreating.asStateFlow()

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

    fun selectResult(product: CatalogProductEntity) {
        _noteInput.value = NoteInputState(product = product)
    }

    fun setNote(value: String) = _noteInput.update { it?.copy(note = value) }

    fun incrementQuantity() = _noteInput.update { it?.copy(quantity = (it.quantity ?: 0) + 1) }

    // Decrementing below 1 clears back to unset, rather than floor-stopping
    // at 1 — matches the popup's "-" default and the tile's "no badge when
    // unset" display.
    fun decrementQuantity() = _noteInput.update { state ->
        val current = state?.quantity ?: return@update state
        state.copy(quantity = if (current <= 1) null else current - 1)
    }

    fun toggleOnSale() = _noteInput.update { it?.copy(onSale = !it.onSale) }

    fun closeNoteInput() {
        _noteInput.value = null
    }

    // Deliberately does not close the whole sheet on confirm — returns to
    // whichever tab/search was active, same reasoning as the earlier
    // AddProductScreen this replaces: adding several different results in a
    // row, or the same one twice with a different note, never needs the FAB
    // to be tapped again in between.
    fun confirmAdd() {
        val state = _noteInput.value ?: return
        val note = state.note.trim().ifEmpty { null }
        _noteInput.value = null
        _query.value = ""
        viewModelScope.launch {
            shoppingListRepository.addCatalogProduct(listId, state.product, note, state.quantity, state.onSale)
        }
    }

    /**
     * "Search found nothing" quick-create path — a direct, synchronous REST
     * call (see [CatalogRepository.createProduct]'s doc comment), tied to
     * whichever category was being browsed when this fires (if any).
     */
    fun quickCreate() {
        val name = _query.value.trim()
        if (name.isBlank() || _quickCreating.value) return
        viewModelScope.launch {
            _quickCreating.value = true
            try {
                val product = catalogRepository.createProduct(name = name, catalogCategoryId = _selectedCategory.value?.id)
                _noteInput.value = NoteInputState(product = product)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort: the user can just retry the tap.
            } finally {
                _quickCreating.value = false
            }
        }
    }

    companion object {
        fun factory(listId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                AddProductViewModel(app.container.shoppingListRepository, app.container.catalogRepository, listId)
            }
        }
    }
}
