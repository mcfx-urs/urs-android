package ch.mcfx.urs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.CatalogRepository
import ch.mcfx.urs.data.local.CatalogCategoryEntity
import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.data.remote.CatalogImageDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProductManagementTab { PRODUCTS, CATEGORIES }

data class ProductFormState(
    val editingId: String? = null,
    val name: String = "",
    val categoryId: String? = null,
    val imageId: String? = null,
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
) {
    val isValid: Boolean get() = name.isNotBlank()
}

data class CategoryFormState(
    val editingId: String? = null,
    val name: String = "",
    val imageId: String? = null,
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
) {
    val isValid: Boolean get() = name.isNotBlank()
}

/**
 * Settings → Product Management screen — create/edit/delete for
 * manually-created catalog products and categories. Only ever surfaces
 * `source == "manual"` rows (filtered here, client-side); `external_catalog`-imported
 * rows never appear in either grid, so there's no separate per-row
 * edit/delete-hidden state to manage — being listed here already implies
 * it's editable, matching the backend's own `requireManualSource` guard.
 */
class ProductManagementViewModel(private val repository: CatalogRepository) : ViewModel() {

    init {
        // Same "warm the cache on screen entry" trigger already used by
        // ShoppingListsViewModel/ProductsViewModel/AddProductViewModel —
        // without it, this screen would only ever show whatever another
        // screen happened to have cached already.
        viewModelScope.launch { repository.refreshFromBackend() }
    }

    private val _tab = MutableStateFlow(ProductManagementTab.PRODUCTS)
    val tab: StateFlow<ProductManagementTab> = _tab.asStateFlow()

