package ch.mcfx.urs.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.InventoryRepository
import ch.mcfx.urs.data.local.InventoryCategoryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CategoriesUiState {
    data object Loading : CategoriesUiState
    data class Data(val categories: List<InventoryCategoryEntity>) : CategoriesUiState
}

data class CategoryFormState(
    val name: String = "",
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
) {
    val isValid: Boolean get() = name.isNotBlank()
}

class CategoriesViewModel(private val repository: InventoryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<CategoriesUiState>(CategoriesUiState.Loading)
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(CategoryFormState())
    val formState: StateFlow<CategoryFormState> = _formState.asStateFlow()

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    init {
        // Room-backed Flow, so this screen has something to show even on a
        // cold start with no connectivity — load() below only refreshes the
        // cache opportunistically (see FuelViewModel for the same shape).
        viewModelScope.launch {
            repository.observeCategories().collect { _uiState.value = CategoriesUiState.Data(it) }
        }
        load()
    }

    fun load() {
        viewModelScope.launch { repository.refreshFromBackend() }
    }

    fun openForm() {
        _formState.value = CategoryFormState()
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
                repository.createCategory(name = form.name.trim())
                // createCategory is a local-only write and returns
                // instantly — no network round-trip to wait on, so the
                // form can close right away (see FuelViewModel.submit).
                _showForm.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _formState.update { it.copy(submitting = false, submitFailed = true) }
            }
        }
    }

    fun deleteCategory(category: InventoryCategoryEntity) {
        // Delete stays a direct network call in this phase (see
        // InventoryRepository.deleteCategory) — nothing to delete
        // server-side yet for a category that hasn't synced.
        val serverId = category.serverId ?: return
        viewModelScope.launch {
            try {
                repository.deleteCategory(serverId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort: the list simply keeps showing the category if the
                // delete failed server-side; the user can just retry the tap.
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                CategoriesViewModel(app.container.inventoryRepository)
            }
        }
    }
}
