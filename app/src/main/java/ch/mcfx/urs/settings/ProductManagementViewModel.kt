package ch.mcfx.urs.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.BarcodeLookup
import ch.mcfx.urs.data.CatalogRepository
import ch.mcfx.urs.data.DEFAULT_SIZE_UNIT
import ch.mcfx.urs.data.parseQuantity
import ch.mcfx.urs.data.local.CatalogCategoryEntity
import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.data.remote.apiError
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

private const val TAG = "ProductManagementVM"

enum class ProductManagementTab { PRODUCTS, CATEGORIES, CATALOG_SEARCH }

/**
 * Coarse classification of a failed [ProductManagementViewModel.generateProductImage]
 * call — drives which message [CatalogProductFormSheet] shows in its
 * [ch.mcfx.urs.ui.components.UrsErrorDialog], since "no network" and "server/AI
 * provider rejected the request" call for different user-facing wording.
 */
enum class ImageGenerationErrorKind { NETWORK, SERVER, UNKNOWN }

data class ProductFormState(
    val editingId: String? = null,
    // Only manually created products offer delete; imported rows opened from the catalog search do not.
    val deletable: Boolean = false,
    val name: String = "",
    // Free-text prompt detail for image generation only — never
    // sent to createProduct/updateProduct, never persisted on the product
    // itself, just shapes the next generateProductImage() call.
    val description: String = "",
    val categoryId: String? = null,
    val imageId: String? = null,
    // Set = this product is a clone of that template: name and category are
    // then inherited (hidden in the form) and only brand, size and an
    // optional own image are the clone's own.
    val templateId: String? = null,
    val templateName: String? = null,
    // Image the clone inherits when it has none of its own.
    val templateImageId: Int? = null,
    val brand: String = "",
    val sizeAmount: String = "",
    val sizeUnit: String = DEFAULT_SIZE_UNIT,
    val barcodes: List<String> = emptyList(),
    val barcodeInput: String = "",
    val barcodeLookupRunning: Boolean = false,
    // Name of the existing product a scanned barcode already belongs to.
    val barcodeTakenBy: String? = null,
    // Backend message of the last failed save or barcode change, shown as received.
    val errorMessage: String? = null,
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
    // Distinct from submitFailed: a 409 from createProduct's
    // requireNew=true means a product with this exact name already
    // exists (from the external catalog or manual) — a specific, actionable message rather
    // than the generic "failed to save".
    val nameConflict: Boolean = false,
    val generatingImage: Boolean = false,
    val generateImageError: ImageGenerationErrorKind? = null,
) {
    val isClone: Boolean get() = templateId != null

    // Name of the image prompt: a clone has no name of its own to describe.
    val imagePromptName: String get() = if (isClone) "${templateName.orEmpty()} $brand".trim() else name.trim()

    val isValid: Boolean
        get() = if (isClone) {
            (brand.isNotBlank() || sizeAmount.isNotBlank()) && (sizeAmount.isBlank() || sizeAmountValue != null)
        } else {
            name.isNotBlank()
        }

    // Positive decimal amount ("," accepted), null if blank or not a valid amount.
    val sizeAmountValue: Double?
        get() = sizeAmount.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
}

