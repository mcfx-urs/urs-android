package ch.mcfx.urs.data.sync

import ch.mcfx.urs.data.local.BakePlanDao
import ch.mcfx.urs.data.local.BakePlanStepDao
import ch.mcfx.urs.data.local.FillDao
import ch.mcfx.urs.data.local.FillingStationDao
import ch.mcfx.urs.data.local.FillingStationEntity
import ch.mcfx.urs.data.local.InventoryDao
import ch.mcfx.urs.data.local.InventoryProductDao
import ch.mcfx.urs.data.local.ListDao
import ch.mcfx.urs.data.local.ListItemDao
import ch.mcfx.urs.data.local.LocationHistoryDao
import ch.mcfx.urs.data.local.NoteDao
import ch.mcfx.urs.data.local.OutboxBakePlanCancelPayload
import ch.mcfx.urs.data.local.OutboxBakePlanPayload
import ch.mcfx.urs.data.local.OutboxBakePlanStepUpdatePayload
import ch.mcfx.urs.data.local.OutboxDao
import ch.mcfx.urs.data.local.OutboxFillDeletePayload
import ch.mcfx.urs.data.local.OutboxFillPayload
import ch.mcfx.urs.data.local.OutboxFillUpdatePayload
import ch.mcfx.urs.data.local.OutboxInventoryDeletePayload
import ch.mcfx.urs.data.local.OutboxInventoryPayload
import ch.mcfx.urs.data.local.OutboxInventoryProductPayload
import ch.mcfx.urs.data.local.OutboxInventoryUpdatePayload
import ch.mcfx.urs.data.local.OutboxListDeletePayload
import ch.mcfx.urs.data.local.OutboxListItemDeletePayload
import ch.mcfx.urs.data.local.OutboxListItemPayload
import ch.mcfx.urs.data.local.OutboxListItemUpdatePayload
import ch.mcfx.urs.data.local.OutboxListPayload
import ch.mcfx.urs.data.local.OutboxListUpdatePayload
import ch.mcfx.urs.data.local.OutboxLocationHistoryPayload
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.local.OutboxNoteCreatePayload
import ch.mcfx.urs.data.local.OutboxNoteDeletePayload
import ch.mcfx.urs.data.local.OutboxNoteStatusPayload
import ch.mcfx.urs.data.local.OutboxNoteUpdatePayload
import ch.mcfx.urs.data.local.OutboxVehicleServiceDeletePayload
import ch.mcfx.urs.data.local.OutboxVehicleServicePayload
import ch.mcfx.urs.data.local.OutboxVehicleServiceUpdatePayload
import ch.mcfx.urs.data.local.OutboxWorkTimeEntryDeletePayload
import ch.mcfx.urs.data.local.OutboxWorkTimeEntryPayload
import ch.mcfx.urs.data.local.OutboxWorkTimeEntryUpdatePayload
import ch.mcfx.urs.data.local.VehicleServiceDao
import ch.mcfx.urs.data.local.VehicleServiceTagEntity
import ch.mcfx.urs.data.local.WorkTimeBreakEntity
import ch.mcfx.urs.data.local.WorkTimeDao
import ch.mcfx.urs.data.local.localInventoryId
import ch.mcfx.urs.data.local.localListId
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.data.remote.BakePlanCreatePayload
import ch.mcfx.urs.data.remote.BakePlanStepCreatePayload
import ch.mcfx.urs.data.remote.BakePlanStepPatchPayload
import ch.mcfx.urs.data.remote.FillPayload
import ch.mcfx.urs.data.remote.FillUpdatePayload
import ch.mcfx.urs.data.remote.InventoryPayload
import ch.mcfx.urs.data.remote.InventoryProductCreatePayload
import ch.mcfx.urs.data.remote.ListItemPayload
import ch.mcfx.urs.data.remote.ListItemUpdatePayload
import ch.mcfx.urs.data.remote.ListPayload
import ch.mcfx.urs.data.remote.LocationHistoryPayload
import ch.mcfx.urs.data.remote.NoteCreatePayload
import ch.mcfx.urs.data.remote.NoteStatusPatchPayload
import ch.mcfx.urs.data.remote.UrsApi
import ch.mcfx.urs.data.remote.VehicleServicePayload
import ch.mcfx.urs.data.remote.VehicleServiceTagDto
import ch.mcfx.urs.data.remote.WorkTimeBreakPayload
import ch.mcfx.urs.data.remote.WorkTimeEntryPayload
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * Single replay code path for the offline outbox, shared by both the
 * manual "Sync now" action and [SyncWorker] — so the two never diverge in
 * behavior, only in when they're triggered. [Mutex]-guarded so a periodic
 * worker run and a manual tap can never interleave against the same rows.
 *
 * No conflict resolution is implemented anywhere in this class: a single
 * user on a single device is the accepted scope for this outbox, so FIFO
 * replay order (oldest queued mutation first) is the only ordering
 * guarantee that's actually needed here.
 */
