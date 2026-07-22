package ch.mcfx.urs.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.FuelRepository
import ch.mcfx.urs.data.remote.FillingStationDto
import ch.mcfx.urs.location.LocationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface StationsUiState {
    data object Loading : StationsUiState
    data class Error(val message: String) : StationsUiState
    data class Data(val stations: List<FillingStationDto>) : StationsUiState
}

data class StationFormState(
    // Null = creating a new station; set = editing this existing one
    // (, super-user-gated at both the UI entry point and the
    // backend endpoint).
    val editingId: String? = null,
    val name: String = "",
    val address: String = "",
    val latitude: String? = null,
    val longitude: String? = null,
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
    val geocoding: Boolean = false,
    val geocodeFailed: Boolean = false,
) {
    val isValid: Boolean get() = name.isNotBlank()
}

class StationsViewModel(
    private val repository: FuelRepository,
    val locationProvider: LocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StationsUiState>(StationsUiState.Loading)
    val uiState: StateFlow<StationsUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(StationFormState())
    val formState: StateFlow<StationFormState> = _formState.asStateFlow()

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

    // One-shot event: fires once a geocode search succeeds, so
    // FuelStationsScreen can navigate to the map-confirm step exactly once,
    // not on every recomposition of the form state it also just updated.
    private val _openMapConfirm = Channel<Unit>(Channel.CONFLATED)
    val openMapConfirm: Flow<Unit> = _openMapConfirm.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = StationsUiState.Loading
        viewModelScope.launch {
            try {
                _uiState.value = StationsUiState.Data(repository.getStations())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = StationsUiState.Error(e.message ?: "unknown")
            }
        }
    }

    fun openForm() {
        _formState.value = StationFormState()
        _showForm.value = true
    }

    fun openFormForEdit(station: FillingStationDto) {
        _formState.value = StationFormState(
            editingId = station.id,
            name = station.name,
            address = station.address,
            latitude = station.latitude.ifBlank { null },
            longitude = station.longitude.ifBlank { null },
        )
        _showForm.value = true
    }

    fun closeForm() {
        _showForm.value = false
    }

    fun setName(value: String) = _formState.update { it.copy(name = value) }

    fun setAddress(value: String) = _formState.update { it.copy(address = value) }

    // Searches the current address via the backend's Nominatim-backed
    // endpoint. On a match, stores the coordinates and fires the one-shot
    // navigation event to the map-confirm step; on failure (a real 404 "no
    // match", or any other transport error) sets geocodeFailed instead —
    // deliberately not distinguished further, since both lead to the same
    // manual-fallback UI (a button that opens the same map step with no
    // pre-set pin).
    fun searchPosition() {
        val address = _formState.value.address.trim()
        if (address.isBlank() || _formState.value.geocoding) return

        viewModelScope.launch {
            _formState.update { it.copy(geocoding = true, geocodeFailed = false) }
            try {
                val result = repository.geocode(address)
                _formState.update {
                    it.copy(geocoding = false, latitude = result.latitude.toString(), longitude = result.longitude.toString())
                }
                _openMapConfirm.send(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _formState.update { it.copy(geocoding = false, geocodeFailed = true) }
            }
        }
    }

    // Writes a confirmed map position into the form — used both after a
    // successful geocode search (user nudges/accepts the suggested pin) and
    // the manual-fallback path (user places a pin from scratch). Both end at
    // the same place: the user confirmed a position, so there's no need to
    // track which path it came from.
    fun setPositionFromMap(latitude: Double, longitude: Double) {
        _formState.update { it.copy(latitude = latitude.toString(), longitude = longitude.toString()) }
    }

    fun submit() {
        val form = _formState.value
        if (!form.isValid || form.submitting) return

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, submitFailed = false) }
            try {
                val editingId = form.editingId
                if (editingId == null) {
                    repository.createStation(
                        name = form.name.trim(),
                        address = form.address.trim(),
                        latitude = form.latitude,
                        longitude = form.longitude,
                    )
                } else {
                    repository.updateStation(
                        id = editingId,
                        name = form.name.trim(),
                        address = form.address.trim(),
                        latitude = form.latitude,
                        longitude = form.longitude,
                    )
                }
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
                StationsViewModel(app.container.fuelRepository, app.container.locationProvider)
            }
        }
    }
}
