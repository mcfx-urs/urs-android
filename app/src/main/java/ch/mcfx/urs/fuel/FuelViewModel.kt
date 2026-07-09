package ch.mcfx.urs.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.FuelRepository
import ch.mcfx.urs.data.remote.CarDto
import ch.mcfx.urs.data.remote.FillDto
import ch.mcfx.urs.data.remote.FillingStationDto
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface FuelUiState {
    data object Loading : FuelUiState
    data class Error(val message: String) : FuelUiState
    data class Data(
        val cars: List<CarDto>,
        val stations: List<FillingStationDto>,
        val fills: List<FillDto>,
    ) : FuelUiState
}

data class FillFormState(
    val car: CarDto? = null,
    val station: FillingStationDto? = null,
    val odometer: String = "",
    val pricePerLiter: String = "",
    val liters: String = "",
    val date: String = LocalDate.now().toString(),
    val lastOdometer: String? = null,
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
) {
    val totalCost: String?
        get() {
            val price = pricePerLiter.toFloatOrNull() ?: return null
            val amount = liters.toFloatOrNull() ?: return null
            return String.format(Locale.US, "%.2f", price * amount)
        }

    val isValid: Boolean
        get() = car != null && station != null &&
            odometer.toFloatOrNull() != null &&
            pricePerLiter.toFloatOrNull() != null &&
            liters.toFloatOrNull() != null
}

class FuelViewModel(private val repository: FuelRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<FuelUiState>(FuelUiState.Loading)
    val uiState: StateFlow<FuelUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(FillFormState())
    val formState: StateFlow<FillFormState> = _formState.asStateFlow()

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = FuelUiState.Loading
        viewModelScope.launch {
            try {
                coroutineScope {
                    val cars = async { repository.getCars() }
                    val stations = async { repository.getStations() }
                    val fills = async { repository.getFills() }
                    _uiState.value = FuelUiState.Data(cars.await(), stations.await(), fills.await())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = FuelUiState.Error(e.message ?: "unknown")
            }
        }
    }

    fun openForm() {
        _formState.value = FillFormState()
        _showForm.value = true
    }

    fun closeForm() {
        _showForm.value = false
    }

    fun selectCar(car: CarDto) {
        _formState.update { it.copy(car = car, lastOdometer = null) }
        viewModelScope.launch {
            val last = try {
                repository.getLastOdometer(car.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null // best-effort hint, not critical
            }
            _formState.update { if (it.car?.id == car.id) it.copy(lastOdometer = last) else it }
        }
    }

    fun selectStation(station: FillingStationDto) = _formState.update { it.copy(station = station) }

    fun setOdometer(value: String) = _formState.update { it.copy(odometer = value) }

    fun setPricePerLiter(value: String) = _formState.update { it.copy(pricePerLiter = value) }

    fun setLiters(value: String) = _formState.update { it.copy(liters = value) }

    fun setDate(value: String) = _formState.update { it.copy(date = value) }

    fun submit() {
        val form = _formState.value
        val car = form.car ?: return
        val station = form.station ?: return
        if (!form.isValid || form.submitting) return

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, submitFailed = false) }
            try {
                repository.createFill(
                    car = car,
                    station = station,
                    date = form.date,
                    odometer = form.odometer,
                    pricePerLiter = form.pricePerLiter,
                    liters = form.liters,
                    lastOdometer = form.lastOdometer,
                )
                _showForm.value = false
                load()
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
                FuelViewModel(app.container.fuelRepository)
            }
        }
    }
}
