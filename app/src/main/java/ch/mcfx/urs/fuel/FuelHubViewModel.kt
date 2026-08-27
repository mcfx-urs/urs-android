package ch.mcfx.urs.fuel

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

data class FuelHubUiState(
    val vehicles: List<VehicleEntity> = emptyList(),
    // The effective default (stored preference, or oldest vehicle as fallback)
    // — the chip that renders as selected.
    val defaultVehicleId: String? = null,
)

// Backs only the Fuel hub's quick-switch chip row; the tile grid itself is
// static. Vehicles come from the Room cache so the row shows on a cold start.
class FuelHubViewModel(
    private val vehicleRepository: VehicleRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    val uiState: StateFlow<FuelHubUiState> = combine(
        vehicleRepository.observeVehicles(),
        userRepository.defaultVehicleId,
    ) { vehicles, preferredId ->
        val ordered = vehicles.sortedBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
        FuelHubUiState(
            vehicles = ordered,
            defaultVehicleId = resolveDefaultVehicleId(preferredId, ordered.map { it.id }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FuelHubUiState())

    init {
        viewModelScope.launch { runCatching { userRepository.refreshDefaultVehicleId() } }
    }

    fun setDefaultVehicle(vehicleId: String) {
        // Optimistic: setDefaultVehicleId writes the local cache first, so the
        // combined uiState re-emits with the new selection before the network
        // call returns.
        viewModelScope.launch { runCatching { userRepository.setDefaultVehicleId(vehicleId) } }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                FuelHubViewModel(app.container.vehicleRepository, app.container.userRepository)
            }
        }
    }
}
