package ch.mcfx.urs.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.InventoryRepository
import ch.mcfx.urs.data.remote.InventoryProductDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProductsUiState {
    data object Loading : ProductsUiState
    data class Error(val message: String) : ProductsUiState
    data class Data(val products: List<InventoryProductDto>) : ProductsUiState
}

data class ProductFormState(
    val name: String = "",
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
) {
    val isValid: Boolean get() = name.isNotBlank()
}

class ProductsViewModel(
    private val repository: InventoryRepository,
    private val categoryId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProductsUiState>(ProductsUiState.Loading)
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(ProductFormState())
    val formState: StateFlow<ProductFormState> = _formState.asStateFlow()

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = ProductsUiState.Loading
        viewModelScope.launch {
            try {
                _uiState.value = ProductsUiState.Data(repository.getProducts(categoryId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = ProductsUiState.Error(e.message ?: "unknown")
            }
        }
    }

    fun openForm() {
        _formState.value = ProductFormState()
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
                repository.createProduct(categoryId = categoryId, name = form.name.trim())
                _showForm.value = false
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _formState.update { it.copy(submitting = false, submitFailed = true) }
            }
        }
    }

    fun increment(product: InventoryProductDto) = adjustQuantity(product, +1)

    fun decrement(product: InventoryProductDto) = adjustQuantity(product, -1)

    // Optimistic: the row updates immediately so the stepper feels instant;
    // if the backend call fails, load() reconciles the list back to the
    // real server state rather than leaving a stale local value around.
    private fun adjustQuantity(product: InventoryProductDto, delta: Int) {
        val currentQuantity = product.quantity.toIntOrNull() ?: 0
        val newQuantity = (currentQuantity + delta).coerceAtLeast(0)
        if (newQuantity == currentQuantity) return

        val state = _uiState.value
        if (state !is ProductsUiState.Data) return
        _uiState.value = ProductsUiState.Data(
            state.products.map { if (it.id == product.id) it.copy(quantity = newQuantity.toString()) else it },
        )

        viewModelScope.launch {
            try {
                repository.updateProductQuantity(product, newQuantity)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                load()
            }
        }
    }

    fun deleteProduct(id: String) {
        viewModelScope.launch {
            try {
                repository.deleteProduct(id)
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort: the list simply keeps showing the product if the
                // delete failed server-side; the user can just retry the tap.
            }
        }
    }

    companion object {
        fun factory(categoryId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                ProductsViewModel(app.container.inventoryRepository, categoryId)
            }
        }
    }
}
