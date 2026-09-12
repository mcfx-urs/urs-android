package ch.mcfx.urs.data

import ch.mcfx.urs.data.local.OutboxDao
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.local.OutboxVehicleServiceDeletePayload
import ch.mcfx.urs.data.local.OutboxVehicleServicePayload
import ch.mcfx.urs.data.local.OutboxVehicleServiceTagPayload
import ch.mcfx.urs.data.local.OutboxVehicleServiceUpdatePayload
import ch.mcfx.urs.data.local.SyncStatus
import ch.mcfx.urs.data.local.VehicleServiceDao
import ch.mcfx.urs.data.local.VehicleServiceEntity
import ch.mcfx.urs.data.local.VehicleServiceTagEntity
import ch.mcfx.urs.data.local.VehicleServiceWithTags
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.remote.VehicleServiceDto
import ch.mcfx.urs.data.remote.VehicleServiceTagDto
import ch.mcfx.urs.data.sync.SyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ServiceRepository(
    private val api: UrsApi,
    private val vehicleServiceDao: VehicleServiceDao,
    private val outboxDao: OutboxDao,
    private val syncManager: SyncManager,
    private val applicationScope: CoroutineScope,
    private val json: Json,
) {

    fun observeServices(vehicleId: String? = null): Flow<List<VehicleServiceWithTags>> =
        if (vehicleId == null) vehicleServiceDao.observeAll() else vehicleServiceDao.observeForVehicle(vehicleId)

    /**
     * Offline-first write path, same shape as [WorkTimeRepository.createEntry]:
     * both writes below are local-only and instant, a background sync
     * attempt fires immediately afterwards but is never awaited here.
     */
    suspend fun createService(
        vehicleId: String,
        date: String,
        odometer: String,
        provider: String,
        isDiy: Boolean,
        notes: String,
        costAmount: String,
        currencyCode: String,
        tags: List<OutboxVehicleServiceTagPayload>,
    ) {
        val payload = OutboxVehicleServicePayload(
            vehicleId = vehicleId,
            date = date,
            odometer = odometer,
            provider = provider,
            isDiy = isDiy,
            notes = notes,
            costAmount = costAmount,
            currencyCode = currencyCode,
            tags = tags,
        )

        val outboxId = outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_VEHICLE_SERVICE,
                payloadJson = json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )

        val serviceId = vehicleServiceDao.insertService(
            VehicleServiceEntity(
                outboxId = outboxId,
                vehicleId = vehicleId,
                date = date,
                odometer = odometer,
                provider = provider,
                isDiy = isDiy,
                notes = notes,
                costAmount = costAmount,
                currencyCode = currencyCode,
                syncStatus = SyncStatus.PENDING,
            ),
        )
        vehicleServiceDao.insertTags(
            tags.map { VehicleServiceTagEntity(serviceId = serviceId, code = it.code, label = it.label) },
        )

        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Resolves a local service row (with its tags) for an edit form —
     * looked up fresh via the DAO, not [observeServices]'s cached Flow, same
     * reasoning as [FuelRepository.getFillForEdit].
     */
    suspend fun getServiceForEdit(localId: Long): VehicleServiceWithTags? = vehicleServiceDao.getWithTagsById(localId)

    /**
     * Offline-first edit path, same shape as [WorkTimeRepository.updateEntry].
     * A service that hasn't reached the server yet (no
     * [VehicleServiceEntity.serverId]) has its still-pending create
     * mutation's payload rewritten in place; an already-synced service
     * cancels whatever mutation is still pending for it and queues a fresh
     * update instead.
     */
    suspend fun updateService(
        localId: Long,
        vehicleId: String,
        date: String,
        odometer: String,
        provider: String,
        isDiy: Boolean,
        notes: String,
        costAmount: String,
        currencyCode: String,
        tags: List<OutboxVehicleServiceTagPayload>,
    ) {
        val current = vehicleServiceDao.getById(localId) ?: return

        val outboxId = if (current.serverId == null) {
            val payload = OutboxVehicleServicePayload(
                vehicleId = vehicleId,
                date = date,
                odometer = odometer,
                provider = provider,
                isDiy = isDiy,
                notes = notes,
                costAmount = costAmount,
                currencyCode = currencyCode,
                tags = tags,
            )
            current.outboxId?.let { outboxDao.updatePayload(it, json.encodeToString(payload)) }
            current.outboxId
        } else {
            current.outboxId?.let { outboxDao.delete(it) }
            val payload = OutboxVehicleServiceUpdatePayload(
                serverId = current.serverId.toString(),
                vehicleId = vehicleId,
                date = date,
                odometer = odometer,
                provider = provider,
                isDiy = isDiy,
                notes = notes,
                costAmount = costAmount,
                currencyCode = currencyCode,
                tags = tags,
            )
            outboxDao.insert(
                OutboxMutationEntity(
                    type = OutboxMutationEntity.TYPE_UPDATE_VEHICLE_SERVICE,
                    payloadJson = json.encodeToString(payload),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }

        vehicleServiceDao.updateFields(
            localId, vehicleId, date, odometer, provider, isDiy, notes, costAmount, currencyCode,
            SyncStatus.PENDING, outboxId,
        )
        vehicleServiceDao.replaceTags(
            localId,
            tags.map { VehicleServiceTagEntity(serviceId = localId, code = it.code, label = it.label) },
        )

        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Offline-first delete path, same shape as [WorkTimeRepository.deleteEntry].
     * The local row is always removed immediately; a server-side delete is
     * only queued if the server ever actually learned about this service
     * ([VehicleServiceEntity.serverId] set).
     */
    suspend fun deleteService(localId: Long) {
        val current = vehicleServiceDao.getById(localId) ?: return
        current.outboxId?.let { outboxDao.delete(it) }
        vehicleServiceDao.deleteEntry(localId)

        val serverId = current.serverId ?: return
        outboxDao.insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_DELETE_VEHICLE_SERVICE,
                payloadJson = json.encodeToString(OutboxVehicleServiceDeletePayload(serverId = serverId.toString())),
                createdAt = System.currentTimeMillis(),
            ),
        )
        applicationScope.launch { syncManager.syncNow() }
    }

    /**
     * Opportunistic backend refresh, same best-effort shape as
     * [WorkTimeRepository.refreshFromBackend] — never blocks the UI or
     * surfaces an error, stale cached data beats an empty or error screen.
     * Never touches PENDING/FAILED rows, which exist solely via the outbox
     * replay path above.
     */
    /** @return `true` if the refresh completed cleanly (see [PullCoordinator]). */
    suspend fun refreshFromBackend(): Boolean {
        try {
            api.getVehicleServices().forEach { dto ->
                vehicleServiceDao.upsertFromServer(dto.toEntity(), dto.tags.map { it.toEntity() })
            }
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best-effort only — see doc comment above.
            android.util.Log.w("ServiceRepository", "refreshFromBackend failed", e)
            return false
        }
    }
}

private fun VehicleServiceDto.toEntity() = VehicleServiceEntity(
    serverId = id.toLongOrNull(),
    outboxId = null,
    vehicleId = vehicleId,
    date = date,
    odometer = odometer,
    provider = provider,
    isDiy = isDiy == "1",
    notes = notes,
    costAmount = costAmount,
    currencyCode = currencyCode,
    syncStatus = SyncStatus.SYNCED,
)

private fun VehicleServiceTagDto.toEntity() = VehicleServiceTagEntity(
    serviceId = 0, // overwritten by VehicleServiceDao.upsertFromServer once the parent's local id is known
    code = code,
    label = label.ifBlank { null },
)
