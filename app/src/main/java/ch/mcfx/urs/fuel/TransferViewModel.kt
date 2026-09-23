package ch.mcfx.urs.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.FuelRepository
import ch.mcfx.urs.data.local.FillEntity
import ch.mcfx.urs.data.local.VehicleEntity
import ch.mcfx.urs.data.remote.FuelDto
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TransferUiState {
    data object Loading : TransferUiState
    data class Data(val containers: List<VehicleEntity>, val destinations: List<VehicleEntity>) : TransferUiState
}

data class TransferFormState(
    val source: VehicleEntity? = null,
    // Resolved from the source container's own fuel type the moment it's
    // picked (see TransferViewModel.selectSource) — null while fuel types
    // are still loading, or if that fuel type has no density set yet (see
    // mcfx-urs/urs-backend PUT /api/v1/fuel/{id}, no edit UI in this app).
    val sourceDensityKgPerLiter: Float? = null,
    // Computed stock/weighted-average price for the picked source container
    // — display only, purely informational (mcfx-urs/urs-android#87's own
    // "no dedicated stock screen" scope stays intact; this just surfaces the
    // same number the submit already computes, right where it's picked).
    val sourceStock: ContainerStock.State? = null,
    val destination: VehicleEntity? = null,
    val weightBeforeKg: String = "",
    val weightAfterKg: String = "",
    val odometer: String = "",
    val isFullTank: Boolean = true,
    val date: String = LocalDate.now().toString(),
    val lastOdometer: String? = null,
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
) {
    val weighedKg: Float?
        get() {
            val before = weightBeforeKg.toFloatOrNull() ?: return null
            val after = weightAfterKg.toFloatOrNull() ?: return null
            val diff = before - after
            return if (diff > 0f) diff else null
        }

    val liters: Float?
        get() {
            val kg = weighedKg ?: return null
            val density = sourceDensityKgPerLiter ?: return null
            return if (density > 0f) kg / density else null
        }

    val isValid: Boolean
        get() = source != null && destination != null && liters != null && odometer.toFloatOrNull() != null
}

/**
 * Pouring fuel from a container into a real vehicle's tank — a separate
 * flow from [FuelViewModel]'s ordinary fill-up form, since a transfer takes
 * no price/station input at all: the cost is computed from the source
 * container's own purchase/withdrawal history at the moment of submission
 * (see [ContainerStock]) and written into the resulting fill's normal price
 * field, same as any manually-entered price from then on. See
 * mcfx-urs/urs-android#87.
 */
class TransferViewModel(
    private val repository: FuelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TransferUiState>(TransferUiState.Loading)
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(TransferFormState())
    val formState: StateFlow<TransferFormState> = _formState.asStateFlow()

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    private var fuelTypesCache: List<FuelDto> = emptyList()
    private var fillsCache: List<FillEntity> = emptyList()

    init {
        viewModelScope.launch {
            combine(repository.observeVehicles(), repository.observeFills()) { vehicles, fills ->
                fillsCache = fills
                TransferUiState.Data(
                    containers = vehicles.filter { it.isContainer },
                    destinations = vehicles.filterNot { it.isContainer },
                )
            }.collect { data ->
                _uiState.value = data
                // Keep the displayed stock current as fills sync in, not
                // just at the moment the source was picked.
                _formState.update { state ->
                    state.source?.let { state.copy(sourceStock = ContainerStock.current(it.id, fillsCache)) } ?: state
                }
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                fuelTypesCache = repository.getFuelTypes()
                // A source already picked before this load completed needs
                // its density re-resolved now that fuel types are in.
                _formState.update { state ->
                    state.source?.let { state.copy(sourceDensityKgPerLiter = densityFor(it)) } ?: state
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best-effort — density simply stays unresolved until the
                // next successful load.
            }
            repository.refreshFromBackend()
        }
    }

    fun openForm() {
        _formState.value = TransferFormState()
        _showForm.value = true
    }

    fun closeForm() {
        _showForm.value = false
    }

    fun selectSource(vehicle: VehicleEntity) = _formState.update {
        it.copy(
            source = vehicle,
            sourceDensityKgPerLiter = densityFor(vehicle),
            sourceStock = ContainerStock.current(vehicle.id, fillsCache),
        )
    }

    fun selectDestination(vehicle: VehicleEntity) {
        _formState.update { it.copy(destination = vehicle, lastOdometer = null) }
        viewModelScope.launch {
            val last = try {
                repository.getLastOdometer(vehicle.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null // best-effort hint, not critical
            }
            _formState.update { if (it.destination?.id == vehicle.id) it.copy(lastOdometer = last) else it }
        }
    }

    fun setWeightBeforeKg(value: String) = _formState.update { it.copy(weightBeforeKg = value) }
    fun setWeightAfterKg(value: String) = _formState.update { it.copy(weightAfterKg = value) }
    fun setOdometer(value: String) = _formState.update { it.copy(odometer = value) }
    fun setIsFullTank(value: Boolean) = _formState.update { it.copy(isFullTank = value) }
    fun setDate(value: String) = _formState.update { it.copy(date = value) }

    private fun densityFor(vehicle: VehicleEntity): Float? =
        fuelTypesCache.firstOrNull { it.id == vehicle.fuelId }?.densityKgPerLiter?.toFloatOrNull()

    fun submit() {
        val form = _formState.value
        val source = form.source ?: return
        val destination = form.destination ?: return
        val liters = form.liters ?: return
        if (!form.isValid || form.submitting) return

        val averagePricePerLiter = ContainerStock.current(source.id, fillsCache).averagePricePerLiter

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, submitFailed = false) }
            try {
                repository.createFill(
                    vehicle = destination,
                    station = null,
                    date = form.date,
                    odometer = form.odometer,
                    pricePerLiter = String.format(Locale.US, "%.3f", averagePricePerLiter),
                    liters = String.format(Locale.US, "%.3f", liters),
                    lastOdometer = form.lastOdometer,
                    currencyCode = "CHF",
                    gpsLatitude = null,
                    gpsLongitude = null,
                    isFullTank = form.isFullTank,
                    sourceVehicleId = source.id,
                )
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
                TransferViewModel(app.container.fuelRepository)
            }
        }
    }
}
