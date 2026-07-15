package ch.mcfx.urs.shoppinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.CatalogRepository
import ch.mcfx.urs.data.InventoryRepository
import ch.mcfx.urs.data.ShoppingListRepository
import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.data.local.InventoryCategoryEntity
import ch.mcfx.urs.data.local.InventoryProductEntity
import ch.mcfx.urs.data.local.localIdStandIn
import ch.mcfx.urs.data.local.publicId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One search result row — either a household product already tracked in
 * inventory, or a predefined catalog entry not yet added to the household.
 * A catalog entry that's already linked to a household product (see
 * [InventoryProductEntity.catalogProductId]) is filtered out of the catalog
 * half of the search rather than shown twice — see [AddProductViewModel]'s
 * `init`.
 */
sealed interface ProductSearchResult {
    val name: String

    data class Household(val product: InventoryProductEntity) : ProductSearchResult {
        override val name get() = product.name
    }

    data class Catalog(val product: CatalogProductEntity) : ProductSearchResult {
        override val name get() = product.name
    }
}

data class NoteInputState(val result: ProductSearchResult, val note: String = "")

data class CustomProductFormState(
    val name: String = "",
    val category: InventoryCategoryEntity? = null,
    val submitting: Boolean = false,
) {
    val isValid: Boolean get() = name.isNotBlank() && category != null
}

class AddProductViewModel(
    private val shoppingListRepository: ShoppingListRepository,
    private val catalogRepository: CatalogRepository,
    private val inventoryRepository: InventoryRepository,
    private val listId: String,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<ProductSearchResult>>(emptyList())
    val results: StateFlow<List<ProductSearchResult>> = _results.asStateFlow()

    private val _noteInput = MutableStateFlow<NoteInputState?>(null)
    val noteInput: StateFlow<NoteInputState?> = _noteInput.asStateFlow()

    private val _categories = MutableStateFlow<List<InventoryCategoryEntity>>(emptyList())
    val categories: StateFlow<List<InventoryCategoryEntity>> = _categories.asStateFlow()

    private val _showCustomForm = MutableStateFlow(false)
    val showCustomForm: StateFlow<Boolean> = _showCustomForm.asStateFlow()

    private val _customForm = MutableStateFlow(CustomProductFormState())
    val customForm: StateFlow<CustomProductFormState> = _customForm.asStateFlow()

    init {
        // collectLatest (not flatMapLatest) so a fast typist's stale
        // in-flight combine() is cancelled cleanly without an experimental
        // coroutines opt-in this codebase doesn't otherwise use anywhere.
        viewModelScope.launch {
            _query.collectLatest { q ->
                if (q.isBlank()) {
                    _results.value = emptyList()
                    return@collectLatest
                }
                combine(catalogRepository.search(q), inventoryRepository.searchProducts(q)) { catalog, household ->
                    val linkedCatalogIds = household.mapNotNull { it.catalogProductId }.toSet()
                    household.map { ProductSearchResult.Household(it) } +
                        catalog.filterNot { it.id in linkedCatalogIds }.map { ProductSearchResult.Catalog(it) }
                }.collect { _results.value = it }
            }
        }
        viewModelScope.launch {
            inventoryRepository.observeCategories().collect { _categories.value = it }
        }
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun selectResult(result: ProductSearchResult) {
        _noteInput.value = NoteInputState(result = result)
    }

    fun setNote(value: String) = _noteInput.update { it?.copy(note = value) }

    fun closeNoteInput() {
        _noteInput.value = null
    }

    fun confirmAdd() {
        val state = _noteInput.value ?: return
        val note = state.note.trim().ifEmpty { null }
        _noteInput.value = null
        viewModelScope.launch {
            when (val result = state.result) {
                is ProductSearchResult.Catalog -> shoppingListRepository.addCatalogProduct(listId, result.product, note)
                is ProductSearchResult.Household -> shoppingListRepository.addExistingProduct(listId, result.product.publicId, note)
            }
        }
    }

    fun openCustomForm() {
        _customForm.value = CustomProductFormState()
        _showCustomForm.value = true
    }

    fun closeCustomForm() {
        _showCustomForm.value = false
    }

    fun setCustomName(value: String) = _customForm.update { it.copy(name = value) }

    fun setCustomCategory(category: InventoryCategoryEntity) = _customForm.update { it.copy(category = category) }

    fun submitCustom() {
        val form = _customForm.value
        val category = form.category
        if (!form.isValid || category == null || form.submitting) return

        viewModelScope.launch {
            _customForm.update { it.copy(submitting = true) }
            try {
                val localProductId = inventoryRepository.createProduct(
                    categoryId = category.publicId,
                    name = form.name.trim(),
                    quantity = null,
                )
                shoppingListRepository.addExistingProduct(listId, localIdStandIn(localProductId), null)
                _showCustomForm.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _customForm.update { it.copy(submitting = false) }
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
                    app.container.inventoryRepository,
                    listId,
                )
            }
        }
    }
}
