package ch.mcfx.urs.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.location.Location
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.FuelRepository
import ch.mcfx.urs.data.local.CurrencyEntity
import ch.mcfx.urs.data.local.FillEntity
import ch.mcfx.urs.data.local.FillingStationEntity
import ch.mcfx.urs.data.local.CarEntity
import ch.mcfx.urs.location.LocationCapture
import ch.mcfx.urs.location.LocationProvider
import ch.mcfx.urs.location.LocationUtils
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface FuelUiState {
    data object Loading : FuelUiState
    data class Data(
        val cars: List<CarEntity>,
        // Unfiltered — still needed as-is so FuelScreen's fill-history can
        // resolve a station name for a past ad-hoc (SOURCE_GPS_AUTO) fill.
        val stations: List<FillingStationEntity>,
        // Picker-ready subset: gps_auto stations excluded hard
        // requirement — they only ever existed to hold one past fill's GPS
        // coordinates, never as a reusable choice) and proximity-sorted
        // when a location is available, alphabetical otherwise.
        val pickerStations: List<StationPickerOption>,
        val fills: List<FillEntity>,
        val currencies: List<CurrencyEntity>,
    ) : FuelUiState
}

data class StationPickerOption(val station: FillingStationEntity, val distanceKm: Double?)

data class FillFormState(
    val car: CarEntity? = null,
    val station: FillingStationEntity? = null,
    // Mutually exclusive with `station`: either a known station is picked,
    // or GPS coordinates are captured for an ad-hoc stop — never both.
    val useGps: Boolean = false,
    val gpsLatitude: String? = null,
    val gpsLongitude: String? = null,
    val capturingLocation: Boolean = false,
    val odometer: String = "",
    val pricePerLiter: String = "",
    val liters: String = "",
    val date: String = LocalDate.now().toString(),
    val isFullTank: Boolean = true,
    val currencyCode: String = "CHF",
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
        get() = car != null &&
            (station != null || (gpsLatitude != null && gpsLongitude != null)) &&
            odometer.toFloatOrNull() != null &&
            pricePerLiter.toFloatOrNull() != null &&
            liters.toFloatOrNull() != null
}

class FuelViewModel(
    private val repository: FuelRepository,
    private val locationCapture: LocationCapture,
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FuelUiState>(FuelUiState.Loading)
    val uiState: StateFlow<FuelUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(FillFormState())
    val formState: StateFlow<FillFormState> = _formState.asStateFlow()

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    init {
        // Cars/fills/stations/currencies are all Room-backed Flows now, so
        // this screen (including the Add-fill form's car picker) has
        // something to show even on a cold start with no connectivity —
        // load() below only refreshes the cache opportunistically.
        viewModelScope.launch {
            combine(
                repository.observeCars(),
                repository.observeStations(),
                repository.observeFills(),
                repository.observeCurrencies(),
                locationProvider.currentLocation,
            ) { cars, stations, fills, currencies, location ->
                FuelUiState.Data(cars, stations, buildPickerStations(stations, location), fills, currencies)
            }.collect { _uiState.value = it }
        }
        load()
    }

    fun load() {
        viewModelScope.launch { repository.refreshFromBackend() }
    }

    fun openForm() {
        _formState.value = FillFormState()
        _showForm.value = true
    }

    fun closeForm() {
        _showForm.value = false
    }

    fun selectCar(car: CarEntity) {
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

    fun selectStation(station: FillingStationEntity) =
        _formState.update { it.copy(station = station, useGps = false, gpsLatitude = null, gpsLongitude = null) }

    fun setUseGps(useGps: Boolean) = _formState.update {
        if (useGps) {
            it.copy(useGps = true, station = null)
        } else {
            it.copy(useGps = false, gpsLatitude = null, gpsLongitude = null)
        }
    }

    fun captureLocation() {
        _formState.update { it.copy(capturingLocation = true) }
        viewModelScope.launch {
            val location = try {
                locationCapture.captureLocation()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            _formState.update {
                it.copy(
                    capturingLocation = false,
                    gpsLatitude = location?.latitude?.toString() ?: it.gpsLatitude,
                    gpsLongitude = location?.longitude?.toString() ?: it.gpsLongitude,
                )
            }
        }
    }

    fun setCurrencyCode(value: String) = _formState.update { it.copy(currencyCode = value) }

    fun setOdometer(value: String) = _formState.update { it.copy(odometer = value) }

    fun setPricePerLiter(value: String) = _formState.update { it.copy(pricePerLiter = value) }

    fun setLiters(value: String) = _formState.update { it.copy(liters = value) }

    fun setDate(value: String) = _formState.update { it.copy(date = value) }

    fun setIsFullTank(value: Boolean) = _formState.update { it.copy(isFullTank = value) }

    fun submit() {
        val form = _formState.value
        val car = form.car ?: return
        if (!form.isValid || form.submitting) return

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, submitFailed = false) }
            try {
                repository.createFill(
                    car = car,
                    station = form.station,
                    date = form.date,
                    odometer = form.odometer,
                    pricePerLiter = form.pricePerLiter,
                    liters = form.liters,
                    lastOdometer = form.lastOdometer,
                    currencyCode = form.currencyCode,
                    gpsLatitude = form.gpsLatitude,
                    gpsLongitude = form.gpsLongitude,
                    isFullTank = form.isFullTank,
                )
                // createFill is a local-only write and returns instantly —
                // no network round-trip to wait on, so the form can close
                // right away. A later sync failure surfaces via the row's
                // own pending/failed badge (see FuelScreen), not here.
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
                FuelViewModel(app.container.fuelRepository, app.container.locationCapture, app.container.locationProvider)
            }
        }

        private fun buildPickerStations(
            stations: List<FillingStationEntity>,
            location: Location?,
        ): List<StationPickerOption> = stations
            .filter { it.source != FillingStationEntity.SOURCE_GPS_AUTO }
            .map { station -> StationPickerOption(station, distanceKm(station, location)) }
            .sortedWith(compareBy<StationPickerOption> { it.distanceKm ?: Double.MAX_VALUE }.thenBy { it.station.name })

        private fun distanceKm(station: FillingStationEntity, location: Location?): Double? {
            if (location == null) return null
            val lat = station.latitude.toDoubleOrNull() ?: return null
            val lon = station.longitude.toDoubleOrNull() ?: return null
            return LocationUtils.haversineKm(location.latitude, location.longitude, lat, lon)
        }
    }
}
