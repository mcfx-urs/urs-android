package ch.mcfx.urs.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.InventoryRepository
import ch.mcfx.urs.data.remote.InventoryCategoryDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CategoriesUiState {
    data object Loading : CategoriesUiState
    data class Error(val message: String) : CategoriesUiState
    data class Data(val categories: List<InventoryCategoryDto>) : CategoriesUiState
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
        load()
    }

    fun load() {
        _uiState.value = CategoriesUiState.Loading
        viewModelScope.launch {
            try {
                _uiState.value = CategoriesUiState.Data(repository.getCategories())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = CategoriesUiState.Error(e.message ?: "unknown")
            }
        }
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
                _showForm.value = false
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _formState.update { it.copy(submitting = false, submitFailed = true) }
            }
        }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteCategory(id)
                load()
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