    val manualProducts: StateFlow<List<CatalogProductEntity>> = repository.observeAll()
        .map { products -> products.filter { it.source == "manual" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val manualCategories: StateFlow<List<CatalogCategoryEntity>> = repository.observeCategories()
        .map { categories -> categories.filter { it.source == "manual" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Unfiltered — a product's category picker offers every category
    // (manual or external-catalog), same as the shopping-list add-product flow.
    val allCategories: StateFlow<List<CatalogCategoryEntity>> = repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _images = MutableStateFlow<List<CatalogImageDto>>(emptyList())
    val images: StateFlow<List<CatalogImageDto>> = _images.asStateFlow()

    private val _actionSheetProduct = MutableStateFlow<CatalogProductEntity?>(null)
    val actionSheetProduct: StateFlow<CatalogProductEntity?> = _actionSheetProduct.asStateFlow()

    private val _actionSheetCategory = MutableStateFlow<CatalogCategoryEntity?>(null)
    val actionSheetCategory: StateFlow<CatalogCategoryEntity?> = _actionSheetCategory.asStateFlow()

    private val _pendingDeleteProduct = MutableStateFlow<CatalogProductEntity?>(null)
    val pendingDeleteProduct: StateFlow<CatalogProductEntity?> = _pendingDeleteProduct.asStateFlow()

    private val _pendingDeleteCategory = MutableStateFlow<CatalogCategoryEntity?>(null)
    val pendingDeleteCategory: StateFlow<CatalogCategoryEntity?> = _pendingDeleteCategory.asStateFlow()

    private val _productForm = MutableStateFlow<ProductFormState?>(null)
    val productForm: StateFlow<ProductFormState?> = _productForm.asStateFlow()

    private val _categoryForm = MutableStateFlow<CategoryFormState?>(null)
    val categoryForm: StateFlow<CategoryFormState?> = _categoryForm.asStateFlow()

    fun selectTab(tab: ProductManagementTab) {
        _tab.value = tab
    }

    fun loadImages() {
        viewModelScope.launch {
            try {
                _images.value = repository.getImages()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort only — image picker just stays empty if this fetch failed.
            }
        }
    }

    fun openCreateForm() {
        when (_tab.value) {
            ProductManagementTab.PRODUCTS -> _productForm.value = ProductFormState()
            ProductManagementTab.CATEGORIES -> _categoryForm.value = CategoryFormState()
        }
    }

    fun openProductActionSheet(product: CatalogProductEntity) {
        _actionSheetProduct.value = product
    }

    fun closeProductActionSheet() {
        _actionSheetProduct.value = null
    }

    fun editProductFromActionSheet() {
        val product = _actionSheetProduct.value ?: return
        _actionSheetProduct.value = null
        _productForm.value = ProductFormState(
            editingId = product.id,
            name = product.name,
            categoryId = product.catalogCategoryId,
            imageId = product.catalogImageId?.toString(),
        )
    }

    fun requestDeleteProduct() {
        val product = _actionSheetProduct.value ?: return
        _actionSheetProduct.value = null
        _pendingDeleteProduct.value = product
    }

    fun cancelDeleteProduct() {
        _pendingDeleteProduct.value = null
    }

    fun confirmDeleteProduct() {
        val product = _pendingDeleteProduct.value ?: return
        _pendingDeleteProduct.value = null
        viewModelScope.launch {
            try {
                repository.deleteProduct(product.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort, same as the rest of this repository's direct-REST
                // calls — the row simply stays visible if the delete failed, and
                // the user can retry the long-press.
            }
        }
    }

    fun openCategoryActionSheet(category: CatalogCategoryEntity) {
        _actionSheetCategory.value = category
    }

    fun closeCategoryActionSheet() {
        _actionSheetCategory.value = null
    }

    fun editCategoryFromActionSheet() {
        val category = _actionSheetCategory.value ?: return
        _actionSheetCategory.value = null
        _categoryForm.value = CategoryFormState(
            editingId = category.id,
            name = category.name,
            imageId = category.catalogImageId?.toString(),
        )
    }

    fun requestDeleteCategory() {
        val category = _actionSheetCategory.value ?: return
        _actionSheetCategory.value = null
        _pendingDeleteCategory.value = category
    }

    fun cancelDeleteCategory() {
        _pendingDeleteCategory.value = null
    }

    fun confirmDeleteCategory() {
        val category = _pendingDeleteCategory.value ?: return
        _pendingDeleteCategory.value = null
        viewModelScope.launch {
            try {
                repository.deleteCategory(category.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort — see confirmDeleteProduct.
            }
        }
    }

    fun setProductName(name: String) {
        _productForm.update { it?.copy(name = name) }
    }

    fun setProductCategory(categoryId: String?) {
        _productForm.update { it?.copy(categoryId = categoryId) }
    }

    fun setProductImage(imageId: String?) {
        _productForm.update { it?.copy(imageId = imageId) }
    }

    fun closeProductForm() {
        _productForm.value = null
    }

    fun submitProductForm() {
        val form = _productForm.value ?: return
        if (!form.isValid || form.submitting) return
        viewModelScope.launch {
            _productForm.update { it?.copy(submitting = true, submitFailed = false) }
            try {
                val name = form.name.trim()
                if (form.editingId != null) {
                    repository.updateProduct(form.editingId, name, form.categoryId, form.imageId)
                } else {
                    val created = repository.createProduct(name, form.categoryId)
                    // postCatalogProduct doesn't accept an image, so a
                    // create-with-image needs this follow-up PUT.
                    if (form.imageId != null) {
                        repository.updateProduct(created.id, name, form.categoryId, form.imageId)
                    }
                }
                _productForm.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _productForm.update { it?.copy(submitting = false, submitFailed = true) }
            }
        }
    }

    fun setCategoryName(name: String) {
        _categoryForm.update { it?.copy(name = name) }
    }

    fun setCategoryImage(imageId: String?) {
        _categoryForm.update { it?.copy(imageId = imageId) }
    }

    fun closeCategoryForm() {
        _categoryForm.value = null
    }

    fun submitCategoryForm() {
        val form = _categoryForm.value ?: return
        if (!form.isValid || form.submitting) return
        viewModelScope.launch {
            _categoryForm.update { it?.copy(submitting = true, submitFailed = false) }
            try {
                val name = form.name.trim()
                if (form.editingId != null) {
                    repository.updateCategory(form.editingId, name, form.imageId)
                } else {
                    val created = repository.createCategory(name)
                    // postCatalogCategory doesn't accept an image either — same
                    // create-then-attach shape as submitProductForm.
                    if (form.imageId != null) {
                        repository.updateCategory(created.id, name, form.imageId)
                    }
                }
                _categoryForm.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _categoryForm.update { it?.copy(submitting = false, submitFailed = true) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                ProductManagementViewModel(app.container.catalogRepository)
            }
        }
    }
}
