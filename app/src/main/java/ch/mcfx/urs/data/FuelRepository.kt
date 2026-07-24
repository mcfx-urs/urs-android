package ch.mcfx.urs.data

import ch.mcfx.urs.data.local.BundledCurrencies
import ch.mcfx.urs.data.local.VehicleDao
import ch.mcfx.urs.data.local.VehicleEntity
import ch.mcfx.urs.data.local.CurrencyDao
import ch.mcfx.urs.data.local.CurrencyEntity
import ch.mcfx.urs.data.local.FillDao
import ch.mcfx.urs.data.local.FillEntity
import ch.mcfx.urs.data.local.FillingStationDao
import ch.mcfx.urs.data.local.FillingStationEntity
import ch.mcfx.urs.data.local.OutboxDao
import ch.mcfx.urs.data.local.OutboxFillDeletePayload
import ch.mcfx.urs.data.local.OutboxFillPayload
import ch.mcfx.urs.data.local.OutboxFillUpdatePayload
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.remote.VehicleDto
import ch.mcfx.urs.data.remote.CurrencyDto
import ch.mcfx.urs.data.remote.FillDto
import ch.mcfx.urs.data.remote.FillingStationDto
import ch.mcfx.urs.data.remote.FillingStationPayload
import ch.mcfx.urs.data.remote.FuelDto
import ch.mcfx.urs.data.remote.FuelPriceDto
import ch.mcfx.urs.data.remote.FuelPricePayload
import ch.mcfx.urs.data.remote.GeocodeResultDto
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.sync.SyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class FillEditData(val fill: FillEntity, val vehicle: VehicleEntity, val station: FillingStationEntity?)

