package ch.mcfx.urs.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.FuelRepository
import ch.mcfx.urs.data.ServiceCategory
import ch.mcfx.urs.data.ServiceRepository
import ch.mcfx.urs.data.VehicleRepository
import ch.mcfx.urs.data.local.CurrencyEntity
import ch.mcfx.urs.data.local.OutboxVehicleServiceTagPayload
import ch.mcfx.urs.data.local.VehicleEntity
import ch.mcfx.urs.data.local.VehicleServiceWithTags
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ServiceUiState {
    data object Loading : ServiceUiState
    data class Data(
        val vehicles: List<VehicleEntity>,
        val services: List<VehicleServiceWithTags>,
        val currencies: List<CurrencyEntity>,
    ) : ServiceUiState
}

data class ServiceFormState(
    // Null = creating a new service entry; set = editing this local row.
    val editingServiceId: Long? = null,
    val editingIsSynced: Boolean = false,
    val vehicle: VehicleEntity? = null,
    val date: String = LocalDate.now().toString(),
    val odometer: String = "",
    val provider: String = "",
    val isDiy: Boolean = false,
    val notes: String = "",
    val costAmount: String = "",
    val currencyCode: String = "CHF",
    val selectedFixedCategories: Set<String> = emptySet(),
    // Growable free-text list: appending a new blank slot whenever the last
    // one becomes non-blank, removing a blanked-out non-last slot — see
    // setCustomTag. Always has at least one (possibly blank) trailing slot.
    val customTags: List<String> = listOf(""),
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
) {
    val isValid: Boolean
        get() = vehicle != null &&
            date.isNotBlank() &&
            odometer.toFloatOrNull() != null &&
            costAmount.toFloatOrNull() != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceViewModel(
    private val serviceRepository: ServiceRepository,
    private val vehicleRepository: VehicleRepository,
    private val fuelRepository: FuelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ServiceUiState>(ServiceUiState.Loading)
    val uiState: StateFlow<ServiceUiState> = _uiState.asStateFlow()

    // Null = show every vehicle's service history.
    private val _vehicleFilter = MutableStateFlow<String?>(null)
    val vehicleFilter: StateFlow<String?> = _vehicleFilter.asStateFlow()

    private val _formState = MutableStateFlow(ServiceFormState())
    val formState: StateFlow<ServiceFormState> = _formState.asStateFlow()

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    private val _actionSheetService = MutableStateFlow<VehicleServiceWithTags?>(null)
    val actionSheetService: StateFlow<VehicleServiceWithTags?> = _actionSheetService.asStateFlow()

    private val _pendingDeleteService = MutableStateFlow<VehicleServiceWithTags?>(null)
    val pendingDeleteService: StateFlow<VehicleServiceWithTags?> = _pendingDeleteService.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                vehicleRepository.observeVehicles(),
                _vehicleFilter.flatMapLatest { serviceRepository.observeServices(it) },
                fuelRepository.observeCurrencies(),
            ) { vehicles, services, currencies -> ServiceUiState.Data(vehicles, services, currencies) }
                .collect { _uiState.value = it }
        }
        load()
    }

    fun load() {
        viewModelScope.launch { serviceRepository.refreshFromBackend() }
    }

    fun setVehicleFilter(vehicleId: String?) {
        _vehicleFilter.value = vehicleId
    }

    fun openForm() {
        _formState.value = ServiceFormState()
        _showForm.value = true
    }

    /**
     * Loads the service entry fresh via the repository (not from [uiState],
     * which may not have emitted yet on a cold navigation into this screen)
     * and pre-fills the same form the create flow uses, mirrors
     * FuelViewModel.openFormForEdit.
     */
    fun openFormForEdit(serviceId: Long) {
        viewModelScope.launch {
            val data = serviceRepository.getServiceForEdit(serviceId) ?: return@launch
            val vehicle = vehicleRepository.getVehicleById(data.service.vehicleId)
            val fixedCodes = ServiceCategory.entries.map { it.code }.toSet()
            val existingCustomTags = data.tags
                .filter { it.code == ServiceCategory.CUSTOM_CODE }
                .mapNotNull { it.label }
                .filter { it.isNotBlank() }

            _formState.value = ServiceFormState(
                editingServiceId = data.service.id,
                editingIsSynced = data.service.serverId != null,
                vehicle = vehicle,
                date = data.service.date,
                odometer = data.service.odometer,
                provider = data.service.provider,
                isDiy = data.service.isDiy,
                notes = data.service.notes,
                costAmount = data.service.costAmount,
                currencyCode = data.service.currencyCode,
                selectedFixedCategories = data.tags.map { it.code }.filter { it in fixedCodes }.toSet(),
                customTags = existingCustomTags + "",
            )
            _showForm.value = true
        }
    }

    fun closeForm() {
        _showForm.value = false
    }

    fun openActionSheet(service: VehicleServiceWithTags) {
        _actionSheetService.value = service
    }

    fun closeActionSheet() {
        _actionSheetService.value = null
    }

    fun requestDelete() {
        val service = _actionSheetService.value ?: return
        _actionSheetService.value = null
        _pendingDeleteService.value = service
    }

    fun cancelDelete() {
        _pendingDeleteService.value = null
    }

    fun confirmDelete() {
        val service = _pendingDeleteService.value ?: return
        _pendingDeleteService.value = null
        // Local delete inside serviceRepository.deleteService is immediate
        // and effectively can't fail — no submitting/failure UI state needed
        // here, unlike the form's submit().
        viewModelScope.launch { serviceRepository.deleteService(service.service.id) }
    }

    fun selectVehicle(vehicle: VehicleEntity) = _formState.update { it.copy(vehicle = vehicle) }

    fun setDate(value: String) = _formState.update { it.copy(date = value) }

    fun setOdometer(value: String) = _formState.update { it.copy(odometer = value) }

    fun setProvider(value: String) = _formState.update { it.copy(provider = value) }

    fun setIsDiy(value: Boolean) = _formState.update { it.copy(isDiy = value) }

    fun setNotes(value: String) = _formState.update { it.copy(notes = value) }

    fun setCostAmount(value: String) = _formState.update { it.copy(costAmount = value) }

    fun setCurrencyCode(value: String) = _formState.update { it.copy(currencyCode = value) }

    fun toggleCategory(code: String) = _formState.update { state ->
        val selected = state.selectedFixedCategories
        state.copy(selectedFixedCategories = if (code in selected) selected - code else selected + code)
    }

    /**
     * Auto-grows/shrinks [ServiceFormState.customTags]: typing into the last
     * slot appends a fresh blank one right after it, and blanking out any
     * slot other than the last one removes it — the last slot always stays
     * as the ready-to-type-into placeholder.
     */
    fun setCustomTag(index: Int, value: String) = _formState.update { state ->
        val updated = state.customTags.toMutableList()
        if (index < updated.size) updated[index] = value else updated.add(value)
        if (updated.last().isNotBlank()) updated.add("")
        val trimmed = updated.filterIndexed { i, tag -> tag.isNotBlank() || i == updated.lastIndex }
        state.copy(customTags = trimmed)
    }

    fun submit() {
        val form = _formState.value
        val vehicle = form.vehicle ?: return
        if (!form.isValid || form.submitting) return

        val fixedTags = ServiceCategory.entries
            .filter { it.code in form.selectedFixedCategories }
            .map { OutboxVehicleServiceTagPayload(code = it.code) }
        val customTags = form.customTags
            .filter { it.isNotBlank() }
            .map { OutboxVehicleServiceTagPayload(code = ServiceCategory.CUSTOM_CODE, label = it) }
        val tags = fixedTags + customTags

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, submitFailed = false) }
            try {
                val editingId = form.editingServiceId
                if (editingId != null) {
                    serviceRepository.updateService(
                        localId = editingId,
                        vehicleId = vehicle.id,
                        date = form.date,
                        odometer = form.odometer,
                        provider = form.provider,
                        isDiy = form.isDiy,
                        notes = form.notes,
                        costAmount = form.costAmount,
                        currencyCode = form.currencyCode,
                        tags = tags,
                    )
                } else {
                    serviceRepository.createService(
                        vehicleId = vehicle.id,
                        date = form.date,
                        odometer = form.odometer,
                        provider = form.provider,
                        isDiy = form.isDiy,
                        notes = form.notes,
                        costAmount = form.costAmount,
                        currencyCode = form.currencyCode,
                        tags = tags,
                    )
                }
                // Both paths above are local-only writes that return
                // instantly — no network round-trip to wait on, so the form
                // can close right away. A later sync failure surfaces via
                // the row's own pending/failed badge, not here.
                _showForm.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _formState.update { it.copy(submitting = false, submitFailed = true) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                ServiceViewModel(
                    app.container.serviceRepository,
                    app.container.vehicleRepository,
                    app.container.fuelRepository,
                )
            }
        }
    }
}