class SyncManager(
    private val api: UrsApi,
    private val fillDao: FillDao,
    private val fillingStationDao: FillingStationDao,
    private val workTimeDao: WorkTimeDao,
    private val inventoryDao: InventoryDao,
    private val inventoryProductDao: InventoryProductDao,
    private val listDao: ListDao,
    private val listItemDao: ListItemDao,
    private val locationHistoryDao: LocationHistoryDao,
    private val vehicleServiceDao: VehicleServiceDao,
    private val bakePlanDao: BakePlanDao,
    private val bakePlanStepDao: BakePlanStepDao,
    private val noteDao: NoteDao,
    private val outboxDao: OutboxDao,
    private val reachabilityChecker: ReachabilityChecker,
    private val syncStatusStore: SyncStatusStore,
    private val json: Json,
) {
    private val mutex = Mutex()

    // Same "yyyy-MM-dd HH:mm:ss", device-local-time convention as
    // BeerStats.DATE_FORMAT — the backend parses location_history_captured_at
    // (and every bake_plan/bake_plan_step DATETIME column) with Go's matching
    // "2006-01-02 15:04:05" layout. Reused for baking's millis<->string
    // conversion too, not just location history — same format, no reason
    // for a second identical formatter.
    private val locationHistoryDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** @return `true` if the backend was reachable and every queued mutation replayed cleanly. */
    suspend fun syncNow(): Boolean = mutex.withLock { replayOutbox() }

    private suspend fun replayOutbox(): Boolean {
        if (!reachabilityChecker.isReachable()) return false

        var allSucceeded = true
        // Continues past individual failures rather than aborting the whole
        // batch — one bad row (e.g. a since-deleted vehicle) shouldn't block
        // every other queued fill from syncing.
        for (mutation in outboxDao.pendingOrdered()) {
            if (!replay(mutation)) allSucceeded = false
        }
        // Also fires when the outbox was already empty — that's still a
        // legitimate "everything confirmed in sync" moment, since reaching
        // this point already required the backend to be reachable.
        if (allSucceeded) syncStatusStore.recordSuccess()
        return allSucceeded
    }

    private suspend fun replay(mutation: OutboxMutationEntity): Boolean {
        outboxDao.markSyncing(mutation.id)
        return try {
            when (mutation.type) {
                OutboxMutationEntity.TYPE_CREATE_FILL -> replayCreateFill(mutation)
                OutboxMutationEntity.TYPE_UPDATE_FILL -> replayUpdateFill(mutation)
                OutboxMutationEntity.TYPE_DELETE_FILL -> replayDeleteFill(mutation)
                OutboxMutationEntity.TYPE_CREATE_WORK_TIME_ENTRY -> replayCreateWorkTimeEntry(mutation)
                OutboxMutationEntity.TYPE_UPDATE_WORK_TIME_ENTRY -> replayUpdateWorkTimeEntry(mutation)
                OutboxMutationEntity.TYPE_DELETE_WORK_TIME_ENTRY -> replayDeleteWorkTimeEntry(mutation)
                OutboxMutationEntity.TYPE_CREATE_INVENTORY -> replayCreateInventory(mutation)
                OutboxMutationEntity.TYPE_UPDATE_INVENTORY -> replayUpdateInventory(mutation)
                OutboxMutationEntity.TYPE_DELETE_INVENTORY -> replayDeleteInventory(mutation)
                OutboxMutationEntity.TYPE_CREATE_INVENTORY_PRODUCT -> replayCreateInventoryProduct(mutation)
                OutboxMutationEntity.TYPE_CREATE_LIST -> replayCreateList(mutation)
                OutboxMutationEntity.TYPE_UPDATE_LIST -> replayUpdateList(mutation)
                OutboxMutationEntity.TYPE_DELETE_LIST -> replayDeleteList(mutation)
                OutboxMutationEntity.TYPE_CREATE_LIST_ITEM -> replayCreateListItem(mutation)
                OutboxMutationEntity.TYPE_UPDATE_LIST_ITEM -> replayUpdateListItem(mutation)
                OutboxMutationEntity.TYPE_DELETE_LIST_ITEM -> replayDeleteListItem(mutation)
                OutboxMutationEntity.TYPE_CREATE_LOCATION_HISTORY -> replayCreateLocationHistory(mutation)
                OutboxMutationEntity.TYPE_CREATE_VEHICLE_SERVICE -> replayCreateVehicleService(mutation)
                OutboxMutationEntity.TYPE_UPDATE_VEHICLE_SERVICE -> replayUpdateVehicleService(mutation)
                OutboxMutationEntity.TYPE_DELETE_VEHICLE_SERVICE -> replayDeleteVehicleService(mutation)
                OutboxMutationEntity.TYPE_CREATE_BAKE_PLAN -> replayCreateBakePlan(mutation)
                OutboxMutationEntity.TYPE_UPDATE_BAKE_PLAN_STEP -> replayUpdateBakePlanStep(mutation)
                OutboxMutationEntity.TYPE_CANCEL_BAKE_PLAN -> replayCancelBakePlan(mutation)
                OutboxMutationEntity.TYPE_CREATE_NOTE -> replayCreateNote(mutation)
                OutboxMutationEntity.TYPE_UPDATE_NOTE -> replayUpdateNote(mutation)
                OutboxMutationEntity.TYPE_UPDATE_NOTE_STATUS -> replayUpdateNoteStatus(mutation)
                OutboxMutationEntity.TYPE_DELETE_NOTE -> replayDeleteNote(mutation)
                else -> {
                    // Forward-compat placeholder — nothing else is queued today.
                    outboxDao.markFailed(mutation.id, "unknown outbox mutation type: ${mutation.type}")
                    false
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            outboxDao.markFailed(mutation.id, e.message ?: "network error")
            false
        } catch (e: HttpException) {
            outboxDao.markFailed(mutation.id, "HTTP ${e.code()}")
            false
        } catch (e: Exception) {
            // Catches anything else that can go wrong turning a response into
            // a result (e.g. a response shape the client doesn't expect) —
            // one bad row must fail safely, not take the whole app down.
            outboxDao.markFailed(mutation.id, e.message ?: e::class.simpleName ?: "sync error")
            false
        }
    }

    private suspend fun replayCreateFill(mutation: OutboxMutationEntity): Boolean {
        val localFill = fillDao.getByOutboxId(mutation.id) ?: run {
            // No local row references this mutation any more — nothing left
            // to reconcile against, drop the orphaned outbox row.
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxFillPayload.serializer(), mutation.payloadJson)

        // Station-counter race fix: read the *current* locally-cached
        // counter here, at replay time, rather than trusting whatever was
        // cached back when the form was submitted — two fills queued
        // offline against the same real station must not both compute the
        // same stale "+1" from the same stale base.
        val stationCounter = payload.stationId
            ?.let { fillingStationDao.getById(it)?.counter?.toIntOrNull() }
            ?.plus(1)
            ?.toString()
            // Ad-hoc station: no prior counter exists yet, the backend
            // starts a brand new station's counter at 1.
            ?: "1"

        val response = api.createFill(
            FillPayload(
                date = payload.date,
                vehicleId = payload.vehicleId,
                stationId = payload.stationId,
                fuelId = payload.fuelId,
                pricePerLiter = payload.pricePerLiter,
                liters = payload.liters,
                odometer = payload.odometer,
                driven = payload.driven,
                stationCounter = stationCounter,
                isFullTank = if (payload.isFullTank) "1" else "0",
                currencyCode = payload.currencyCode,
                stationLatitude = payload.stationLatitude,
                stationLongitude = payload.stationLongitude,
            ),
        )

        fillDao.markSynced(localFill.id, response.id.toLongOrNull() ?: 0L, response.stationId)

        if (payload.stationId == null) {
            // Ad-hoc station: it now exists server-side under
            // response.stationId — cache it locally (the backend itself
            // never surfaces gps_auto stations back through the picker
            // endpoint, so this is the only place this app ever learns
            // about it), tagged SOURCE_GPS_AUTO so the fuel-add picker
            // excludes it (FuelViewModel) while fill-history name lookups
            // (FuelScreen) still resolve it fine.
            fillingStationDao.upsert(
                FillingStationEntity(
                    id = response.stationId,
                    name = "Ad-hoc fuel stop",
                    counter = stationCounter,
                    address = "",
                    latitude = payload.stationLatitude ?: "",
                    longitude = payload.stationLongitude ?: "",
                    source = FillingStationEntity.SOURCE_GPS_AUTO,
                ),
            )
        } else {
            // Refresh the cached counter immediately so the *next* queued
            // fill against this same station (if any) computes its own
            // "+1" from an up-to-date base, not the value cached before
            // this replay.
            fillingStationDao.getById(payload.stationId)?.let {
                fillingStationDao.upsert(it.copy(counter = stationCounter))
            }
        }

        outboxDao.delete(mutation.id)
        return true
    }

    // No local-row lookup needed — payload.serverId identifies the target
    // directly (same reasoning as OutboxWorkTimeEntryUpdatePayload's doc
    // comment), and the PUT route returns no body to reconcile against.
    private suspend fun replayUpdateFill(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxFillUpdatePayload.serializer(), mutation.payloadJson)
        api.updateFill(
            payload.serverId,
            FillUpdatePayload(
                date = payload.date,
                vehicleId = payload.vehicleId,
                stationId = payload.stationId,
                fuelId = payload.fuelId,
                pricePerLiter = payload.pricePerLiter,
                liters = payload.liters,
                odometer = payload.odometer,
                currencyCode = payload.currencyCode,
                isFullTank = if (payload.isFullTank) "1" else "0",
            ),
        )
        outboxDao.delete(mutation.id)
        return true
    }

    // No local row to look up — deleteFill() already removed it immediately,
    // offline-first, before this mutation was ever queued (same shape as
    // replayDeleteWorkTimeEntry).
    private suspend fun replayDeleteFill(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxFillDeletePayload.serializer(), mutation.payloadJson)
        api.deleteFill(payload.serverId)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayCreateWorkTimeEntry(mutation: OutboxMutationEntity): Boolean {
        val localEntry = workTimeDao.getByOutboxId(mutation.id) ?: run {
            // No local row references this mutation any more — nothing left
            // to reconcile against, drop the orphaned outbox row.
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxWorkTimeEntryPayload.serializer(), mutation.payloadJson)

        val response = api.createWorkTimeEntry(
            WorkTimeEntryPayload(
                userId = payload.userId,
                date = payload.date,
                workStart = payload.workStart,
                workEnd = payload.workEnd,
                targetDailyHours = payload.targetDailyHours,
                paidBreak = if (payload.paidBreak) "1" else "0",
                breaks = payload.breaks.map { WorkTimeBreakPayload(startTime = it.startTime, endTime = it.endTime) },
            ),
        )

        workTimeDao.markSynced(localEntry.id, response.id.toLongOrNull() ?: 0L)
        workTimeDao.replaceBreaks(
            localEntry.id,
            response.breaks.map { WorkTimeBreakEntity(entryId = localEntry.id, startTime = it.startTime, endTime = it.endTime) },
        )

        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayUpdateWorkTimeEntry(mutation: OutboxMutationEntity): Boolean {
        val localEntry = workTimeDao.getByOutboxId(mutation.id) ?: run {
            // Local row no longer references this mutation (e.g. deleted
            // since it was queued) — nothing left to reconcile, drop it.
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxWorkTimeEntryUpdatePayload.serializer(), mutation.payloadJson)

        val response = api.updateWorkTimeEntry(
            payload.serverId,
            WorkTimeEntryPayload(
                userId = payload.userId,
                date = payload.date,
                workStart = payload.workStart,
                workEnd = payload.workEnd,
                targetDailyHours = payload.targetDailyHours,
                paidBreak = if (payload.paidBreak) "1" else "0",
                breaks = payload.breaks.map { WorkTimeBreakPayload(startTime = it.startTime, endTime = it.endTime) },
            ),
        )

        workTimeDao.markSynced(localEntry.id, response.id.toLongOrNull() ?: payload.serverId.toLongOrNull() ?: 0L)
        workTimeDao.replaceBreaks(
            localEntry.id,
            response.breaks.map { WorkTimeBreakEntity(entryId = localEntry.id, startTime = it.startTime, endTime = it.endTime) },
        )

        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayDeleteWorkTimeEntry(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxWorkTimeEntryDeletePayload.serializer(), mutation.payloadJson)
        // No local row to look up — deleteEntry() already removed it
        // immediately, offline-first, before this mutation was ever queued.
        api.deleteWorkTimeEntry(payload.serverId)
        outboxDao.delete(mutation.id)
        return true
    }

    /**
     * The backend's create-inventory route echoes the assigned id directly
     * in its response body (mirrors [replayCreateList]) — no re-fetch-and-
     * match-by-name workaround needed, unlike the old inventory-category
     * create this replaces.
     */
    private suspend fun replayCreateInventory(mutation: OutboxMutationEntity): Boolean {
        val localInventory = inventoryDao.getByOutboxId(mutation.id) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxInventoryPayload.serializer(), mutation.payloadJson)

        val response = api.createInventory(InventoryPayload(name = payload.name))

        inventoryDao.markSynced(localInventory.id, response.id)
        outboxDao.delete(mutation.id)
        return true
    }

    // No local-row lookup needed — payload.serverId identifies the target
    // directly (same reasoning as OutboxListUpdatePayload's doc comment),
    // and the PUT route returns no body to reconcile against.
    private suspend fun replayUpdateInventory(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxInventoryUpdatePayload.serializer(), mutation.payloadJson)
        api.updateInventory(payload.serverId, InventoryPayload(name = payload.name))
        outboxDao.delete(mutation.id)
        return true
    }

    // No local row to look up — deleteInventory() already removed it
    // immediately, offline-first, before this mutation was ever queued
    // (same shape as replayDeleteList).
    private suspend fun replayDeleteInventory(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxInventoryDeletePayload.serializer(), mutation.payloadJson)
        api.deleteInventory(payload.serverId)
        outboxDao.delete(mutation.id)
        return true
    }

    /**
     * The backend's create-inventory-product route also echoes the assigned
     * id directly (see `urs-backend`'s `postInventoryProduct`) — no re-fetch-
     * and-match workaround needed, unlike the old inventory-product create
     * this replaces. Resolves [OutboxInventoryProductPayload.inventoryId]
     * first, same stand-in-until-synced reasoning as [replayCreateListItem]'s
     * listId resolution — FIFO replay order means the parent inventory's own
     * create has normally already replayed earlier in this same pass; if
     * not, this mutation fails too and is simply retried next sync.
     * [OutboxInventoryProductPayload.catalogProductId] is forwarded as-is —
     * always a real id, never a stand-in (see that field's doc comment).
     */
    private suspend fun replayCreateInventoryProduct(mutation: OutboxMutationEntity): Boolean {
        val localProduct = inventoryProductDao.getByOutboxId(mutation.id) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxInventoryProductPayload.serializer(), mutation.payloadJson)

        val resolvedInventoryId = resolveInventoryId(payload.inventoryId) ?: run {
            outboxDao.markFailed(mutation.id, "parent inventory not yet synced")
            return false
        }

        val response = api.createInventoryProduct(
            InventoryProductCreatePayload(
                inventoryId = resolvedInventoryId,
                catalogProductId = payload.catalogProductId,
                quantity = payload.quantity?.toString().orEmpty(),
            ),
        )

        inventoryProductDao.markSynced(localProduct.id, response.id, resolvedInventoryId)
        outboxDao.delete(mutation.id)
        return true
    }

    /**
     * Unlike [replayCreateInventory], the backend's create-list route
     * echoes the assigned id directly in its response body (see
     * `urs-backend`'s `postList`) — no re-fetch-and-match-by-name workaround
     * needed here.
     */
    private suspend fun replayCreateList(mutation: OutboxMutationEntity): Boolean {
        val localList = listDao.getByOutboxId(mutation.id) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxListPayload.serializer(), mutation.payloadJson)

        val response = api.createList(ListPayload(name = payload.name))

        listDao.markSynced(localList.id, response.id)
        outboxDao.delete(mutation.id)
        return true
    }

    // No local-row lookup needed — payload.serverId identifies the target
    // directly (same reasoning as OutboxWorkTimeEntryUpdatePayload's doc
    // comment), and the PUT route returns no body to reconcile against.
    private suspend fun replayUpdateList(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxListUpdatePayload.serializer(), mutation.payloadJson)
        api.updateList(payload.serverId, ListPayload(name = payload.name))
        outboxDao.delete(mutation.id)
        return true
    }

    // No local row to look up — deleteList() already removed it immediately,
    // offline-first, before this mutation was ever queued (same shape as
    // replayDeleteWorkTimeEntry).
    private suspend fun replayDeleteList(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxListDeletePayload.serializer(), mutation.payloadJson)
        api.deleteList(payload.serverId)
        outboxDao.delete(mutation.id)
        return true
    }

    /**
     * The backend's create-list-item route also echoes the assigned id
     * directly (see `urs-backend`'s `postListItem`). Resolves
     * [OutboxListItemPayload.listId], same stand-in-until-synced reasoning as
     * [replayCreateInventoryProduct]'s inventoryId resolution — FIFO replay
     * order means the parent list's own create has normally already replayed
     * earlier in this same pass; if not, this mutation fails too and is
     * simply retried next sync. [OutboxListItemPayload.catalogProductId]
     * needs no such resolution — see that field's doc comment. There's also
     * no more "checked" state to forward once this create replays — the old
     * post-create `updateListItem` follow-up call this replaces is gone
     * along with it.
     */
    private suspend fun replayCreateListItem(mutation: OutboxMutationEntity): Boolean {
        val localItem = listItemDao.getByOutboxId(mutation.id) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxListItemPayload.serializer(), mutation.payloadJson)

        val resolvedListId = resolveListId(payload.listId) ?: run {
            outboxDao.markFailed(mutation.id, "parent list not yet synced")
            return false
        }

        val response = api.createListItem(
            ListItemPayload(
                listId = resolvedListId, catalogProductId = payload.catalogProductId, note = payload.note.orEmpty(),
                quantity = payload.quantity, onSale = payload.onSale,
            ),
        )

        listItemDao.markSynced(localItem.id, response.id, resolvedListId)
        outboxDao.delete(mutation.id)
        return true
    }

    // Same "no local-row lookup needed" reasoning as replayUpdateList.
    private suspend fun replayUpdateListItem(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxListItemUpdatePayload.serializer(), mutation.payloadJson)
        api.updateListItem(
            payload.serverId,
            ListItemUpdatePayload(note = payload.note.orEmpty(), quantity = payload.quantity, onSale = payload.onSale),
        )
        outboxDao.delete(mutation.id)
        return true
    }

    // Same "no local row to look up" reasoning as replayDeleteList.
    private suspend fun replayDeleteListItem(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxListItemDeletePayload.serializer(), mutation.payloadJson)
        api.deleteListItem(payload.serverId)
        outboxDao.delete(mutation.id)
        return true
    }

    /**
     * No station-counter-style side effect to reconcile beyond the id
     * (unlike [replayCreateFill]) — a location-history point has no other
     * server state that depends on it, so this is closer in shape to
     * [replayCreateInventory].
     */
    private suspend fun replayCreateLocationHistory(mutation: OutboxMutationEntity): Boolean {
        val localPoint = locationHistoryDao.getByOutboxId(mutation.id) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxLocationHistoryPayload.serializer(), mutation.payloadJson)

        val response = api.createLocationHistory(
            LocationHistoryPayload(
                latitude = payload.latitude.toString(),
                longitude = payload.longitude.toString(),
                accuracyMeters = payload.accuracyMeters?.toString().orEmpty(),
                capturedAt = Instant.ofEpochMilli(payload.capturedAt)
                    .atZone(ZoneId.systemDefault())
                    .format(locationHistoryDateFormat),
            ),
        )

        locationHistoryDao.markSynced(localPoint.id, response.id.toLongOrNull() ?: 0L)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayCreateVehicleService(mutation: OutboxMutationEntity): Boolean {
        val localService = vehicleServiceDao.getByOutboxId(mutation.id) ?: run {
            // No local row references this mutation any more — nothing left
            // to reconcile against, drop the orphaned outbox row.
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxVehicleServicePayload.serializer(), mutation.payloadJson)

        val response = api.createVehicleService(
            VehicleServicePayload(
                vehicleId = payload.vehicleId,
                date = payload.date,
                odometer = payload.odometer,
                provider = payload.provider,
                isDiy = if (payload.isDiy) "1" else "0",
                notes = payload.notes,
                costAmount = payload.costAmount,
                currencyCode = payload.currencyCode,
                tags = payload.tags.map { VehicleServiceTagDto(code = it.code, label = it.label.orEmpty()) },
            ),
        )

        vehicleServiceDao.markSynced(localService.id, response.id.toLongOrNull() ?: 0L)
        vehicleServiceDao.replaceTags(
            localService.id,
            response.tags.map {
                VehicleServiceTagEntity(serviceId = localService.id, code = it.code, label = it.label.ifBlank { null })
            },
        )

        outboxDao.delete(mutation.id)
        return true
    }

    // Mirrors replayUpdateWorkTimeEntry's shape (not replayUpdateFill's) —
    // the response's resolved tags still need to be written back against the
    // *local* service id via replaceTags, so the local row is looked up by
    // outboxId despite payload.serverId already identifying the backend row.
    private suspend fun replayUpdateVehicleService(mutation: OutboxMutationEntity): Boolean {
        val localService = vehicleServiceDao.getByOutboxId(mutation.id) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxVehicleServiceUpdatePayload.serializer(), mutation.payloadJson)

        val response = api.updateVehicleService(
            payload.serverId,
            VehicleServicePayload(
                vehicleId = payload.vehicleId,
                date = payload.date,
                odometer = payload.odometer,
                provider = payload.provider,
                isDiy = if (payload.isDiy) "1" else "0",
                notes = payload.notes,
                costAmount = payload.costAmount,
                currencyCode = payload.currencyCode,
                tags = payload.tags.map { VehicleServiceTagDto(code = it.code, label = it.label.orEmpty()) },
            ),
        )

        vehicleServiceDao.markSynced(localService.id, response.id.toLongOrNull() ?: payload.serverId.toLongOrNull() ?: 0L)
        vehicleServiceDao.replaceTags(
            localService.id,
            response.tags.map {
                VehicleServiceTagEntity(serviceId = localService.id, code = it.code, label = it.label.ifBlank { null })
            },
        )

        outboxDao.delete(mutation.id)
        return true
    }

    // No local row to look up — deleteEntry() already removed it
    // immediately, offline-first, before this mutation was ever queued (same
    // shape as replayDeleteFill).
    private suspend fun replayDeleteVehicleService(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxVehicleServiceDeletePayload.serializer(), mutation.payloadJson)
        api.deleteVehicleService(payload.serverId)
        outboxDao.delete(mutation.id)
        return true
    }

    // Steps ride along in the single plan-create payload — no per-step
    // outbox row, unlike list/list-item's separate child-mutation pattern
    // (see OutboxBakePlanPayload's doc comment), so this is the only place
    // a plan's steps ever get reconciled with their server ids.
    private suspend fun replayCreateBakePlan(mutation: OutboxMutationEntity): Boolean {
        val localPlan = bakePlanDao.getByOutboxId(mutation.id) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxBakePlanPayload.serializer(), mutation.payloadJson)
        // Read before markSynced below changes localPlan's publicId out from
        // under this lookup.
        val localSteps = bakePlanStepDao.getByPlanId(localPlan.publicId)

        val response = api.createBakePlan(
            BakePlanCreatePayload(
                templateKey = payload.templateKey,
                anchorAt = payload.anchorAtMillis.toBakingDateString(),
                steps = payload.steps.map { step ->
                    BakePlanStepCreatePayload(
                        index = step.index.toString(),
                        label = step.label,
                        plannedAt = step.plannedAtMillis.toBakingDateString(),
                    )
                },
            ),
        )

        bakePlanDao.markSynced(localPlan.id, response.id)
        // Matched by index order — both lists were built/inserted in the
        // same template order, and InsertBakePlan (urs-backend) returns
        // steps in the same insertion order it received them in.
        localSteps.sortedBy { it.stepIndex }
            .zip(response.steps.sortedBy { it.index.toIntOrNull() ?: 0 })
            .forEach { (local, remote) -> bakePlanStepDao.markSyncedWithServerId(local.id, remote.id, response.id) }

        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayUpdateBakePlanStep(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxBakePlanStepUpdatePayload.serializer(), mutation.payloadJson)
        val step = bakePlanStepDao.getById(payload.localStepId) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val serverId = step.serverId ?: run {
            // markSyncedWithServerId stamps both serverId and the real planId
            // together in the same replayCreateBakePlan call, so a null
            // serverId here always means the parent plan isn't synced yet
            // either — retried on the next sync pass once that mutation goes
            // through, same "not yet synced, retry later" shape as
            // replayCreateListItem's still-pending-parent case.
            outboxDao.markFailed(mutation.id, "bake plan step not yet synced")
            return false
        }

        api.updateBakePlanStep(
            step.planId,
            serverId,
            BakePlanStepPatchPayload(
                done = payload.done,
                snoozedAt = payload.snoozedAtMillis?.toBakingDateString(),
            ),
        )
        bakePlanStepDao.markSynced(step.id)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayCancelBakePlan(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxBakePlanCancelPayload.serializer(), mutation.payloadJson)
        val plan = bakePlanDao.getById(payload.localPlanId) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val serverId = plan.serverId ?: run {
            outboxDao.markFailed(mutation.id, "bake plan not yet synced")
            return false
        }
        api.cancelBakePlan(serverId)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayCreateNote(mutation: OutboxMutationEntity): Boolean {
        val localNote = noteDao.getByOutboxId(mutation.id) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxNoteCreatePayload.serializer(), mutation.payloadJson)
        val response = api.createNote(
            NoteCreatePayload(
                title = payload.title,
                content = payload.content,
                reminderAt = payload.reminderAtMillis?.toBakingDateString(),
                tags = payload.tags,
            ),
        )
        noteDao.markSynced(localNote.id, response.id)
        outboxDao.delete(mutation.id)
        return true
    }

    // Identifies its target by localNoteId (payload.localNoteId), resolved to
    // a serverId here at replay time — same "not yet synced, retry later"
    // shape as replayUpdateBakePlanStep, since a note can be edited before
    // its own create mutation has synced.
    private suspend fun replayUpdateNote(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxNoteUpdatePayload.serializer(), mutation.payloadJson)
        val note = noteDao.getById(payload.localNoteId) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val serverId = note.serverId ?: run {
            outboxDao.markFailed(mutation.id, "note not yet synced")
            return false
        }
        api.updateNote(
            serverId,
            NoteCreatePayload(
                title = payload.title,
                content = payload.content,
                reminderAt = payload.reminderAtMillis?.toBakingDateString(),
                tags = payload.tags,
            ),
        )
        noteDao.clearPending(note.id)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayUpdateNoteStatus(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxNoteStatusPayload.serializer(), mutation.payloadJson)
        val note = noteDao.getById(payload.localNoteId) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val serverId = note.serverId ?: run {
            outboxDao.markFailed(mutation.id, "note not yet synced")
            return false
        }
        api.updateNoteStatus(serverId, NoteStatusPatchPayload(status = payload.status))
        noteDao.clearPending(note.id)
        outboxDao.delete(mutation.id)
        return true
    }

    // Unlike update/status above, the local row is already gone by replay
    // time (offline-first delete) — the payload already carries the
    // serverId captured at queue time, nothing left to resolve here.
    private suspend fun replayDeleteNote(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxNoteDeletePayload.serializer(), mutation.payloadJson)
        api.deleteNote(payload.serverId)
        outboxDao.delete(mutation.id)
        return true
    }

    private fun Long.toBakingDateString(): String =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(locationHistoryDateFormat)

    private suspend fun resolveListId(value: String): String? {
        val localId = localListId(value) ?: return value
        return listDao.getById(localId)?.serverId
    }

    private suspend fun resolveInventoryId(value: String): String? {
        val localId = localInventoryId(value) ?: return value
        return inventoryDao.getById(localId)?.serverId
    }
}
