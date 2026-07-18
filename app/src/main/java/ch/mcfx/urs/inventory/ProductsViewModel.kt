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
import ch.mcfx.urs.data.local.CatalogProductEntity
import ch.mcfx.urs.data.local.InventoryProductEntity
import ch.mcfx.urs.notifications.NotificationChannels
import ch.mcfx.urs.notifications.ReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One tracked product tile — [InventoryProductEntity] joined with the catalog product it links to. */
data class InventoryProductTile(
    val product: InventoryProductEntity,
    val name: String,
    val catalogImageId: Int?,
)

sealed interface ProductsUiState {
    data object Loading : ProductsUiState
    data class Data(val products: List<InventoryProductTile>) : ProductsUiState
}

data class AddProductFormState(
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
)

// Long-press popup state: quantity + the two warning-color thresholds +
// an optional daily low-stock reminder, all for one specific product.
data class ProductSettingsFormState(
    val product: InventoryProductTile? = null,
    val quantity: String = "",
    val firstThreshold: String = "",
    val secondThreshold: String = "",
    val reminderEnabled: Boolean = false,
    val reminderHour: String = "",
    val reminderMinute: String = "",
    val reminderThreshold: String = "",
    val error: String? = null,
    val submitting: Boolean = false,
)

class ProductsViewModel(
    private val inventoryRepository: InventoryRepository,
    private val catalogRepository: CatalogRepository,
    private val inventoryId: String,
    private val inventoryName: String,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProductsUiState>(ProductsUiState.Loading)
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(AddProductFormState())
    val formState: StateFlow<AddProductFormState> = _formState.asStateFlow()

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    // Deliberately independent of _formState (see AddProductViewModel's own
    // query/results split, the same shape this mirrors) — the search itself
    // writes to _results, so collecting it as part of the same state this
    // search loop keys off of would re-trigger the search on every one of
    // its own results, an easy but real feedback-loop bug.
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<CatalogProductEntity>>(emptyList())
    val results: StateFlow<List<CatalogProductEntity>> = _results.asStateFlow()

    private val _settingsForm = MutableStateFlow(ProductSettingsFormState())
    val settingsForm: StateFlow<ProductSettingsFormState> = _settingsForm.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    init {
        viewModelScope.launch {
            combine(inventoryRepository.observeProducts(inventoryId), catalogRepository.observeAll()) { products, catalogProducts ->
                val catalogById = catalogProducts.associateBy { it.id }
                products
                    .mapNotNull { product ->
                        val catalog = catalogById[product.catalogProductId] ?: return@mapNotNull null
                        InventoryProductTile(product = product, name = catalog.name, catalogImageId = catalog.catalogImageId)
                    }
                    .sortedBy { it.name.alphabeticSortKey() }
            }.collect { _uiState.value = ProductsUiState.Data(it) }
        }
        // collectLatest (not flatMapLatest) — same reasoning as
        // AddProductViewModel's own search collector.
        viewModelScope.launch {
            _query.collectLatest { q ->
                if (q.isBlank()) {
                    _results.value = emptyList()
                    return@collectLatest
                }
                catalogRepository.search(q).collect { _results.value = it }
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch { inventoryRepository.refreshFromBackend() }
        viewModelScope.launch { catalogRepository.refreshFromBackend() }
    }

    fun openForm() {
        _formState.value = AddProductFormState()
        _query.value = ""
        _results.value = emptyList()
        _showForm.value = true
    }

    fun closeForm() {
        _showForm.value = false
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    /** Track an existing catalog product in this inventory — see [InventoryRepository.createProduct]'s dedup doc comment. */
    fun trackExistingProduct(product: CatalogProductEntity) {
        viewModelScope.launch {
            inventoryRepository.createProduct(inventoryId = inventoryId, catalogProductId = product.id, quantity = 0)
            _showForm.value = false
        }
    }

    /**
     * "Type a new product name" path — creates a genuinely new shared
     * catalog product first (direct, synchronous REST call, see
     * [CatalogRepository.createProduct]'s doc comment), then tracks it in
     * this inventory.
     */
    fun createAndTrackProduct() {
        val form = _formState.value
        val name = _query.value.trim()
        if (name.isBlank() || form.submitting) return

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, submitFailed = false) }
            try {
                val catalogProduct = catalogRepository.createProduct(name = name, catalogCategoryId = null)
                inventoryRepository.createProduct(inventoryId = inventoryId, catalogProductId = catalogProduct.id, quantity = 0)
                _showForm.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _formState.update { it.copy(submitting = false, submitFailed = true) }
            }
        }
    }

    fun deleteProduct(tile: InventoryProductTile) {
        // Same "nothing to delete server-side yet" guard as before .
        val serverId = tile.product.serverId ?: return
        viewModelScope.launch {
            try {
                inventoryRepository.deleteProduct(serverId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort: the tile grid simply keeps showing the product if the delete failed server-side.
            }
        }
    }

    // Long-press popup: quantity + thresholds + reminder, all for one
    // product. Prefilled from whatever's already persisted on the backend —
    // reminderEnabled is derived from those fields being non-empty rather
    // than tracked separately, so a fresh load() always reflects reality.
    fun openSettings(tile: InventoryProductTile) {
        val product = tile.product
        _settingsForm.value = ProductSettingsFormState(
            product = tile,
            quantity = product.quantity?.toString().orEmpty(),
            firstThreshold = product.firstThreshold?.toString().orEmpty(),
            secondThreshold = product.secondThreshold?.toString().orEmpty(),
            reminderEnabled = product.reminderHour != null && product.reminderMinute != null && product.reminderThreshold != null,
            reminderHour = product.reminderHour?.toString().orEmpty(),
            reminderMinute = product.reminderMinute?.toString().orEmpty(),
            reminderThreshold = product.reminderThreshold?.toString().orEmpty(),
        )
        _showSettings.value = true
    }

    fun closeSettings() {
        _showSettings.value = false
    }

    fun setSettingsQuantity(value: String) =
        _settingsForm.update { it.copy(quantity = value.filter(Char::isDigit), error = null) }

    fun setFirstThreshold(value: String) =
        _settingsForm.update { it.copy(firstThreshold = value.filter(Char::isDigit), error = null) }

    fun setSecondThreshold(value: String) =
        _settingsForm.update { it.copy(secondThreshold = value.filter(Char::isDigit), error = null) }

    fun setReminderEnabled(value: Boolean) = _settingsForm.update { it.copy(reminderEnabled = value, error = null) }

    fun setReminderHour(value: String) =
        _settingsForm.update { it.copy(reminderHour = value.filter(Char::isDigit).take(2), error = null) }

    fun setReminderMinute(value: String) =
        _settingsForm.update { it.copy(reminderMinute = value.filter(Char::isDigit).take(2), error = null) }

    fun setReminderThreshold(value: String) =
        _settingsForm.update { it.copy(reminderThreshold = value.filter(Char::isDigit), error = null) }

    fun submitSettings(
        thresholdOrderError: String,
        reminderFieldsError: String,
        reminderTitle: String,
        reminderBody: String,
    ) {
        val form = _settingsForm.value
        val tile = form.product ?: return
        val serverId = tile.product.serverId ?: return
        if (form.submitting) return

        val first = form.firstThreshold.toIntOrNull()
        val second = form.secondThreshold.toIntOrNull()
        if (first != null && second != null && first <= second) {
            _settingsForm.update { it.copy(error = thresholdOrderError) }
            return
        }

        val hour = form.reminderHour.toIntOrNull()
        val minute = form.reminderMinute.toIntOrNull()
        val reminderThreshold = form.reminderThreshold.toIntOrNull()
        if (form.reminderEnabled &&
            (hour == null || hour !in 0..23 || minute == null || minute !in 0..59 || reminderThreshold == null)
        ) {
            _settingsForm.update { it.copy(error = reminderFieldsError) }
            return
        }

        // Blank means "not tracked" here too — not defaulted to 0, so the
        // popup can also be used to pause tracking a product.
        val newQuantity = form.quantity.toIntOrNull()

        viewModelScope.launch {
            _settingsForm.update { it.copy(submitting = true, error = null) }
            try {
                if (newQuantity != tile.product.quantity) {
                    inventoryRepository.updateProductQuantity(productId = serverId, newQuantity = newQuantity)
                }
                inventoryRepository.updateProductSettings(
                    productId = serverId,
                    firstThreshold = first,
                    secondThreshold = second,
                    reminderThreshold = if (form.reminderEnabled) reminderThreshold else null,
                    reminderHour = if (form.reminderEnabled) hour else null,
                    reminderMinute = if (form.reminderEnabled) minute else null,
                )

                val reminderId = reminderIdFor(serverId)
                if (form.reminderEnabled && hour != null && minute != null && reminderThreshold != null) {
                    reminderScheduler.scheduleDaily(
                        id = reminderId,
                        channelId = NotificationChannels.INVENTORY,
                        hour = hour,
                        minute = minute,
                        title = reminderTitle,
                        body = reminderBody,
                        deepLinkRoute = InventoryRoutes.products(inventoryId, inventoryName),
                        conditionInventoryId = inventoryId,
                        conditionInventoryProductId = serverId,
                        conditionBelowQuantity = reminderThreshold,
                    )
                } else {
                    reminderScheduler.cancel(reminderId)
                }

                _showSettings.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _settingsForm.update { it.copy(submitting = false, error = null) }
            }
        }
    }

    companion object {
        // Namespaced away from small hand-picked IDs elsewhere (e.g. the
        // fixed test-notification ID) so a per-product reminder can never
        // collide with an unrelated notification/alarm.
        private const val INVENTORY_REMINDER_ID_BASE = 100_000

        private fun reminderIdFor(productId: String): Int =
            INVENTORY_REMINDER_ID_BASE + (productId.toIntOrNull() ?: productId.hashCode().and(0xFFFF))

        fun factory(
            inventoryId: String,
            inventoryName: String,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                ProductsViewModel(
                    app.container.inventoryRepository,
                    app.container.catalogRepository,
                    inventoryId,
                    inventoryName,
                    app.container.reminderScheduler,
                )
            }
        }
    }
}
