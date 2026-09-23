package ch.mcfx.urs.fuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.FuelRepository
import ch.mcfx.urs.data.UserRepository
import ch.mcfx.urs.data.resolveDefaultVehicleId
import ch.mcfx.urs.data.remote.VehicleDto
import ch.mcfx.urs.data.remote.FillDto
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MonthlyFuelStat(
    val yearMonth: String,
    val totalCost: Float,
    val totalKm: Float,
    val avgConsumption: Float?,
)

data class FuelStatsUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val vehicles: List<VehicleDto> = emptyList(),
    val selectedVehicleId: String? = null,
    val avgConsumption: Float? = null,
    val avgPricePerLiter: Float? = null,
    val totalCost: Float = 0f,
    val totalKm: Float = 0f,
    val fillCount: Int = 0,
    val monthly: List<MonthlyFuelStat> = emptyList(),
    // One entry per fuel type (name to its own price history, oldest
    // first) — best-effort, loaded separately from the main fills/vehicles
    // load so a price-history hiccup never blocks the rest of the screen.
    val priceHistory: List<Pair<String, List<Float>>> = emptyList(),
)

// Statistics are computed entirely client-side from the raw fills list, same
// approach as urs-legacy-frontend's stats.vue — the backend has no
// aggregation endpoints of its own.
class FuelStatsViewModel(
    private val repository: FuelRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FuelStatsUiState())
    val uiState: StateFlow<FuelStatsUiState> = _uiState.asStateFlow()

    private var allFills: List<FillDto> = emptyList()

    init {
        viewModelScope.launch {
            try {
                val vehicles: List<VehicleDto>
                coroutineScope {
                    val vehiclesDeferred = async { repository.getVehicles() }
                    val fillsDeferred = async { repository.getFills() }
                    vehicles = vehiclesDeferred.await()
                    allFills = fillsDeferred.await()
                }
                // Start scoped to the default vehicle rather than "All
                // vehicles"; the user can still widen it via the dropdown.
                runCatching { userRepository.refreshDefaultVehicleId() }
                // A container is never a plausible fallback default — it has
                // no odometer/consumption meaning (mcfx-urs/urs-android#87).
                val defaultVehicleId = resolveDefaultVehicleId(
                    userRepository.defaultVehicleId.value,
                    vehicles.filterNot { it.isContainer == "1" }.map { it.id },
                )
                _uiState.update { it.copy(vehicles = vehicles, selectedVehicleId = defaultVehicleId) }
                recompute()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.update { it.copy(loading = false, error = true) }
            }
        }
        viewModelScope.launch { loadPriceHistory() }
    }

    private suspend fun loadPriceHistory() {
        try {
            val fuelTypes = repository.getFuelTypes()
            val history = coroutineScope {
                fuelTypes.map { fuel -> fuel to async { repository.getFuelPrices(fuel.id) } }
                    .map { (fuel, deferred) ->
                        fuel.name to deferred.await()
                            .sortedBy { FuelStats.parseDate(it.date) ?: LocalDate.MIN }
                            .mapNotNull { it.price.toFloatOrNull() }
                    }
            }
            _uiState.update { it.copy(priceHistory = history) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort only — the rest of the screen doesn't depend on this.
        }
    }

    fun selectVehicle(vehicleId: String?) {
        _uiState.update { it.copy(selectedVehicleId = vehicleId) }
        recompute()
    }

    private fun recompute() {
        val vehicleId = _uiState.value.selectedVehicleId
        val fills = if (vehicleId != null) allFills.filter { it.vehicleId == vehicleId } else allFills

        val totalCost = fills.sumOf { fillCost(it) }.toFloat()
        val totalLiters = fills.sumOf { it.liters.toDoubleOrNull() ?: 0.0 }
        val avgPrice = if (totalLiters > 0.0) (totalCost / totalLiters).toFloat() else null

        val samples = FuelStats.consumptionSamples(fills)
        val totalKm = samples.sumOf { it.kmDriven.toDouble() }.toFloat()

        _uiState.update {
            it.copy(
                loading = false,
                error = false,
                avgConsumption = FuelStats.averageConsumptionL100Km(fills),
                avgPricePerLiter = avgPrice,
                totalCost = totalCost,
                totalKm = totalKm,
                fillCount = fills.size,
                monthly = monthlyStats(fills),
            )
        }
    }

    private fun monthlyStats(fills: List<FillDto>): List<MonthlyFuelStat> {
        val costByMonth = fills
            .mapNotNull { fill -> FuelStats.parseDate(fill.date)?.let { monthKey(it) to fillCost(fill) } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, costs) -> costs.sum().toFloat() }

        val samples = FuelStats.consumptionSamples(fills).groupBy { monthKey(it.date) }
        val kmByMonth = samples.mapValues { (_, s) -> s.sumOf { it.kmDriven.toDouble() }.toFloat() }
        val consumptionByMonth = samples.mapValues { (_, s) ->
            val km = s.sumOf { it.kmDriven.toDouble() }
            val liters = s.sumOf { it.liters.toDouble() }
            if (km > 0.0) (liters / km * 100.0).toFloat() else null
        }

        return (costByMonth.keys + kmByMonth.keys)
            .distinct()
            .sortedDescending()
            .map { month ->
                MonthlyFuelStat(
                    yearMonth = month,
                    totalCost = costByMonth[month] ?: 0f,
                    totalKm = kmByMonth[month] ?: 0f,
                    avgConsumption = consumptionByMonth[month],
                )
            }
    }

    // Must use the backend's FX-resolved fill_amount_chf, not
    // pricePerLiter*liters in the fill's own currency — a fill entered in
    // e.g. PLN would otherwise add its raw (much larger) numeric price
    // straight into a CHF total, inflating Total Cost/Avg Price per Liter.
    // amountChf is only blank for a moment right after creation, before the
    // async FX job (urs-backend's fx/job.go) resolves it — CHF fills fall
    // back to the raw math (rate is always 1.0 for them anyway), non-CHF
    // fills contribute 0 until resolved rather than mixing currencies.
    private fun fillCost(fill: FillDto): Double {
        fill.amountChf.toDoubleOrNull()?.let { return it }
        if (fill.currencyCode != "CHF") return 0.0
        val price = fill.pricePerLiter.toDoubleOrNull() ?: 0.0
        val liters = fill.liters.toDoubleOrNull() ?: 0.0
        return price * liters
    }

    private fun monthKey(date: LocalDate): String = "%04d-%02d".format(date.year, date.monthValue)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                FuelStatsViewModel(app.container.fuelRepository, app.container.userRepository)
            }
        }
    }
}
