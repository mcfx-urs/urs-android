package ch.mcfx.urs.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.CatalogRepository
import ch.mcfx.urs.data.InventoryRepository
import ch.mcfx.urs.data.alphabeticSortKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * One category tile on [CategoryListScreen] — derived from this inventory's
 * currently-tracked products rather than a CRUD entity of its own (
 * `inventory_category` is gone, grouping now comes entirely from
 * `catalog_product.catalogCategoryId` → the shared `catalog_category` pool).
 * [categoryId] is `null` for the "uncategorized" group.
 */
data class InventoryCategoryGroup(
    val categoryId: String?,
    val categoryName: String,
    val productCount: Int,
)

sealed interface CategoriesUiState {
    data object Loading : CategoriesUiState
    data class Data(val groups: List<InventoryCategoryGroup>) : CategoriesUiState
}

/**
 * Read-only browse view over one inventory's tracked products, grouped by
 * category — no create/delete of categories any more, that concept
 * moved entirely to the shared catalog (see [ch.mcfx.urs.shoppinglist
 * .AddProductViewModel]'s KATEGORIEN tab / manual-category-creation path).
 */
class CategoriesViewModel(
    private val inventoryRepository: InventoryRepository,
    private val catalogRepository: CatalogRepository,
    private val inventoryId: String,
    private val uncategorizedLabel: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CategoriesUiState>(CategoriesUiState.Loading)
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                inventoryRepository.observeProducts(inventoryId),
                catalogRepository.observeAll(),
                catalogRepository.observeCategories(),
            ) { products, catalogProducts, categories ->
                val catalogById = catalogProducts.associateBy { it.id }
                val categoriesById = categories.associateBy { it.id }
                products
                    .groupBy { product -> catalogById[product.catalogProductId]?.catalogCategoryId }
                    .map { (categoryId, items) ->
                        InventoryCategoryGroup(
                            categoryId = categoryId,
                            categoryName = categoryId?.let { categoriesById[it]?.name }.orEmpty().ifEmpty { uncategorizedLabel },
                            productCount = items.size,
                        )
                    }
                    .sortedBy { it.categoryName.alphabeticSortKey() }
            }.collect { _uiState.value = CategoriesUiState.Data(it) }
        }
        load()
    }

    fun load() {
        viewModelScope.launch { inventoryRepository.refreshFromBackend() }
        viewModelScope.launch { catalogRepository.refreshFromBackend() }
    }

    companion object {
        fun factory(inventoryId: String, uncategorizedLabel: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                CategoriesViewModel(app.container.inventoryRepository, app.container.catalogRepository, inventoryId, uncategorizedLabel)
            }
        }
    }
}
