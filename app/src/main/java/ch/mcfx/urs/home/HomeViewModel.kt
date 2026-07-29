package ch.mcfx.urs.home

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.auth.AuthTokenStore
import ch.mcfx.urs.beer.BeerStats
import ch.mcfx.urs.data.BeerRepository
import ch.mcfx.urs.data.FuelRepository
import ch.mcfx.urs.fuel.FuelStats
import ch.mcfx.urs.location.LocationProvider
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val QUICK_STAT_WINDOW_MONTHS = 6L

data class HomeUiState(
    val fuelAvgConsumptionL100Km: Float? = null,
    val daysSinceLastBeer: Long? = null,
)

// Home only needs lightweight cross-feature quick-stats for its tiles (e.g.
// Fuel's 6-month average consumption) — full per-feature detail lives on
// each feature's own screens, not here.
class HomeViewModel(
    private val fuelRepository: FuelRepository,
    private val beerRepository: BeerRepository,
    private val locationProvider: LocationProvider,
    private val authTokenStore: AuthTokenStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // The username last used to log in (see AuthTokenStore's own doc comment)
    // — read once, not observed: it only ever changes on a fresh login, which
    // always recreates this ViewModel along with the rest of the logged-in UI.
    val username: String? = authTokenStore.userName

    // Home's own mini-map preview tile reuses the same ambient value every
    // other screen warms up via refreshLocation() below — no separate fetch.
    val currentLocation: StateFlow<Location?> = locationProvider.currentLocation

    init {
        viewModelScope.launch {
            val fills = runCatching { fuelRepository.getFills() }.getOrDefault(emptyList())
            val since = LocalDate.now().minusMonths(QUICK_STAT_WINDOW_MONTHS)
            val beerEntries = runCatching { beerRepository.getEntries() }.getOrDefault(emptyList())
            _uiState.value = HomeUiState(
                fuelAvgConsumptionL100Km = FuelStats.averageConsumptionL100Km(fills, since = since),
                daysSinceLastBeer = BeerStats.daysSinceLast(beerEntries),
            )
        }
    }

    // Location permission is requested proactively here (once, not
    // on every Home visit) rather than contextually in the fuel-add form,
    // so the ambient LocationProvider is already warm by the time any
    // screen wants a proximity sort.
    fun hasPromptedLocationPermission(): Boolean = locationProvider.hasPromptedPermission()

    fun markLocationPermissionPrompted() = locationProvider.markPermissionPrompted()

    fun refreshLocation() {
        viewModelScope.launch { locationProvider.refresh() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                HomeViewModel(
                    app.container.fuelRepository,
                    app.container.beerRepository,
                    app.container.locationProvider,
                    app.container.authTokenStore,
                )
            }
        }
    }
}
