package ch.mcfx.urs.data

import ch.mcfx.urs.data.local.BundledCurrencies
import ch.mcfx.urs.data.local.CarDao
import ch.mcfx.urs.data.local.CarEntity
import ch.mcfx.urs.data.local.CurrencyDao
import ch.mcfx.urs.data.local.CurrencyEntity
import ch.mcfx.urs.data.local.FillDao
import ch.mcfx.urs.data.local.FillEntity
import ch.mcfx.urs.data.local.FillingStationDao
import ch.mcfx.urs.data.local.FillingStationEntity
import ch.mcfx.urs.data.local.OutboxDao
import ch.mcfx.urs.data.local.OutboxFillPayload
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.remote.CarDto
import ch.mcfx.urs.data.remote.CurrencyDto
import ch.mcfx.urs.data.remote.FillDto
import ch.mcfx.urs.data.remote.FillingStationDto
import ch.mcfx.urs.data.remote.FillingStationPayload
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.sync.SyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class FuelRepository(
    private val api: UrsApi,
    private val fillDao: FillDao,
    private val fillingStationDao: FillingStationDao,
    private val currencyDao: CurrencyDao,
    private val carDao: CarDao,
    private val outboxDao: OutboxDao,
    private val syncManager: SyncManager,
    private val applicationScope: CoroutineScope,
    private val json: Json,
) {

    // Cars are never created from this app, so there's no outbox/pending
    // concern here — this cache exists purely so the Add-fill form's car
    // picker still has something to show on a cold start with no
    // connectivity (see refreshFromBackend for how it's kept warm).
    fun observeCars(): Flow<List<CarEntity>> = carDao.observeAll()

    // Still a direct REST read — used only by the statistics screen, which
    // this pass didn't move onto the offline-first Room path (see
    // FuelViewModel/observeCars for the one that did).
    suspend fun getCars(): List<CarDto> = api.getCars()

    fun observeFills(): Flow<List<FillEntity>> = fillDao.observeAll()

    fun observeStations(): Flow<List<FillingStationEntity>> = fillingStationDao.observeAll()

    fun observeCurrencies(): Flow<List<CurrencyEntity>> = currencyDao.observeAll()

    suspend fun seedCurrenciesIfEmpty() {
        if (currencyDao.count() == 0) currencyDao.upsertAll(BundledCurrencies.seed)
    }

    // Still direct REST reads — used only by the station-management and
    // statistics screens, which this pass didn't move onto the offline-first
    // Room path (see FuelViewModel for the one that did); both already
    // tolerate a plain network error via their own existing UI state.
    suspend fun getStations(): List<FillingStationDto> = emptyAsNull { api.getFillingStations() }

    suspend fun createStation(name: String, address: String = "") {
        api.createFillingStation(FillingStationPayload(name = name, address = address))
    }

    suspend fun getFills(): List<FillDto> =
        emptyAsNull { api.getFills() }.sortedByDescending { it.date }

    suspend fun getLastOdometer(carId: String): String? =
        emptyAsNull { api.getOdometer(carId) }
            .maxByOrNull { it.mileage.toFloatOrNull() ?: 0f }
            ?.mileage

    /**
     * Offline-first write path — the *only* way a fill gets created,
     * online or offline. Both writes below (the outbox row and the local
     * [FillEntity], status [SyncStatus.PENDING]) are local-only and
     * instant; a background sync attempt is fired immediately afterwards
     * but never awaited here — a slower periodic retry (see `SyncManager`/
     * `SyncWorker`) picks it up if this one doesn't land. Maintaining a
     * separate "call the API directly when online" fast path would double
     * the code paths to get right for no real benefit, since a reachable
     * sync resolves near-instantly anyway.
     */
    suspend fun createFill(
        car: CarEntity,
        station: FillingStationEntity?,
        date: String,
        odometer: String,
        pricePerLiter: String,
        liters: String,
        lastOdometer: String?,
        currencyCode: String,
        gpsLatitude: String?,
        gpsLongitude: String?,
    ) {
        val driven = lastOdometer
            ?.let { formatKm((odometer.toFloatOrNull() ?: 0f) - (it.toFloatOrNull() ?: 0f)) }
            ?: "0"
        val fullDate = "$date 00:00:00"

        val payload = OutboxFillPayload(
            carId = car.id,
            fuelId = car.fuelId,
            date = fullDate,
            odometer = odometer,
            pricePerLiter = pricePerLiter,
            liters = liters,
            driven = driven,
            currencyCode = currencyCode,
            stationId = station?.id,
            stationLatitude = gpsLatitude,
            stationLongitude = gpsLongitude,
        )

        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_FILL,
                payloadJson = json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )

        fillDao.insert(
            FillEntity(
                outboxId = outboxId,
                stationId = station?.id,
                carId = car.id,
                fuelId = car.fuelId,
                date = fullDate,
                pricePerLiter = pricePerLiter,
                liters = liters,
                odometer = odometer,
                driven = driven,
                currencyCode = currencyCode,
                syncStatus = SyncStatus.PENDING,
            ),
        )

        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Opportunistic backend refresh for the Room-cached fills/stations/
     * currencies — run when reachable, but never blocking the UI or
     * surfacing an error on failure: stale cached data beats an empty or
     * error screen. Never touches PENDING/FAILED rows, which exist solely
     * via the outbox replay path above.
     */
    suspend fun refreshFromBackend() {
        refreshQuietly { api.getFillingStations().forEach { fillingStationDao.upsert(it.toEntity()) } }
        refreshQuietly { emptyAsNull { api.getFills() }.forEach { fillDao.upsertFromServer(it.toEntity()) } }
        refreshQuietly {
            api.getCurrencies().takeIf { it.isNotEmpty() }?.let { currencyDao.upsertAll(it.map(CurrencyDto::toEntity)) }
        }
        refreshQuietly { api.getCars().takeIf { it.isNotEmpty() }?.let { carDao.upsertAll(it.map(CarDto::toEntity)) } }
    }

    private suspend fun refreshQuietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort only — see refreshFromBackend's doc comment.
        }
    }

    // The backend encodes empty result sets as JSON `null` instead of `[]`.
    private suspend fun <T> emptyAsNull(call: suspend () -> List<T>): List<T> =
        try {
            call()
        } catch (_: SerializationException) {
            emptyList()
        }

    private fun formatKm(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else value.toString()
}

private fun FillingStationDto.toEntity() = FillingStationEntity(
    id = id,
    name = name,
    counter = counter,
    address = address,
    latitude = latitude,
    longitude = longitude,
)

private fun FillDto.toEntity() = FillEntity(
    serverId = id.toLongOrNull(),
    outboxId = null,
    stationId = stationId,
    carId = carId,
    fuelId = fuelId,
    date = date,
    pricePerLiter = pricePerLiter,
    liters = liters,
    odometer = odometer,
    // Not returned by GET /fill (only relevant at creation time) — already
    // synced rows don't need it for anything the UI shows today.
    driven = "",
    currencyCode = currencyCode,
    syncStatus = SyncStatus.SYNCED,
)

private fun CurrencyDto.toEntity() = CurrencyEntity(code = code, name = name)

private fun CarDto.toEntity() = CarEntity(
    id = id,
    fuelId = fuelId,
    fuelName = fuelName,
    brand = brand,
    model = model,
    year = year,
)
