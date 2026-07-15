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
import ch.mcfx.urs.data.local.publicId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One category's items, already sorted — see [ListDetailViewModel]'s "recently used" doc comment. */
data class ShoppingListCategoryGroup(val categoryName: String, val items: List<ShoppingListItemDetail>)

sealed interface ListDetailUiState {
    data object Loading : ListDetailUiState
    data class Data(val listName: String, val groups: List<ShoppingListCategoryGroup>) : ListDetailUiState
}

data class AddNoteFormState(
    val item: ShoppingListItemDetail? = null,
    val note: String = "",
)

/**
 * "Recently used" placement design call: a checked item sinks to the bottom
 * of its own category group rather than into one separate cross-category
 * section at the bottom of the whole list — a checked item stays visually
 * near the other items of the same kind (e.g. still under "Drinks"), just
 * de-prioritized within it, rather than losing that context in an
 * undifferentiated pile once several categories have checked items at once.
 */
class ListDetailViewModel(
    private val repository: ShoppingListRepository,
    private val listId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ListDetailUiState>(ListDetailUiState.Loading)
    val uiState: StateFlow<ListDetailUiState> = _uiState.asStateFlow()

    private val _showAddProduct = MutableStateFlow(false)
    val showAddProduct: StateFlow<Boolean> = _showAddProduct.asStateFlow()

    private val _noteForm = MutableStateFlow(AddNoteFormState())
    val noteForm: StateFlow<AddNoteFormState> = _noteForm.asStateFlow()

    init {
        viewModelScope.launch {
            combine(repository.observeLists(), repository.observeItems(listId)) { lists, items ->
                val listName = lists.find { it.publicId == listId }?.name.orEmpty()
                val groups = items
                    .groupBy { it.categoryName }
                    .entries
                    .sortedBy { it.key.alphabeticSortKey() }
                    .map { (categoryName, groupItems) ->
                        ShoppingListCategoryGroup(
                            categoryName = categoryName,
                            items = groupItems.sortedWith(
                                compareBy({ it.item.checked }, { it.productName.alphabeticSortKey() }),
                            ),
                        )
                    }
                ListDetailUiState.Data(listName = listName, groups = groups)
            }.collect { _uiState.value = it }
        }
        viewModelScope.launch { repository.refreshFromBackend() }
    }

    /** Tap-to-toggle — the only per-item interaction this screen itself offers (editing a note happens via [openNoteForm]). */
    fun toggleChecked(detail: ShoppingListItemDetail) {
        viewModelScope.launch {
            repository.updateItem(detail.item.id, note = detail.item.note, checked = !detail.item.checked)
        }
    }

    fun openNoteForm(detail: ShoppingListItemDetail) {
        _noteForm.value = AddNoteFormState(item = detail, note = detail.item.note.orEmpty())
    }

    fun setNote(value: String) = _noteForm.update { it.copy(note = value) }

    fun closeNoteForm() {
        _noteForm.value = AddNoteFormState()
    }

    fun saveNote() {
        val form = _noteForm.value
        val detail = form.item ?: return
        viewModelScope.launch {
            repository.updateItem(detail.item.id, note = form.note.trim().ifEmpty { null }, checked = detail.item.checked)
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
        fun factory(listId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                ListDetailViewModel(app.container.shoppingListRepository, listId)
            }
        }
    }
}
