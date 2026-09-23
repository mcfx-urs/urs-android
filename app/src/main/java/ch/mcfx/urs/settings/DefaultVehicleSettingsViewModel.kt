package ch.mcfx.urs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.UserRepository
import ch.mcfx.urs.data.VehicleRepository
import ch.mcfx.urs.data.local.VehicleEntity
import ch.mcfx.urs.data.resolveDefaultVehicleId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DefaultVehicleSettingsViewModel(
    private val vehicleRepository: VehicleRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    data class UiState(
        val vehicles: List<VehicleEntity> = emptyList(),
        // The effective default: the stored preference, or the oldest vehicle
        // as a fallback so the picker never shows a blank selection.
        val selectedVehicleId: String? = null,
    )

    val uiState: StateFlow<UiState> = combine(
        vehicleRepository.observeVehicles(),
        userRepository.defaultVehicleId,
    ) { vehicles, preferredId ->
        val ordered = vehicles.sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
        // A container is never a plausible fallback default — it has no
        // odometer/consumption meaning (mcfx-urs/urs-android#87). The
        // picker list itself (`ordered`) still shows every vehicle, so the
        // owner can still explicitly pick one if they ever want to.
        UiState(ordered, resolveDefaultVehicleId(preferredId, ordered.filterNot { it.isContainer }.map { it.id }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        viewModelScope.launch { runCatching { userRepository.refreshDefaultVehicleId() } }
    }

    fun selectVehicle(vehicleId: String) {
        viewModelScope.launch { runCatching { userRepository.setDefaultVehicleId(vehicleId) } }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                DefaultVehicleSettingsViewModel(app.container.vehicleRepository, app.container.userRepository)
            }
        }
    }
}
