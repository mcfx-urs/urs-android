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
import ch.mcfx.urs.data.local.ListEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ShoppingListsUiState {
    data object Loading : ShoppingListsUiState
    data class Data(val lists: List<ListEntity>) : ShoppingListsUiState
}

data class ListFormState(
    // null = creating a new list; set = renaming this local row.
    val editingListId: Long? = null,
    val name: String = "",
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
) {
    val isValid: Boolean get() = name.isNotBlank()
}

class ShoppingListsViewModel(
    private val repository: ShoppingListRepository,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ShoppingListsUiState>(ShoppingListsUiState.Loading)
    val uiState: StateFlow<ShoppingListsUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(ListFormState())
    val formState: StateFlow<ListFormState> = _formState.asStateFlow()

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    // Long-press → Rename/Delete, same shape as WorkTimeViewModel's entry action sheet.
    private val _actionSheetList = MutableStateFlow<ListEntity?>(null)
    val actionSheetList: StateFlow<ListEntity?> = _actionSheetList.asStateFlow()

    private val _pendingDeleteList = MutableStateFlow<ListEntity?>(null)
    val pendingDeleteList: StateFlow<ListEntity?> = _pendingDeleteList.asStateFlow()

    init {
        // Room-backed Flow, same shape as CategoriesViewModel — load() below
        // only refreshes the caches opportunistically.
        viewModelScope.launch {
            repository.observeLists().collect { _uiState.value = ShoppingListsUiState.Data(it) }
        }
        load()
    }

    fun load() {
        viewModelScope.launch { repository.refreshFromBackend() }
        // First natural place this fires (see CatalogRepository's own doc
        // comment) — the shopping-list hub is the earliest screen in this
        // feature's own flow, so the catalog cache is already warm by the
        // time AddProductScreen's search actually needs it.
        viewModelScope.launch { catalogRepository.refreshFromBackend() }
    }

    fun openCreateForm() {
        _formState.value = ListFormState()
        _showForm.value = true
    }

    fun openRenameForm(list: ListEntity) {
        _formState.value = ListFormState(editingListId = list.id, name = list.name)
        _actionSheetList.value = null
        _showForm.value = true
    }

    fun closeForm() {
        _showForm.value = false
    }

    fun setName(value: String) = _formState.update { it.copy(name = value) }

    fun submit() {
        val form = _formState.value
        if (!form.isValid || form.submitting) return

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, submitFailed = false) }
            try {
                val editingId = form.editingListId
                if (editingId != null) {
                    repository.renameList(editingId, form.name.trim())
                } else {
                    repository.createList(form.name.trim())
                }
                // Both paths are local-only writes that return instantly —
                // no network round-trip to wait on, so the form can close
                // right away (see CategoriesViewModel.submit).
                _showForm.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _formState.update { it.copy(submitting = false, submitFailed = true) }
            }
        }
    }

    fun openActionSheet(list: ListEntity) {
        _actionSheetList.value = list
    }

    fun closeActionSheet() {
        _actionSheetList.value = null
    }

    fun requestDelete() {
        val list = _actionSheetList.value ?: return
        _actionSheetList.value = null
        _pendingDeleteList.value = list
    }

    fun cancelDelete() {
        _pendingDeleteList.value = null
    }

    fun confirmDelete() {
        val list = _pendingDeleteList.value ?: return
        _pendingDeleteList.value = null
        // Local delete inside repository.deleteList is immediate and
        // effectively can't fail — no submitting/failure UI state needed
        // here, unlike the form's submit() (see WorkTimeViewModel.confirmDelete).
        viewModelScope.launch { repository.deleteList(list.id) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                ShoppingListsViewModel(app.container.shoppingListRepository, app.container.catalogRepository)
            }
        }
    }
}
