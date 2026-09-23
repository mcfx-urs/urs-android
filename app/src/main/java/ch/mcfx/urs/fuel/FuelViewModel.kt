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
import ch.mcfx.urs.data.UserRepository
import ch.mcfx.urs.data.resolveDefaultVehicleId
import ch.mcfx.urs.data.local.CurrencyEntity
import ch.mcfx.urs.data.local.FillEntity
import ch.mcfx.urs.data.local.FillingStationEntity
import ch.mcfx.urs.data.local.VehicleEntity
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface FuelUiState {
    data object Loading : FuelUiState
    data class Data(
        val vehicles: List<VehicleEntity>,
        // Unfiltered — still needed as-is so FuelScreen's fill-history can
        // resolve a station name for a past ad-hoc (SOURCE_GPS_AUTO) fill.
        val stations: List<FillingStationEntity>,
        // Picker-ready subset: gps_auto stations excluded — they only ever
        // existed to hold one past fill's GPS coordinates, never as a
        // reusable choice — and proximity-sorted
        // when a location is available, alphabetical otherwise.
        val pickerStations: List<StationPickerOption>,
        val fills: List<FillEntity>,
        val currencies: List<CurrencyEntity>,
    ) : FuelUiState
}

data class StationPickerOption(val station: FillingStationEntity, val distanceKm: Double?)

data class FillFormState(
    // Null = creating a new fill; set = editing this local row.
    val editingFillId: Long? = null,
    // True once editing a fill the backend already confirmed (has a
    // FillEntity.serverId) — the GPS/ad-hoc-station toggle is locked in
    // that case, since PUT /api/v1/fill/{id} has no ad-hoc-station-creation
    // branch (see FuelRepository.updateFill).
    val editingIsSynced: Boolean = false,
    val vehicle: VehicleEntity? = null,
    // Set only when this fill is (or was originally created as) a
    // container-to-vehicle transfer — see mcfx-urs/urs-android#87. Not
    // settable from this generic form (only TransferAddScreen creates one);
    // carried through unchanged so re-saving an existing transfer via this
    // edit form doesn't need a station.
    val sourceVehicleId: String? = null,
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
    // Guards openForm()/openFormForEdit() against re-running on a config
    // change (the ViewModel survives rotation, but its LaunchedEffect(Unit)
    // caller doesn't, so it fires again) — without this, a fresh load
    // overwrites any in-progress edit with the last-saved DB state.
    val initialized: Boolean = false,
    // True once any field has been edited since the last load/save — drives
    // the discard-changes confirmation on back/Home.
    val dirty: Boolean = false,
) {
    val totalCost: String?
        get() {
            val price = pricePerLiter.toFloatOrNull() ?: return null
            val amount = liters.toFloatOrNull() ?: return null
            return String.format(Locale.US, "%.2f", price * amount)
        }

    val isValid: Boolean
        get() = vehicle != null &&
            (station != null || (gpsLatitude != null && gpsLongitude != null) || sourceVehicleId != null) &&
            odometer.toFloatOrNull() != null &&
            pricePerLiter.toFloatOrNull() != null &&
            liters.toFloatOrNull() != null
}