/** A refused delete: the backend message plus, for a template, the names of all clones. */
data class DeleteBlocked(val message: String, val clones: List<String> = emptyList())

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
 * manually-created catalog products and categories. The PRODUCTS/CATEGORIES
 * grids only ever surface `source == "manual"` rows (filtered here,
 * client-side) — being listed there already implies it's editable, matching
 * the backend's own `requireManualSource` guard on delete. The separate,
 * super-user-only CATALOG_SEARCH tab (`catalogSearchResults` below) is the
 * exception: it deliberately searches the *unfiltered* catalog so a
 * `external_catalog`-imported row can be found and edited too, which the backend now
 * allows for a super user (`requireEditableSource`) — editing it locks that
 * row against the next external_catalog import (`catalog_product_is_locked`).
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

    // Super-user-only "Katalog" tab (catalog_product_is_locked) — unlike
    // manualProducts/manualCategories above, deliberately unfiltered by
    // source, since the whole point is finding an externally-imported row to lock+edit.
    // Same search primitive AddProductViewModel already uses for its picker
    // (Room LIKE across name/searchTerms/brands), just without that
    // screen's "manual quick-add" framing.
    private val _catalogSearchQuery = MutableStateFlow("")
    val catalogSearchQuery: StateFlow<String> = _catalogSearchQuery.asStateFlow()

    val catalogSearchResults: StateFlow<List<CatalogProductEntity>> = _catalogSearchQuery
        .flatMapLatest { query -> if (query.isBlank()) flowOf(emptyList()) else repository.search(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setCatalogSearchQuery(query: String) {
        _catalogSearchQuery.value = query
    }

    // Opens the same edit form a tapped manual product tile uses
    // (editProduct below) — the backend now allows a super
    // user to PUT a non-manual row too (putCatalogProduct), locking it
    // against the next external-catalog import. No client-side gate here beyond the
    // UI itself only offering this tab to a super user in the first place;
    // the backend is the actual enforcement point.
    fun editCatalogSearchResult(product: CatalogProductEntity) {
        openEditForm(product)
    }

    private fun openEditForm(product: CatalogProductEntity) {
        _productForm.value = ProductFormState(
            editingId = product.id,
            deletable = product.source == "manual",
            name = product.name,
            categoryId = product.catalogCategoryId,
            imageId = product.catalogImageId?.toString(),
            templateId = product.templateId,
            brand = product.brands.orEmpty(),
            sizeAmount = product.sizeAmount.orEmpty(),
            sizeUnit = product.sizeUnit ?: DEFAULT_SIZE_UNIT,
            barcodes = product.barcodeList,
        )
        product.templateId?.let { templateId ->
            viewModelScope.launch {
                val template = repository.getProduct(templateId)
                _productForm.update { form ->
                    form?.takeIf { it.editingId == product.id }
                        ?.copy(templateName = template?.name, templateImageId = template?.catalogImageId) ?: form
                }
            }
        }
    }

    private val _pendingDeleteProduct = MutableStateFlow<CatalogProductEntity?>(null)
    val pendingDeleteProduct: StateFlow<CatalogProductEntity?> = _pendingDeleteProduct.asStateFlow()

    private val _pendingDeleteCategory = MutableStateFlow<CatalogCategoryEntity?>(null)
    val pendingDeleteCategory: StateFlow<CatalogCategoryEntity?> = _pendingDeleteCategory.asStateFlow()

    private val _deleteBlocked = MutableStateFlow<DeleteBlocked?>(null)
    val deleteBlocked: StateFlow<DeleteBlocked?> = _deleteBlocked.asStateFlow()

    private val _templateQuery = MutableStateFlow("")
    val templateQuery: StateFlow<String> = _templateQuery.asStateFlow()

    private val _templateResults = MutableStateFlow<List<CatalogProductEntity>>(emptyList())
    val templateResults: StateFlow<List<CatalogProductEntity>> = _templateResults.asStateFlow()

    private val _productForm = MutableStateFlow<ProductFormState?>(null)
    val productForm: StateFlow<ProductFormState?> = _productForm.asStateFlow()

    private val _categoryForm = MutableStateFlow<CategoryFormState?>(null)
    val categoryForm: StateFlow<CategoryFormState?> = _categoryForm.asStateFlow()

    fun selectTab(tab: ProductManagementTab) {
        _tab.value = tab
    }

    suspend fun searchImages(query: String, offset: Int) = repository.searchImages(query, offset)

    fun openCreateForm() {
        when (_tab.value) {
            ProductManagementTab.PRODUCTS -> _productForm.value = ProductFormState()
            ProductManagementTab.CATEGORIES -> _categoryForm.value = CategoryFormState()
            // No "create" affordance on the search tab — the screen hides
            // the FAB there instead of calling this.
            ProductManagementTab.CATALOG_SEARCH -> {}
        }
    }

    fun editProduct(product: CatalogProductEntity) {
        openEditForm(product)
    }

    /** Asks for confirmation to delete the product open in the edit form. */
    fun requestDeleteProduct() {
        val id = _productForm.value?.editingId ?: return
        viewModelScope.launch { _pendingDeleteProduct.value = repository.getProduct(id) }
    }

    fun cancelDeleteProduct() {
        _pendingDeleteProduct.value = null
    }

    fun confirmDeleteProduct() {
        val product = _pendingDeleteProduct.value ?: return
        _pendingDeleteProduct.value = null
        _productForm.value = null
        viewModelScope.launch {
            try {
                repository.deleteProduct(product.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                val error = e.apiError()
                _deleteBlocked.value = DeleteBlocked(
                    message = error?.error?.takeIf { it.isNotBlank() } ?: "",
                    clones = error?.clones?.map { it.name }.orEmpty(),
                )
            } catch (_: Exception) {
                // Best-effort, same as the rest of this repository's direct-REST
                // calls — the row simply stays visible if the delete failed, and
                // the user can reopen it and retry.
            }
        }
    }

    fun dismissDeleteBlocked() {
        _deleteBlocked.value = null
    }

    fun editCategory(category: CatalogCategoryEntity) {
        _categoryForm.value = CategoryFormState(
            editingId = category.id,
            name = category.name,
            imageId = category.catalogImageId?.toString(),
        )
    }

    /** Asks for confirmation to delete the category open in the edit form. */
    fun requestDeleteCategory() {
        val id = _categoryForm.value?.editingId ?: return
        _pendingDeleteCategory.value = manualCategories.value.firstOrNull { it.id == id }
    }

    fun cancelDeleteCategory() {
        _pendingDeleteCategory.value = null
    }

    fun confirmDeleteCategory() {
        val category = _pendingDeleteCategory.value ?: return
        _pendingDeleteCategory.value = null
        _categoryForm.value = null
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

    fun setProductDescription(description: String) {
        _productForm.update { it?.copy(description = description) }
    }

    fun setTemplateQuery(query: String) {
        _templateQuery.value = query
        viewModelScope.launch {
            _templateResults.value = if (query.isBlank()) emptyList() else repository.searchTemplates(query)
        }
    }

    /** Make the form a clone of [template], or a standalone product again with null. */
    fun setTemplate(template: CatalogProductEntity?) {
        _templateQuery.value = ""
        _templateResults.value = emptyList()
        _productForm.update {
            it?.copy(
                templateId = template?.id,
                templateName = template?.name,
                templateImageId = template?.catalogImageId,
                brand = if (template == null) "" else it.brand,
                sizeAmount = if (template == null) "" else it.sizeAmount,
                errorMessage = null,
                nameConflict = false,
            )
        }
    }

    fun setBrand(brand: String) {
        _productForm.update { it?.copy(brand = brand) }
    }

    fun setSizeAmount(amount: String) {
        _productForm.update { it?.copy(sizeAmount = amount) }
    }

    fun setSizeUnit(unit: String) {
        _productForm.update { it?.copy(sizeUnit = unit) }
    }

    fun setBarcodeInput(input: String) {
        _productForm.update { it?.copy(barcodeInput = input) }
    }

    /** A barcode read by the camera is added like a typed one. */
    fun addScannedBarcode(barcode: String) {
        setBarcodeInput(barcode)
        addBarcode()
    }

    /**
     * Adds the typed barcode. On an existing product it is mapped on the
     * backend right away (which rejects one owned by another product with a
     * message naming it); on a new product it is kept in the form and sent
     * with the save.
     */
    fun addBarcode() {
        val form = _productForm.value ?: return
        val barcode = form.barcodeInput.trim()
        if (barcode.isEmpty() || barcode in form.barcodes) return
        val editingId = form.editingId
        if (editingId == null) {
            _productForm.update { it?.copy(barcodes = it.barcodes + barcode, barcodeInput = "", errorMessage = null, barcodeTakenBy = null) }
            prefillFromBarcode(barcode)
            return
        }
        viewModelScope.launch {
            try {
                repository.addBarcode(editingId, barcode)
                _productForm.update { it?.copy(barcodes = it.barcodes + barcode, barcodeInput = "", errorMessage = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _productForm.update { it?.copy(errorMessage = errorMessageOf(e)) }
            }
        }
    }

    /**
     * Looks the barcode of a new product up: fills the still empty name, brand
     * and size from the Open Food Facts suggestion, or reports the product the
     * barcode already belongs to. A failed or empty lookup changes nothing.
     */
    private fun prefillFromBarcode(barcode: String) {
        _productForm.update { it?.copy(barcodeLookupRunning = true) }
        viewModelScope.launch {
            val result = try {
                repository.lookupByBarcode(barcode)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                BarcodeLookup.Failed
            }
            _productForm.update { form ->
                if (form == null || barcode !in form.barcodes) return@update form?.copy(barcodeLookupRunning = false)
                when (result) {
                    is BarcodeLookup.Found -> form.copy(barcodeLookupRunning = false, barcodeTakenBy = result.product.name)
                    is BarcodeLookup.Unknown -> {
                        val suggestion = result.suggestion
                        val size = suggestion?.quantity?.let(::parseQuantity)
                        form.copy(
                            barcodeLookupRunning = false,
                            name = if (!form.isClone && form.name.isBlank()) suggestion?.name.orEmpty() else form.name,
                            brand = if (form.brand.isBlank()) suggestion?.brand.orEmpty() else form.brand,
                            sizeAmount = if (form.sizeAmount.isBlank() && size != null) size.first else form.sizeAmount,
                            sizeUnit = if (form.sizeAmount.isBlank() && size != null) size.second else form.sizeUnit,
                        )
                    }
                    BarcodeLookup.Failed -> form.copy(barcodeLookupRunning = false)
                }
            }
        }
    }

    fun removeBarcode(barcode: String) {
        val editingId = _productForm.value?.editingId
        if (editingId == null) {
            _productForm.update { it?.copy(barcodes = it.barcodes - barcode) }
            return
        }
        viewModelScope.launch {
            try {
                repository.removeBarcode(editingId, barcode)
                _productForm.update { it?.copy(barcodes = it.barcodes - barcode, errorMessage = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _productForm.update { it?.copy(errorMessage = errorMessageOf(e)) }
            }
        }
    }

    // The backend's own message for an HTTP error, else null (shown generically).
    private fun errorMessageOf(e: Exception): String? =
        (e as? HttpException)?.apiError()?.error?.takeIf { it.isNotBlank() }

    /**
 * Generate a new product image from the form's current
     * name/description and assign it — same [setProductImage] target the
     * manual reuse picker writes to, so submitProductForm() doesn't need to
     * know a generated image from a reused one. Synchronous from the UI's
     * point of view: [ProductFormState.generatingImage] drives a spinner
     * while the (blocking) network call is in flight.
     */
    fun generateProductImage() {
        val form = _productForm.value ?: return
        val name = form.imagePromptName
        if (name.isBlank() || form.generatingImage) return
        viewModelScope.launch {
            _productForm.update { it?.copy(generatingImage = true, generateImageError = null) }
            try {
                val image = repository.generateImage(name, form.description.trim())
                _productForm.update { it?.copy(generatingImage = false, imageId = image.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val kind = when (e) {
                    is IOException -> ImageGenerationErrorKind.NETWORK
                    is HttpException -> ImageGenerationErrorKind.SERVER
                    else -> ImageGenerationErrorKind.UNKNOWN
                }
                Log.e(TAG, "generateProductImage failed ($kind)", e)
                _productForm.update { it?.copy(generatingImage = false, generateImageError = kind) }
            }
        }
    }

    fun dismissImageGenerationError() {
        _productForm.update { it?.copy(generateImageError = null) }
    }

    fun closeProductForm() {
        _productForm.value = null
    }

    fun submitProductForm() {
        val form = _productForm.value ?: return
        if (!form.isValid || form.submitting) return
        viewModelScope.launch {
            _productForm.update { it?.copy(submitting = true, submitFailed = false, nameConflict = false, errorMessage = null) }
            try {
                val name = form.name.trim()
                val amount = form.sizeAmountValue?.let { java.math.BigDecimal(it.toString()).stripTrailingZeros().toPlainString() }.orEmpty()
                val unit = if (amount.isEmpty()) "" else form.sizeUnit
                if (form.templateId != null) {
                    if (form.editingId != null) {
                        repository.updateClone(form.editingId, form.templateId, form.brand.trim(), amount, unit, form.imageId)
                    } else {
                        val created = repository.createClone(
                            templateId = form.templateId,
                            brand = form.brand.trim(),
                            sizeAmount = amount,
                            sizeUnit = unit,
                            catalogImageId = form.imageId,
                            barcode = form.barcodes.firstOrNull(),
                            requireNew = true,
                        )
                        form.barcodes.drop(1).forEach { repository.addBarcode(created.id, it) }
                    }
                } else if (form.editingId != null) {
                    repository.updateProduct(form.editingId, name, form.categoryId, form.imageId)
                } else {
                    // requireNew = true: reject a name collision with an
                    // existing row (e.g. an external-catalog import) outright instead of
                    // silently reusing it — see createProduct's own doc
                    // comment for why.
                    val created = repository.createProduct(name, form.categoryId, requireNew = true, barcode = form.barcodes.firstOrNull())
                    form.barcodes.drop(1).forEach { repository.addBarcode(created.id, it) }
                    // postCatalogProduct doesn't accept an image, so a
                    // create-with-image needs this follow-up PUT.
                    if (form.imageId != null) {
                        repository.updateProduct(created.id, name, form.categoryId, form.imageId)
                    }
                }
                _productForm.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: HttpException) {
                val message = e.apiError()?.error?.takeIf { it.isNotBlank() }
                if (e.code() == 409 && form.templateId == null && message == null) {
                    _productForm.update { it?.copy(submitting = false, nameConflict = true) }
                } else {
                    _productForm.update { it?.copy(submitting = false, submitFailed = message == null, errorMessage = message) }
                }
            } catch (_: Exception) {
                _productForm.update { it?.copy(submitting = false, submitFailed = true) }
            }
        }
    }

    fun setCategoryName(name: String) {
        _categoryForm.update { it?.copy(name = name) }
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
                    repository.createCategory(name)
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
