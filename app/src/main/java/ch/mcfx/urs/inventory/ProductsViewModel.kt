package ch.mcfx.urs.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.InventoryRepository
import ch.mcfx.urs.data.local.InventoryProductEntity
import ch.mcfx.urs.notifications.NotificationChannels
import ch.mcfx.urs.notifications.ReminderScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProductsUiState {
    data object Loading : ProductsUiState
    data class Data(val products: List<InventoryProductEntity>) : ProductsUiState
}

data class ProductFormState(
    val name: String = "",
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
) {
    val isValid: Boolean get() = name.isNotBlank()
}

// Long-press popup state: quantity + the two warning-color thresholds +
// an optional daily low-stock reminder, all for one specific product.
data class ProductSettingsFormState(
    val product: InventoryProductEntity? = null,
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
    private val repository: InventoryRepository,
    private val categoryId: String,
    private val categoryName: String,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProductsUiState>(ProductsUiState.Loading)
    val uiState: StateFlow<ProductsUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(ProductFormState())
    val formState: StateFlow<ProductFormState> = _formState.asStateFlow()

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    private val _settingsForm = MutableStateFlow(ProductSettingsFormState())
    val settingsForm: StateFlow<ProductSettingsFormState> = _settingsForm.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    init {
        // Room-backed Flow, same shape as CategoriesViewModel/FuelViewModel —
        // load() below only refreshes the cache opportunistically.
        viewModelScope.launch {
            repository.observeProducts(categoryId).collect { _uiState.value = ProductsUiState.Data(it) }
        }
        load()
    }

    fun load() {
        viewModelScope.launch { repository.refreshFromBackend() }
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
                repository.createProduct(categoryId = categoryId, name = form.name.trim(), quantity = 0)
                // createProduct is a local-only write and returns instantly —
                // no network round-trip to wait on, so the form can close
                // right away (see FuelViewModel.submit).
                _showForm.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _formState.update { it.copy(submitting = false, submitFailed = true) }
            }
        }
    }

    fun increment(product: InventoryProductEntity) = adjustQuantity(product, +1)

    fun decrement(product: InventoryProductEntity) = adjustQuantity(product, -1)

    // Direct network call, no outbox — see InventoryRepository
    // .updateProductQuantity's doc comment. Nothing to update server-side
    // for a product that hasn't synced yet, so the stepper is a no-op for
    // one until then.
    //
    // null quantity means "not currently tracked" (paused) — a state below
    // 0, not the same as it. Decrementing past 0 lands there; incrementing
    // from there lands back on 0, not 1, so the stepper always moves by
    // exactly one step in either direction (jumping straight to a specific
    // number is what the long-press settings popup is for).
    private fun adjustQuantity(product: InventoryProductEntity, delta: Int) {
        val serverId = product.serverId ?: return
        val currentQuantity = product.quantity
        val newQuantity = when {
            delta > 0 && currentQuantity == null -> 0
            delta < 0 && currentQuantity == null -> return
            delta < 0 && currentQuantity == 0 -> null
            else -> (currentQuantity!! + delta).coerceAtLeast(0)
        }
        if (newQuantity == currentQuantity) return

        viewModelScope.launch {
            try {
                repository.updateProductQuantity(
                    categoryId = categoryId,
                    productId = serverId,
                    name = product.name,
                    newQuantity = newQuantity,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort: the row simply keeps showing the last-known
                // quantity if the update failed server-side.
            }
        }
    }

    fun deleteProduct(product: InventoryProductEntity) {
        // Same "nothing to delete server-side yet" guard as
        // CategoriesViewModel.deleteCategory.
        val serverId = product.serverId ?: return
        viewModelScope.launch {
            try {
                repository.deleteProduct(serverId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort: the list simply keeps showing the product if the
                // delete failed server-side; the user can just retry the tap.
            }
        }
    }

    // Long-press popup: quantity + thresholds + reminder, all for one
    // product. Prefilled from whatever's already persisted on the backend —
    // reminderEnabled is derived from those fields being non-empty rather
    // than tracked separately, so a fresh load() always reflects reality.
    fun openSettings(product: InventoryProductEntity) {
        _settingsForm.value = ProductSettingsFormState(
            product = product,
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
        val product = form.product ?: return
        val serverId = product.serverId ?: return
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

        // Blank means "not tracked" here too, same as clearing it via the
        // stepper — not defaulted to 0, so the popup can also be used to
        // pause tracking a product.
        val newQuantity = form.quantity.toIntOrNull()

        viewModelScope.launch {
            _settingsForm.update { it.copy(submitting = true, error = null) }
            try {
                if (newQuantity != product.quantity) {
                    repository.updateProductQuantity(
                        categoryId = categoryId, productId = serverId, name = product.name, newQuantity = newQuantity,
                    )
                }
                repository.updateProductSettings(
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
                        deepLinkRoute = InventoryRoutes.products(categoryId, categoryName),
                        conditionInventoryCategoryId = categoryId,
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

        fun factory(categoryId: String, categoryName: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                ProductsViewModel(app.container.inventoryRepository, categoryId, categoryName, app.container.reminderScheduler)
            }
        }
    }
}