class FuelViewModel(
    private val repository: FuelRepository,
    private val locationCapture: LocationCapture,
    private val locationProvider: LocationProvider,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FuelUiState>(FuelUiState.Loading)
    val uiState: StateFlow<FuelUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(FillFormState())
    val formState: StateFlow<FillFormState> = _formState.asStateFlow()

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    private val _actionSheetFill = MutableStateFlow<FillEntity?>(null)
    val actionSheetFill: StateFlow<FillEntity?> = _actionSheetFill.asStateFlow()

    private val _pendingDeleteFill = MutableStateFlow<FillEntity?>(null)
    val pendingDeleteFill: StateFlow<FillEntity?> = _pendingDeleteFill.asStateFlow()

    init {
        // Vehicles/fills/stations/currencies are all Room-backed Flows now,
        // so this screen (including the Add-fill form's vehicle picker) has
        // something to show even on a cold start with no connectivity —
        // load() below only refreshes the cache opportunistically.
        viewModelScope.launch {
            combine(
                repository.observeVehicles(),
                repository.observeStations(),
                repository.observeFills(),
                repository.observeCurrencies(),
                locationProvider.currentLocation,
            ) { vehicles, stations, fills, currencies, location ->
                FuelUiState.Data(vehicles, stations, buildPickerStations(stations, location), fills, currencies)
            }.collect { _uiState.value = it }
        }
        load()
    }

    fun load() {
        viewModelScope.launch { repository.refreshFromBackend() }
    }

    fun openForm() {
        if (_formState.value.initialized) return
        _formState.value = FillFormState(initialized = true)
        _showForm.value = true
        // Pre-select the default vehicle (still overridable in the form).
        viewModelScope.launch {
            runCatching { userRepository.refreshDefaultVehicleId() }
            val vehicles = repository.observeVehicles().first()
            // A container is never a plausible fallback default — it has no
            // odometer/consumption meaning (mcfx-urs/urs-android#87).
            val defaultVehicleId =
                resolveDefaultVehicleId(userRepository.defaultVehicleId.value, vehicles.filterNot { it.isContainer }.map { it.id })
            val vehicle = vehicles.firstOrNull { it.id == defaultVehicleId } ?: return@launch
            if (_showForm.value && _formState.value.editingFillId == null && _formState.value.vehicle == null) {
                selectVehicle(vehicle)
            }
        }
    }

    /**
     * Loads the fill fresh via the repository (not from [uiState], which may
     * not have emitted yet on a cold navigation into this screen) and
     * pre-fills the same form the create flow uses — [showForm] flips to
     * `true` only once that load completes, mirrors
     * WorkTimeViewModel.openFormForEdit.
     */
    fun openFormForEdit(fillId: Long) {
        if (_formState.value.initialized) return
        _formState.value = FillFormState(initialized = true)
        viewModelScope.launch {
            val data = repository.getFillForEdit(fillId) ?: return@launch
            _formState.value = FillFormState(
                editingFillId = data.fill.id,
                editingIsSynced = data.fill.serverId != null,
                vehicle = data.vehicle,
                sourceVehicleId = data.fill.sourceVehicleId,
                station = data.station,
                odometer = data.fill.odometer,
                pricePerLiter = data.fill.pricePerLiter,
                liters = data.fill.liters,
                date = data.fill.date.substringBefore(' '),
                isFullTank = data.fill.isFullTank,
                currencyCode = data.fill.currencyCode,
                initialized = true,
            )
            _showForm.value = true
        }
    }

    fun closeForm() {
        _showForm.value = false
    }

    fun openActionSheet(fill: FillEntity) {
        _actionSheetFill.value = fill
    }

    fun closeActionSheet() {
        _actionSheetFill.value = null
    }

    fun requestDelete() {
        val fill = _actionSheetFill.value ?: return
        _actionSheetFill.value = null
        _pendingDeleteFill.value = fill
    }

    fun cancelDelete() {
        _pendingDeleteFill.value = null
    }

    fun confirmDelete() {
        val fill = _pendingDeleteFill.value ?: return
        _pendingDeleteFill.value = null
        // Local delete inside repository.deleteFill is immediate and
        // effectively can't fail — no submitting/failure UI state needed
        // here, unlike the form's submit().
        viewModelScope.launch { repository.deleteFill(fill.id) }
    }

    fun selectVehicle(vehicle: VehicleEntity) {
        // A container has no odometer/consumption meaning — its own
        // fill-up form hides the field entirely (see FuelAddScreen), so
        // default it to a valid value here rather than leaving it blank
        // (the backend's fill_odometer column is still NOT NULL).
        if (vehicle.isContainer) {
            _formState.update { it.copy(vehicle = vehicle, odometer = "0", lastOdometer = null) }
            return
        }
        _formState.update { it.copy(vehicle = vehicle, lastOdometer = null) }
        viewModelScope.launch {
            val last = try {
                repository.getLastOdometer(vehicle.id)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null // best-effort hint, not critical
            }
            _formState.update { if (it.vehicle?.id == vehicle.id) it.copy(lastOdometer = last) else it }
        }
    }

    fun selectStation(station: FillingStationEntity) =
        _formState.update { it.copy(station = station, useGps = false, gpsLatitude = null, gpsLongitude = null, dirty = true) }

    fun setUseGps(useGps: Boolean) = _formState.update {
        if (useGps) {
            it.copy(useGps = true, station = null, dirty = true)
        } else {
            it.copy(useGps = false, gpsLatitude = null, gpsLongitude = null, dirty = true)
        }
    }

    fun captureLocation() {
        _formState.update { it.copy(capturingLocation = true) }
        viewModelScope.launch {
            val location = try {
                locationCapture.captureLocation("fuel-fill")
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

    fun setCurrencyCode(value: String) = _formState.update { it.copy(currencyCode = value, dirty = true) }

    fun setOdometer(value: String) = _formState.update { it.copy(odometer = value, dirty = true) }

    fun setPricePerLiter(value: String) = _formState.update { it.copy(pricePerLiter = value, dirty = true) }

    fun setLiters(value: String) = _formState.update { it.copy(liters = value, dirty = true) }

    fun setDate(value: String) = _formState.update { it.copy(date = value, dirty = true) }

    fun setIsFullTank(value: Boolean) = _formState.update { it.copy(isFullTank = value, dirty = true) }

    fun submit() {
        val form = _formState.value
        val vehicle = form.vehicle ?: return
        if (!form.isValid || form.submitting) return

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, submitFailed = false) }
            try {
                val editingFillId = form.editingFillId
                if (editingFillId != null) {
                    repository.updateFill(
                        localId = editingFillId,
                        vehicle = vehicle,
                        station = form.station,
                        date = form.date,
                        odometer = form.odometer,
                        pricePerLiter = form.pricePerLiter,
                        liters = form.liters,
                        currencyCode = form.currencyCode,
                        gpsLatitude = form.gpsLatitude,
                        gpsLongitude = form.gpsLongitude,
                        isFullTank = form.isFullTank,
                        sourceVehicleId = form.sourceVehicleId,
                    )
                } else {
                    repository.createFill(
                        vehicle = vehicle,
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
                }
                // Both paths above are local-only writes that return
                // instantly — no network round-trip to wait on, so the form
                // can close right away. A later sync failure surfaces via
                // the row's own pending/failed badge (see FuelScreen), not
                // here.
                _formState.update { it.copy(dirty = false) }
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
                FuelViewModel(
                    app.container.fuelRepository,
                    app.container.locationCapture,
                    app.container.locationProvider,
                    app.container.userRepository,
                )
            }
        }
    }
}

// Top-level (not FuelViewModel-private) so FuelPriceViewModel's own station
// picker can reuse the same exclude-ad-hoc/proximity-sort logic.
internal fun buildPickerStations(
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
