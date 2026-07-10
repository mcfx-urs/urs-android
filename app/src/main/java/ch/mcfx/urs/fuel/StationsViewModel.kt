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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface StationsUiState {
    data object Loading : StationsUiState
    data class Error(val message: String) : StationsUiState
    data class Data(val stations: List<FillingStationDto>) : StationsUiState
}

data class StationFormState(
    val name: String = "",
    val address: String = "",
    val submitting: Boolean = false,
    val submitFailed: Boolean = false,
) {
    val isValid: Boolean get() = name.isNotBlank()
}

class StationsViewModel(private val repository: FuelRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<StationsUiState>(StationsUiState.Loading)
    val uiState: StateFlow<StationsUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(StationFormState())
    val formState: StateFlow<StationFormState> = _formState.asStateFlow()

    private val _showForm = MutableStateFlow(false)
    val showForm: StateFlow<Boolean> = _showForm.asStateFlow()

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

    fun closeForm() {
        _showForm.value = false
    }

    fun setName(value: String) = _formState.update { it.copy(name = value) }

    fun setAddress(value: String) = _formState.update { it.copy(address = value) }

    fun submit() {
        val form = _formState.value
        if (!form.isValid || form.submitting) return

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, submitFailed = false) }
            try {
                repository.createStation(name = form.name.trim(), address = form.address.trim())
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
                StationsViewModel(app.container.fuelRepository)
            }
        }
    }
}
