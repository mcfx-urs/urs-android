package ch.mcfx.urs.shoppinglist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.ShoppingListItemDetail
import ch.mcfx.urs.data.ShoppingListRepository
import ch.mcfx.urs.data.alphabeticSortKey
import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.data.local.publicId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One category's items, already sorted. */
data class ShoppingListCategoryGroup(val categoryName: String, val items: List<ShoppingListItemDetail>)

sealed interface ListDetailUiState {
    data object Loading : ListDetailUiState
    data class Data(
        val listName: String,
        val groups: List<ShoppingListCategoryGroup>,
        val recentlyUsed: List<CatalogProductEntity>,
    ) : ListDetailUiState
}

data class AddNoteFormState(
    val item: ShoppingListItemDetail? = null,
    val note: String = "",
    val quantity: Int? = null,
    val onSale: Boolean = false,
)

/**
 * Tile-grid list detail — groups by `catalog_category_name` (empty
 * → an "Ohne Kategorie" fallback group), no more checked/purchased state at
 * all (that concept is gone server-side too, see [ShoppingListItemDetail]'s
 * doc comment): a tap on a tile removes it from the list — the tile grid
 * *is* the list, there's no separate check action. A "recently used" tail
 * section (same data source as [ch.mcfx.urs.shoppinglist.AddProductViewModel]'s
 * ZULETZT tab, `GET /recently-used-product`) lets adding a recent product
 * back onto this list happen without opening the full add-product sheet.
 */
class ListDetailViewModel(
    private val repository: ShoppingListRepository,
    private val listId: String,
    private val uncategorizedLabel: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ListDetailUiState>(ListDetailUiState.Loading)
    val uiState: StateFlow<ListDetailUiState> = _uiState.asStateFlow()

    private val _showAddProduct = MutableStateFlow(false)
    val showAddProduct: StateFlow<Boolean> = _showAddProduct.asStateFlow()

    private val _noteForm = MutableStateFlow(AddNoteFormState())
    val noteForm: StateFlow<AddNoteFormState> = _noteForm.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeLists(),
                repository.observeItems(listId),
                repository.observeRecentlyUsed(listId),
            ) { lists, items, recentlyUsed ->
                val listName = lists.find { it.publicId == listId }?.name.orEmpty()
                val groups = items
                    .groupBy { it.categoryName.ifBlank { uncategorizedLabel } }
                    .entries
                    .sortedBy { it.key.alphabeticSortKey() }
                    .map { (categoryName, groupItems) ->
                        ShoppingListCategoryGroup(
                            categoryName = categoryName,
                            items = groupItems.sortedBy { it.productName.alphabeticSortKey() },
                        )
                    }
                ListDetailUiState.Data(listName = listName, groups = groups, recentlyUsed = recentlyUsed)
            }.collect { _uiState.value = it }
        }
        viewModelScope.launch { repository.refreshFromBackend() }
        viewModelScope.launch { repository.refreshRecentlyUsed(listId) }
    }

    /** Tap a tile = remove it from the list — no separate check action any more. */
    fun removeItem(detail: ShoppingListItemDetail) {
        viewModelScope.launch { repository.deleteItem(detail.item.id) }
    }

    /** Tap a "recently used" tile = add it back onto this list. */
    fun addRecentlyUsed(product: CatalogProductEntity) {
        viewModelScope.launch { repository.addExistingProduct(listId, product.id, note = null) }
    }

    fun openNoteForm(detail: ShoppingListItemDetail) {
        _noteForm.value = AddNoteFormState(
            item = detail,
            note = detail.item.note.orEmpty(),
            quantity = detail.item.quantity,
            onSale = detail.item.onSale,
        )
    }

    fun setNote(value: String) = _noteForm.update { it.copy(note = value) }

    fun incrementQuantity() = _noteForm.update { it.copy(quantity = (it.quantity ?: 0) + 1) }

    // Same "decrement below 1 clears to unset" rule as AddProductViewModel.
    fun decrementQuantity() = _noteForm.update { form ->
        val current = form.quantity ?: return@update form
        form.copy(quantity = if (current <= 1) null else current - 1)
    }

    fun toggleOnSale() = _noteForm.update { it.copy(onSale = !it.onSale) }

    fun closeNoteForm() {
        _noteForm.value = AddNoteFormState()
    }

    fun saveNote() {
        val form = _noteForm.value
        val detail = form.item ?: return
        viewModelScope.launch {
            repository.updateItem(
                detail.item.id,
                note = form.note.trim().ifEmpty { null },
                quantity = form.quantity,
                onSale = form.onSale,
            )
        }
        _noteForm.value = AddNoteFormState()
    }

    fun openAddProduct() {
        _showAddProduct.value = true
    }

    fun closeAddProduct() {
        _showAddProduct.value = false
    }

    companion object {
        fun factory(listId: String, uncategorizedLabel: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                ListDetailViewModel(app.container.shoppingListRepository, listId, uncategorizedLabel)
            }
        }
    }
}