class FuelRepository(
    private val api: UrsApi,
    private val fillDao: FillDao,
    private val fillingStationDao: FillingStationDao,
    private val currencyDao: CurrencyDao,
    private val vehicleDao: VehicleDao,
    private val outboxDao: OutboxDao,
    private val syncManager: SyncManager,
    private val applicationScope: CoroutineScope,
    private val json: Json,
) {

    // Vehicles are never created from this app, so there's no outbox/pending
    // concern here — this cache exists purely so the Add-fill form's vehicle
    // picker still has something to show on a cold start with no
    // connectivity (see refreshFromBackend for how it's kept warm).
    fun observeVehicles(): Flow<List<VehicleEntity>> = vehicleDao.observeAll()

    // Still a direct REST read — used only by the statistics screen, which
    // this pass didn't move onto the offline-first Room path (see
    // FuelViewModel/observeVehicles for the one that did).
    suspend fun getVehicles(): List<VehicleDto> = api.getVehicles()

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

    suspend fun createStation(name: String, address: String = "", latitude: String? = null, longitude: String? = null) {
        api.createFillingStation(
            FillingStationPayload(
                name = name,
                address = address,
                latitude = latitude ?: "",
                longitude = longitude ?: "",
            ),
        )
    }

    suspend fun updateStation(id: String, name: String, address: String = "", latitude: String? = null, longitude: String? = null) {
        api.updateFillingStation(
            id,
            FillingStationPayload(
                name = name,
                address = address,
                latitude = latitude ?: "",
                longitude = longitude ?: "",
            ),
        )
    }

    // Direct REST reads/writes, same as getStations/createStation above —
    // recording a standalone price observation is a rare, non-critical
    // action with no edit/delete lifecycle, so it doesn't warrant the
    // outbox/Room-entity machinery createFill uses.
    suspend fun getFuelTypes(): List<FuelDto> = emptyAsNull { api.getFuelTypes() }

    suspend fun submitFuelPrice(date: String, price: String, fuelTypeId: String, stationId: String): FuelPriceDto =
        api.createFuelPrice(FuelPricePayload(date = date, price = price, fuelTypeId = fuelTypeId, stationId = stationId))

    // Direct, unbuffered REST call — a read-only lookup has no reason to go
    // through the outbox. Exceptions (including a 404 "no match") propagate
    // to the caller, same as every other function in this file.
    suspend fun geocode(address: String): GeocodeResultDto = api.geocode(address)

    suspend fun getFills(): List<FillDto> =
        emptyAsNull { api.getFills() }.sortedByDescending { it.date }

    suspend fun getLastOdometer(vehicleId: String): String? =
        emptyAsNull { api.getOdometer(vehicleId) }
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
        vehicle: VehicleEntity,
        station: FillingStationEntity?,
        date: String,
        odometer: String,
        pricePerLiter: String,
        liters: String,
        lastOdometer: String?,
        currencyCode: String,
        gpsLatitude: String?,
        gpsLongitude: String?,
        isFullTank: Boolean,
    ) {
        val driven = lastOdometer
            ?.let { formatKm((odometer.toFloatOrNull() ?: 0f) - (it.toFloatOrNull() ?: 0f)) }
            ?: "0"
        val fullDate = "$date 00:00:00"

        val payload = OutboxFillPayload(
            vehicleId = vehicle.id,
            fuelId = vehicle.fuelId,
            date = fullDate,
            odometer = odometer,
            pricePerLiter = pricePerLiter,
            liters = liters,
            driven = driven,
            isFullTank = isFullTank,
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
                vehicleId = vehicle.id,
                fuelId = vehicle.fuelId,
                date = fullDate,
                pricePerLiter = pricePerLiter,
                liters = liters,
                odometer = odometer,
                driven = driven,
                isFullTank = isFullTank,
                currencyCode = currencyCode,
                syncStatus = SyncStatus.PENDING,
            ),
        )

        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Resolves a local fill row into the entities its edit form needs
     * (looked up fresh via DAOs, not [observeFills]'s cached Flow — that
     * Flow may not have emitted yet on a cold navigation straight into the
     * edit screen, same reasoning as WorkTimeViewModel.openFormForEdit).
     * Returns null if the fill or its vehicle no longer exists locally.
     */
    suspend fun getFillForEdit(localId: Long): FillEditData? {
        val fill = fillDao.getById(localId) ?: return null
        val vehicle = vehicleDao.getById(fill.vehicleId) ?: return null
        val station = fill.stationId?.let { fillingStationDao.getById(it) }
        return FillEditData(fill, vehicle, station)
    }

    /**
     * Offline-first edit path, same shape as [WorkTimeRepository.updateEntry].
     * A fill that hasn't reached the server yet (no [FillEntity.serverId])
     * has its still-pending create mutation's payload rewritten in place —
     * GPS/ad-hoc station reassignment is still possible here, since that
     * mutation still goes through the ad-hoc-creation-capable create route.
     * An already-synced fill cancels whatever mutation is still pending for
     * it and queues a fresh `PUT` update instead — [station] must be
     * non-null by this point (enforced by FuelAddScreen locking the GPS
     * toggle once editing an already-synced fill), since the PUT route has
     * no ad-hoc-station-creation branch.
     */
    suspend fun updateFill(
        localId: Long,
        vehicle: VehicleEntity,
        station: FillingStationEntity?,
        date: String,
        odometer: String,
        pricePerLiter: String,
        liters: String,
        currencyCode: String,
        gpsLatitude: String?,
        gpsLongitude: String?,
        isFullTank: Boolean,
    ) {
        val current = fillDao.getById(localId) ?: return
        val fullDate = "$date 00:00:00"

        val outboxId = if (current.serverId == null) {
            val payload = OutboxFillPayload(
                vehicleId = vehicle.id,
                fuelId = vehicle.fuelId,
                date = fullDate,
                odometer = odometer,
                pricePerLiter = pricePerLiter,
                liters = liters,
                driven = current.driven,
                isFullTank = isFullTank,
                currencyCode = currencyCode,
                stationId = station?.id,
                stationLatitude = gpsLatitude,
                stationLongitude = gpsLongitude,
            )
            current.outboxId?.let { outboxDao.updatePayload(it, json.encodeToString(payload)) }
            current.outboxId
        } else {
            val stationId = station?.id ?: return
            current.outboxId?.let { outboxDao.delete(it) }
            val payload = OutboxFillUpdatePayload(
                serverId = current.serverId.toString(),
                vehicleId = vehicle.id,
                fuelId = vehicle.fuelId,
                date = fullDate,
                stationId = stationId,
                odometer = odometer,
                pricePerLiter = pricePerLiter,
                liters = liters,
                isFullTank = isFullTank,
                currencyCode = currencyCode,
            )
            outboxDao.insert(
                OutboxMutationEntity(
                    type = OutboxMutationEntity.TYPE_UPDATE_FILL,
                    payloadJson = json.encodeToString(payload),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }

        fillDao.updateFields(
            localId, station?.id, fullDate, pricePerLiter, liters, odometer, isFullTank, currencyCode,
            SyncStatus.PENDING, outboxId,
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Offline-first delete path, same shape as [WorkTimeRepository.deleteEntry].
     * The local row is always removed immediately; a server-side delete is
     * only queued if the server ever actually learned about this fill
     * ([FillEntity.serverId] set).
     */
    suspend fun deleteFill(localId: Long) {
        val current = fillDao.getById(localId) ?: return
        current.outboxId?.let { outboxDao.delete(it) }
        fillDao.deleteEntry(localId)

        val serverId = current.serverId ?: return
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_DELETE_FILL,
                payloadJson = json.encodeToString(OutboxFillDeletePayload(serverId = serverId.toString())),
                createdAt = System.currentTimeMillis(),
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
        refreshQuietly { api.getVehicles().takeIf { it.isNotEmpty() }?.let { vehicleDao.upsertAll(it.map(VehicleDto::toEntity)) } }
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
    vehicleId = vehicleId,
    fuelId = fuelId,
    date = date,
    pricePerLiter = pricePerLiter,
    liters = liters,
    odometer = odometer,
    // Not returned by GET /fill (only relevant at creation time) — already
    // synced rows don't need it for anything the UI shows today.
    driven = "",
    isFullTank = isFullTank == "1",
    currencyCode = currencyCode,
    syncStatus = SyncStatus.SYNCED,
)

private fun CurrencyDto.toEntity() = CurrencyEntity(code = code, name = name)

private fun VehicleDto.toEntity() = VehicleEntity(
    id = id,
    fuelId = fuelId,
    fuelName = fuelName,
    brand = brand,
    model = model,
    year = year,
)
