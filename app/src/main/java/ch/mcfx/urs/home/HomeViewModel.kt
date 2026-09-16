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
import ch.mcfx.urs.data.InventoryRepository
import ch.mcfx.urs.data.KanbanRepository
import ch.mcfx.urs.data.ShoppingListRepository
import ch.mcfx.urs.data.UserRepository
import ch.mcfx.urs.data.VehicleRepository
import ch.mcfx.urs.data.local.InventoryEntity
import ch.mcfx.urs.data.local.KanbanBoardEntity
import ch.mcfx.urs.data.local.ListEntity
import ch.mcfx.urs.data.resolveDefaultVehicleId
import ch.mcfx.urs.data.sync.PullCoordinator
import ch.mcfx.urs.data.sync.SyncPhase
import ch.mcfx.urs.fuel.FuelStats
import ch.mcfx.urs.location.LocationProvider
import ch.mcfx.urs.navigation.Destination
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val QUICK_STAT_WINDOW_MONTHS = 6L

data class HomeUiState(
    val fuelAvgConsumptionL100Km: Float? = null,
    val sinceLastBeer: BeerStats.SinceLast? = null,
)

// Home only needs lightweight cross-feature quick-stats for its tiles (e.g.
// Fuel's 6-month average consumption) — full per-feature detail lives on
// each feature's own screens, not here.
class HomeViewModel(
    private val fuelRepository: FuelRepository,
    private val beerRepository: BeerRepository,
    private val locationProvider: LocationProvider,
    private val authTokenStore: AuthTokenStore,
    private val userRepository: UserRepository,
    private val vehicleRepository: VehicleRepository,
    private val homeLayoutStore: HomeLayoutStore,
    private val shoppingListRepository: ShoppingListRepository,
    private val inventoryRepository: InventoryRepository,
    private val kanbanRepository: KanbanRepository,
    pullCoordinator: PullCoordinator,
) : ViewModel() {

    // GitHub issue #53's status indicator (Home banner logo dot + hero
    // moon tint) — this is a straight passthrough, HomeViewModel doesn't
    // own any sync state itself.
    val syncPhase: StateFlow<SyncPhase> = pullCoordinator.phase

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Quick-jump badges on the Shopping List / Inventory tiles — kept live
    // (unlike uiState's one-shot quick-stats above) since favoriting a list
    // elsewhere in the app should show up the moment the user returns Home.
    private val _favoriteLists = MutableStateFlow<List<ListEntity>>(emptyList())
    val favoriteLists: StateFlow<List<ListEntity>> = _favoriteLists.asStateFlow()

    private val _favoriteInventories = MutableStateFlow<List<InventoryEntity>>(emptyList())
    val favoriteInventories: StateFlow<List<InventoryEntity>> = _favoriteInventories.asStateFlow()

    private val _favoriteBoards = MutableStateFlow<List<KanbanBoardEntity>>(emptyList())
    val favoriteBoards: StateFlow<List<KanbanBoardEntity>> = _favoriteBoards.asStateFlow()

    val layout: StateFlow<List<HomeTilePlacement>> = homeLayoutStore.layout

    private val _editMode = MutableStateFlow(false)
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    private val _selectedTileId = MutableStateFlow<String?>(null)
    val selectedTileId: StateFlow<String?> = _selectedTileId.asStateFlow()

    private val _showAddTilePicker = MutableStateFlow(false)
    val showAddTilePicker: StateFlow<Boolean> = _showAddTilePicker.asStateFlow()

    /** Every Destination not currently on the grid — the add-tile picker's list. */
    val addableTiles: List<Destination>
        get() {
            val placedIds = layout.value.map { it.destinationId }.toSet()
            return Destination.entries.filter {
                it != Destination.HOME && it != Destination.SETTINGS && it.name !in placedIds
            }
        }

    fun enterEditMode(selecting: String? = null) {
        _editMode.value = true
        _selectedTileId.value = selecting
    }

    fun exitEditMode() {
        _editMode.value = false
        _selectedTileId.value = null
        _showAddTilePicker.value = false
    }

    /** Tapping a tile in edit mode: selects it, or deselects if it was already selected. */
    fun toggleSelected(id: String) {
        _selectedTileId.value = if (_selectedTileId.value == id) null else id
    }

    fun moveTile(id: String, targetId: String) {
        homeLayoutStore.setOrder(HomeLayoutEngine.moveTile(homeLayoutStore.order.value, id, targetId))
    }

    fun resizeTile(id: String, width: Int, height: Int) {
        homeLayoutStore.setOrder(HomeLayoutEngine.resizeTile(homeLayoutStore.order.value, id, width, height))
    }

    fun removeTile(id: String) {
        homeLayoutStore.setOrder(HomeLayoutEngine.removeTile(homeLayoutStore.order.value, id))
        if (_selectedTileId.value == id) _selectedTileId.value = null
    }

    fun openAddTilePicker() {
        _showAddTilePicker.value = true
    }

    fun dismissAddTilePicker() {
        _showAddTilePicker.value = false
    }

    fun addTile(id: String) {
        homeLayoutStore.setOrder(HomeLayoutEngine.addTile(homeLayoutStore.order.value, id))
        _showAddTilePicker.value = false
    }

    fun resetLayoutToDefault() {
        homeLayoutStore.resetToDefault()
        _selectedTileId.value = null
    }

    // The username last used to log in (see AuthTokenStore's own doc comment)
    // — read once, not observed: it only ever changes on a fresh login, which
    // always recreates this ViewModel along with the rest of the logged-in UI.
    val username: String? = authTokenStore.userName

    // Home's own mini-map preview tile reuses the same ambient value every
    // other screen warms up via refreshLocation() below — no separate fetch.
    val currentLocation: StateFlow<Location?> = locationProvider.currentLocation

    init {
        viewModelScope.launch { shoppingListRepository.observeFavoriteLists().collect { _favoriteLists.value = it } }
        viewModelScope.launch { inventoryRepository.observeFavoriteInventories().collect { _favoriteInventories.value = it } }
        viewModelScope.launch { kanbanRepository.observeFavoriteBoards().collect { _favoriteBoards.value = it } }
        // Covers a beer logged via the watch relay while Home is already the
        // foreground screen (see BeerRepository.logged's doc) — HomeScreen's
        // enter/resume triggers (#67) don't fire in that case since Home
        // never leaves and never backgrounds.
        viewModelScope.launch { beerRepository.logged.collect { refreshBeerStat() } }
        viewModelScope.launch {
            val fills = runCatching { fuelRepository.getFills() }.getOrDefault(emptyList())
            val since = LocalDate.now().minusMonths(QUICK_STAT_WINDOW_MONTHS)
            val beerEntries = runCatching { beerRepository.getEntries() }.getOrDefault(emptyList())
            // The quick-stat tracks the user's default vehicle rather than a
            // blended figure across the whole fleet — meaningless when the
            // vehicles run on different fuels.
            runCatching { userRepository.refreshDefaultVehicleId() }
            val vehicleIds = runCatching { vehicleRepository.observeVehicles().first().map { it.id } }
                .getOrDefault(emptyList())
            val defaultVehicleId = resolveDefaultVehicleId(userRepository.defaultVehicleId.value, vehicleIds)
            _uiState.value = HomeUiState(
                fuelAvgConsumptionL100Km = FuelStats.averageConsumptionL100Km(fills, since = since, vehicleId = defaultVehicleId),
                sinceLastBeer = BeerStats.sinceLast(beerEntries),
            )
        }
    }

    // Re-fetched independently of the init-block quick-stats above (#67):
    // Home's ViewModel instance survives navigating away and back (nav
    // graph uses saveState/restoreState), so the beer stat would otherwise
    // stay frozen at whatever it was on first load.
    fun refreshBeerStat() {
        viewModelScope.launch {
            val beerEntries = runCatching { beerRepository.getEntries() }.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(sinceLastBeer = BeerStats.sinceLast(beerEntries))
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
                    app.container.userRepository,
                    app.container.vehicleRepository,
                    app.container.homeLayoutStore,
                    app.container.shoppingListRepository,
                    app.container.inventoryRepository,
                    app.container.kanbanRepository,
                    app.container.pullCoordinator,
                )
            }
        }
    }
}
