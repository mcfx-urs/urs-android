package ch.mcfx.urs.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.FuelRepository
import ch.mcfx.urs.fuel.FuelStats
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val QUICK_STAT_WINDOW_MONTHS = 6L

data class HomeUiState(
    val fuelAvgConsumptionL100Km: Float? = null,
)

// Home only needs lightweight cross-feature quick-stats for its tiles (e.g.
// Fuel's 6-month average consumption) — full per-feature detail lives on
// each feature's own screens, not here.
class HomeViewModel(private val fuelRepository: FuelRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val fills = runCatching { fuelRepository.getFills() }.getOrDefault(emptyList())
            val since = LocalDate.now().minusMonths(QUICK_STAT_WINDOW_MONTHS)
            _uiState.value = HomeUiState(
                fuelAvgConsumptionL100Km = FuelStats.averageConsumptionL100Km(fills, since = since),
            )
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                HomeViewModel(app.container.fuelRepository)
            }
        }
    }
}
