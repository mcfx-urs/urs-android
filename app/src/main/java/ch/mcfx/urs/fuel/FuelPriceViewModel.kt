package ch.mcfx.urs.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.FuelRepository
import ch.mcfx.urs.data.local.FillingStationEntity
import ch.mcfx.urs.data.remote.FuelDto
import ch.mcfx.urs.location.LocationProvider
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface FuelPriceUiState {
    data object Loading : FuelPriceUiState
    data class Data(
        val pickerStations: List<StationPickerOption>,
        val fuelTypes: List<FuelDto>,
    ) : FuelPriceUiState
}

data class FuelPriceFormState(
    val station: FillingStationEntity? = null,
    val fuelType: FuelDto? = null,
    val price: String = "",
    val date: String = LocalDate.now().toString(),
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
) {
    val isValid: Boolean
        get() = station != null && fuelType != null && price.toFloatOrNull() != null && date.isNotBlank()
}

/**
 * Records a fuel price independent of a fill-up — e.g. seen at a station
 * while driving past without stopping. Submits directly to the backend
 * (see [FuelRepository.submitFuelPrice]'s doc comment) rather than through
 * [FuelViewModel]'s offline outbox: unlike a fill-up, a price observation
 * has no edit/delete lifecycle and isn't critical enough to justify that
 * machinery.
 */
class FuelPriceViewModel(
    private val repository: FuelRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FuelPriceUiState>(FuelPriceUiState.Loading)
    val uiState: StateFlow<FuelPriceUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(FuelPriceFormState())
    val formState: StateFlow<FuelPriceFormState> = _formState.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _fuelTypes = MutableStateFlow<List<FuelDto>>(emptyList())

    init {
        viewModelScope.launch {
            combine(
                repository.observeStations(),
                locationProvider.currentLocation,
                _fuelTypes,
            ) { stations, location, fuelTypes ->
                FuelPriceUiState.Data(buildPickerStations(stations, location), fuelTypes)
            }.collect { _uiState.value = it }
        }
        viewModelScope.launch {
            _fuelTypes.value = try {
                repository.getFuelTypes()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList() // best-effort — an empty picker still lets the user retry
            }
        }
    }

    fun selectStation(station: FillingStationEntity) = _formState.update { it.copy(station = station) }

    fun selectFuelType(fuelType: FuelDto) = _formState.update { it.copy(fuelType = fuelType) }

    fun setPrice(value: String) = _formState.update { it.copy(price = value) }

    fun setDate(value: String) = _formState.update { it.copy(date = value) }

    fun submit() {
        val form = _formState.value
        val station = form.station ?: return
        val fuelType = form.fuelType ?: return
        if (!form.isValid || form.submitting) return

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, submitFailed = false) }
            try {
                repository.submitFuelPrice(
                    date = form.date,
                    price = form.price,
                    fuelTypeId = fuelType.id,
                    stationId = station.id,
                )
                _saved.value = true
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
                FuelPriceViewModel(app.container.fuelRepository, app.container.locationProvider)
            }
        }
    }
}
