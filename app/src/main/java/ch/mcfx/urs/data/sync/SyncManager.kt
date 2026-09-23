package ch.mcfx.urs.data.sync

import ch.mcfx.urs.data.local.AssetCommentDao
import ch.mcfx.urs.data.local.AssetComponentDao
import ch.mcfx.urs.data.local.AssetDao
import ch.mcfx.urs.data.local.BakePlanDao
import ch.mcfx.urs.data.local.BakePlanStepDao
import ch.mcfx.urs.data.local.FillDao
import ch.mcfx.urs.data.local.FillingStationDao
import ch.mcfx.urs.data.local.FillingStationEntity
import ch.mcfx.urs.data.local.InventoryDao
import ch.mcfx.urs.data.local.InventoryProductDao
import ch.mcfx.urs.data.local.KanbanBoardDao
import ch.mcfx.urs.data.local.KanbanCardDao
import ch.mcfx.urs.data.local.KanbanChecklistItemDao
import ch.mcfx.urs.data.local.KanbanColumnDao
import ch.mcfx.urs.data.local.ListDao
import ch.mcfx.urs.data.local.ListItemDao
import ch.mcfx.urs.data.local.LocationHistoryDao
import ch.mcfx.urs.data.local.NoteDao
import ch.mcfx.urs.data.local.OutboxAssetCommentDeletePayload
import ch.mcfx.urs.data.local.OutboxAssetCommentPayload
import ch.mcfx.urs.data.local.OutboxAssetCommentUpdatePayload
import ch.mcfx.urs.data.local.OutboxAssetComponentDeletePayload
import ch.mcfx.urs.data.local.OutboxAssetComponentPayload
import ch.mcfx.urs.data.local.OutboxAssetComponentUpdatePayload
import ch.mcfx.urs.data.local.OutboxAssetDeletePayload
import ch.mcfx.urs.data.local.OutboxAssetPayload
import ch.mcfx.urs.data.local.OutboxAssetUpdatePayload
import ch.mcfx.urs.data.local.OutboxBakePlanCancelPayload
import ch.mcfx.urs.data.local.OutboxBeerLogCreatePayload
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
import ch.mcfx.urs.data.local.OutboxKanbanBoardDeletePayload
import ch.mcfx.urs.data.local.OutboxKanbanBoardPayload
import ch.mcfx.urs.data.local.OutboxKanbanBoardUpdatePayload
import ch.mcfx.urs.data.local.OutboxKanbanCardDeletePayload
import ch.mcfx.urs.data.local.OutboxKanbanCardMovePayload
import ch.mcfx.urs.data.local.OutboxKanbanCardPayload
import ch.mcfx.urs.data.local.OutboxKanbanCardUpdatePayload
import ch.mcfx.urs.data.local.OutboxKanbanChecklistItemDeletePayload
import ch.mcfx.urs.data.local.OutboxKanbanChecklistItemPayload
import ch.mcfx.urs.data.local.OutboxKanbanChecklistItemUpdatePayload
import ch.mcfx.urs.data.local.OutboxKanbanColumnDeletePayload
import ch.mcfx.urs.data.local.OutboxKanbanColumnMovePayload
import ch.mcfx.urs.data.local.OutboxKanbanColumnPayload
import ch.mcfx.urs.data.local.OutboxKanbanColumnUpdatePayload
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
import ch.mcfx.urs.data.local.OutboxTrackerDomainCreatePayload
import ch.mcfx.urs.data.local.OutboxTrackerDomainDeletePayload
import ch.mcfx.urs.data.local.OutboxTrackerDomainUpdatePayload
import ch.mcfx.urs.data.local.OutboxTrackerEventCreatePayload
import ch.mcfx.urs.data.local.OutboxTrackerEventDeletePayload
import ch.mcfx.urs.data.local.OutboxTrackerEventUpdatePayload
import ch.mcfx.urs.data.local.OutboxTrackerTypeArchivePayload
import ch.mcfx.urs.data.local.OutboxTrackerTypeCreatePayload
import ch.mcfx.urs.data.local.OutboxTrackerTypeReactivatePayload
import ch.mcfx.urs.data.local.OutboxTrackerTypeUpdatePayload
import ch.mcfx.urs.data.local.TrackerDomainDao
import ch.mcfx.urs.data.local.TrackerEventDao
import ch.mcfx.urs.data.local.TrackerTypeDao
import ch.mcfx.urs.data.local.localTrackerDomainId
import ch.mcfx.urs.data.local.localTrackerTypeId
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
import ch.mcfx.urs.data.local.localAssetId
import ch.mcfx.urs.data.local.localInventoryId
import ch.mcfx.urs.data.local.localKanbanBoardId
import ch.mcfx.urs.data.local.localKanbanCardId
import ch.mcfx.urs.data.local.localKanbanColumnId
import ch.mcfx.urs.data.local.localListId
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.data.remote.BakePlanCreatePayload
import ch.mcfx.urs.data.remote.BeerLogPayload
import ch.mcfx.urs.data.remote.BakePlanStepCreatePayload
import ch.mcfx.urs.data.remote.BakePlanStepPatchPayload
import ch.mcfx.urs.data.remote.FillPayload
import ch.mcfx.urs.data.remote.FillUpdatePayload
import ch.mcfx.urs.data.remote.InventoryPayload
import ch.mcfx.urs.data.remote.InventoryProductCreatePayload
import ch.mcfx.urs.data.remote.AssetCommentCreatePayload
import ch.mcfx.urs.data.remote.AssetCommentUpdatePayload
import ch.mcfx.urs.data.remote.AssetComponentCreatePayload
import ch.mcfx.urs.data.remote.AssetComponentDto
import ch.mcfx.urs.data.remote.AssetComponentUpdatePayload
import ch.mcfx.urs.data.remote.AssetCreatePayload
import ch.mcfx.urs.data.remote.AssetUpdatePayload
import ch.mcfx.urs.data.remote.KanbanBoardCreatePayload
import ch.mcfx.urs.data.remote.KanbanBoardRenamePayload
import ch.mcfx.urs.data.remote.KanbanCardCreatePayload
import ch.mcfx.urs.data.remote.KanbanCardMovePayload
import ch.mcfx.urs.data.remote.KanbanCardUpdatePayload
import ch.mcfx.urs.data.remote.KanbanChecklistItemCreatePayload
import ch.mcfx.urs.data.remote.KanbanChecklistItemUpdatePayload
import ch.mcfx.urs.data.remote.KanbanColumnCreatePayload
import ch.mcfx.urs.data.remote.KanbanColumnMovePayload
import ch.mcfx.urs.data.remote.KanbanColumnRenamePayload
import ch.mcfx.urs.data.remote.ListItemPayload
import ch.mcfx.urs.data.remote.ListItemUpdatePayload
import ch.mcfx.urs.data.remote.ListPayload
import ch.mcfx.urs.data.remote.LocationHistoryPayload
import ch.mcfx.urs.data.remote.NoteCreatePayload
import ch.mcfx.urs.data.remote.NoteStatusPatchPayload
import ch.mcfx.urs.data.remote.TrackerDomainPayload
import ch.mcfx.urs.data.remote.TrackerEventPayload
import ch.mcfx.urs.data.remote.TrackerTypePayload
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
 * FIFO replay order (oldest queued mutation first) is the ordering
 * guarantee here. The only conflict resolution is last-write-wins for
 * `list`/`list_item`/`inventory_product` updates: those carry a basis
 * timestamp and the backend answers 409 (or 404 for a since-deleted row)
 * when a newer edit or a delete already won — the losing edit is then
 * discarded silently, see [replayUpdateList]/[replayUpdateListItem].
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
    private val trackerTypeDao: TrackerTypeDao,
    private val trackerEventDao: TrackerEventDao,
    private val trackerDomainDao: TrackerDomainDao,
    private val kanbanBoardDao: KanbanBoardDao,
    private val kanbanColumnDao: KanbanColumnDao,
    private val kanbanCardDao: KanbanCardDao,
    private val kanbanChecklistItemDao: KanbanChecklistItemDao,
    private val assetDao: AssetDao,
    private val assetComponentDao: AssetComponentDao,
    private val assetCommentDao: AssetCommentDao,
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

    /**
     * Wired by the DI container after [ch.mcfx.urs.data.ShoppingListRepository]
     * is built — it can't be a constructor dependency, this class is
     * constructed first. Invoked once per replay pass in which a list or
     * list-item update lost to a 409/404, so the shopping-list UI reconciles
     * to the winning state immediately. Must not synchronously re-enter
     * [syncNow] (the refresh it triggers calls it) — the container wraps it
     * in an `applicationScope.launch { ... }`.
     */
    var onListConflictResolved: (() -> Unit)? = null

    // Set on a 409/404 in replayUpdateList/replayUpdateListItem, consumed at
    // the end of replayOutbox. A plain var is safe — every access is inside
    // the syncNow() mutex.
    private var listUpdateConflictSeen = false

    /** Same wiring/rationale as [onListConflictResolved], for the Kanban board/column/card equivalent. */
    var onKanbanConflictResolved: (() -> Unit)? = null

    /**
     * Invoked once a queued beer log has actually synced to the backend
     * (not on every replay pass — only when [replayCreateBeerLog] succeeds),
     * so Home's quick-stat tile can refresh with data the backend actually
     * has, rather than racing ahead of the sync. Same late-binding/
     * applicationScope.launch wiring as [onListConflictResolved].
     */
    var onBeerLogSynced: (() -> Unit)? = null

    // Set on a 409/404 in replayUpdateKanbanBoard/Column/Card, consumed at
    // the end of replayOutbox — same shape as [listUpdateConflictSeen].
    private var kanbanUpdateConflictSeen = false

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
        if (listUpdateConflictSeen) {
            listUpdateConflictSeen = false
            // Deferred (see onListConflictResolved's doc) so the refresh runs
            // once this pass has released the mutex.
            onListConflictResolved?.invoke()
        }
        if (kanbanUpdateConflictSeen) {
            kanbanUpdateConflictSeen = false
            onKanbanConflictResolved?.invoke()
        }
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
                OutboxMutationEntity.TYPE_CREATE_TRACKER_TYPE -> replayCreateTrackerType(mutation)
                OutboxMutationEntity.TYPE_UPDATE_TRACKER_TYPE -> replayUpdateTrackerType(mutation)
                OutboxMutationEntity.TYPE_ARCHIVE_TRACKER_TYPE -> replayArchiveTrackerType(mutation)
                OutboxMutationEntity.TYPE_REACTIVATE_TRACKER_TYPE -> replayReactivateTrackerType(mutation)
                OutboxMutationEntity.TYPE_CREATE_TRACKER_EVENT -> replayCreateTrackerEvent(mutation)
                OutboxMutationEntity.TYPE_UPDATE_TRACKER_EVENT -> replayUpdateTrackerEvent(mutation)
                OutboxMutationEntity.TYPE_DELETE_TRACKER_EVENT -> replayDeleteTrackerEvent(mutation)
                OutboxMutationEntity.TYPE_CREATE_TRACKER_DOMAIN -> replayCreateTrackerDomain(mutation)
                OutboxMutationEntity.TYPE_UPDATE_TRACKER_DOMAIN -> replayUpdateTrackerDomain(mutation)
                OutboxMutationEntity.TYPE_DELETE_TRACKER_DOMAIN -> replayDeleteTrackerDomain(mutation)
                OutboxMutationEntity.TYPE_CREATE_BEER_LOG -> replayCreateBeerLog(mutation)
                OutboxMutationEntity.TYPE_CREATE_KANBAN_BOARD -> replayCreateKanbanBoard(mutation)
                OutboxMutationEntity.TYPE_UPDATE_KANBAN_BOARD -> replayUpdateKanbanBoard(mutation)
                OutboxMutationEntity.TYPE_DELETE_KANBAN_BOARD -> replayDeleteKanbanBoard(mutation)
                OutboxMutationEntity.TYPE_CREATE_KANBAN_COLUMN -> replayCreateKanbanColumn(mutation)
                OutboxMutationEntity.TYPE_UPDATE_KANBAN_COLUMN -> replayUpdateKanbanColumn(mutation)
                OutboxMutationEntity.TYPE_MOVE_KANBAN_COLUMN -> replayMoveKanbanColumn(mutation)
                OutboxMutationEntity.TYPE_DELETE_KANBAN_COLUMN -> replayDeleteKanbanColumn(mutation)
                OutboxMutationEntity.TYPE_CREATE_KANBAN_CARD -> replayCreateKanbanCard(mutation)
                OutboxMutationEntity.TYPE_UPDATE_KANBAN_CARD -> replayUpdateKanbanCard(mutation)
                OutboxMutationEntity.TYPE_MOVE_KANBAN_CARD -> replayMoveKanbanCard(mutation)
                OutboxMutationEntity.TYPE_DELETE_KANBAN_CARD -> replayDeleteKanbanCard(mutation)
                OutboxMutationEntity.TYPE_CREATE_KANBAN_CHECKLIST_ITEM -> replayCreateKanbanChecklistItem(mutation)
                OutboxMutationEntity.TYPE_UPDATE_KANBAN_CHECKLIST_ITEM -> replayUpdateKanbanChecklistItem(mutation)
                OutboxMutationEntity.TYPE_DELETE_KANBAN_CHECKLIST_ITEM -> replayDeleteKanbanChecklistItem(mutation)
                OutboxMutationEntity.TYPE_CREATE_ASSET -> replayCreateAsset(mutation)
                OutboxMutationEntity.TYPE_UPDATE_ASSET -> replayUpdateAsset(mutation)
                OutboxMutationEntity.TYPE_DELETE_ASSET -> replayDeleteAsset(mutation)
                OutboxMutationEntity.TYPE_CREATE_ASSET_COMPONENT -> replayCreateAssetComponent(mutation)
                OutboxMutationEntity.TYPE_UPDATE_ASSET_COMPONENT -> replayUpdateAssetComponent(mutation)
                OutboxMutationEntity.TYPE_DELETE_ASSET_COMPONENT -> replayDeleteAssetComponent(mutation)
                OutboxMutationEntity.TYPE_CREATE_ASSET_COMMENT -> replayCreateAssetComment(mutation)
                OutboxMutationEntity.TYPE_UPDATE_ASSET_COMMENT -> replayUpdateAssetComment(mutation)
                OutboxMutationEntity.TYPE_DELETE_ASSET_COMMENT -> replayDeleteAssetComment(mutation)
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
                sourceVehicleId = payload.sourceVehicleId,
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

        if (payload.sourceVehicleId != null) {
            // Transfer fill: no station at all, known or ad-hoc — nothing to
            // cache. Skip the ad-hoc/known-station bookkeeping below entirely
            // rather than upserting a bogus station keyed by the backend's
            // empty response.stationId.
        } else if (payload.stationId == null) {
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
                sourceVehicleId = payload.sourceVehicleId,
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
                mealAllowance = if (payload.mealAllowance) "1" else "0",
                comment = payload.comment,
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
                mealAllowance = if (payload.mealAllowance) "1" else "0",
                comment = payload.comment,
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
    // Carries mutation.createdAt as the last-write-wins basis; a 409 (newer
    // edit won) or 404 (list deleted elsewhere) means this edit lost and is
    // discarded, same as a clean success but flagged for a follow-up refresh.
    private suspend fun replayUpdateList(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxListUpdatePayload.serializer(), mutation.payloadJson)
        try {
            api.updateList(
                payload.serverId,
                ListPayload(name = payload.name, updatedAt = mutation.createdAt.toUpdatedAtBasis()),
            )
        } catch (e: HttpException) {
            if (e.code() != 409 && e.code() != 404) throw e
            listUpdateConflictSeen = true
        }
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

    // Same "no local-row lookup needed" and same last-write-wins handling as
    // replayUpdateList.
    private suspend fun replayUpdateListItem(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxListItemUpdatePayload.serializer(), mutation.payloadJson)
        try {
            api.updateListItem(
                payload.serverId,
                ListItemUpdatePayload(
                    note = payload.note.orEmpty(),
                    quantity = payload.quantity,
                    onSale = payload.onSale,
                    updatedAt = mutation.createdAt.toUpdatedAtBasis(),
                ),
            )
        } catch (e: HttpException) {
            if (e.code() != 409 && e.code() != 404) throw e
            listUpdateConflictSeen = true
        }
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

    // --- Chores tracker (GitHub issue #27) — same parent/child offline
    // ordering as list/list-item: an event's create resolves its parent
    // type's real id at replay time, retrying if the type isn't synced yet.

    private suspend fun replayCreateTrackerType(mutation: OutboxMutationEntity): Boolean {
        val localType = trackerTypeDao.getByOutboxId(mutation.id) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxTrackerTypeCreatePayload.serializer(), mutation.payloadJson)
        val resolvedDomainId = payload.domainId?.let {
            resolveTrackerDomainId(it) ?: run {
                outboxDao.markFailed(mutation.id, "parent tracker domain not yet synced")
                return false
            }
        }
        val response = api.createTrackerType(
            TrackerTypePayload(
                domainId = resolvedDomainId.orEmpty(),
                name = payload.name,
                color = payload.color,
                icon = payload.icon,
                calendar = payload.calendar.orEmpty(),
                expectedIntervalDays = payload.expectedIntervalDays?.toString().orEmpty(),
            ),
        )
        trackerTypeDao.markSynced(localType.id, response.id)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayUpdateTrackerType(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxTrackerTypeUpdatePayload.serializer(), mutation.payloadJson)
        val resolvedDomainId = payload.domainId?.let {
            resolveTrackerDomainId(it) ?: run {
                outboxDao.markFailed(mutation.id, "parent tracker domain not yet synced")
                return false
            }
        }
        api.updateTrackerType(
            payload.serverId,
            TrackerTypePayload(
                domainId = resolvedDomainId.orEmpty(),
                name = payload.name,
                color = payload.color,
                icon = payload.icon,
                calendar = payload.calendar.orEmpty(),
                expectedIntervalDays = payload.expectedIntervalDays?.toString().orEmpty(),
            ),
        )
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayArchiveTrackerType(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxTrackerTypeArchivePayload.serializer(), mutation.payloadJson)
        api.archiveTrackerType(payload.serverId)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayReactivateTrackerType(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxTrackerTypeReactivatePayload.serializer(), mutation.payloadJson)
        api.reactivateTrackerType(payload.serverId)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayCreateTrackerEvent(mutation: OutboxMutationEntity): Boolean {
        val localEvent = trackerEventDao.getByOutboxId(mutation.id) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxTrackerEventCreatePayload.serializer(), mutation.payloadJson)
        val resolvedTypeId = resolveTrackerTypeId(payload.trackerTypeId) ?: run {
            outboxDao.markFailed(mutation.id, "parent tracker type not yet synced")
            return false
        }
        val response = api.createTrackerEvent(
            TrackerEventPayload(
                trackerTypeId = resolvedTypeId,
                occurredOn = payload.occurredOn,
                occurredOnEnd = payload.occurredOnEnd.orEmpty(),
                occurredAt = payload.occurredAt.orEmpty(),
                occurredAtEnd = payload.occurredAtEnd.orEmpty(),
                note = payload.note.orEmpty(),
                source = payload.source,
            ),
        )
        trackerEventDao.markSynced(localEvent.id, response.id, resolvedTypeId)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayUpdateTrackerEvent(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxTrackerEventUpdatePayload.serializer(), mutation.payloadJson)
        val resolvedTypeId = resolveTrackerTypeId(payload.trackerTypeId) ?: run {
            outboxDao.markFailed(mutation.id, "tracker type not yet synced")
            return false
        }
        api.updateTrackerEvent(
            payload.serverId,
            TrackerEventPayload(
                trackerTypeId = resolvedTypeId,
                occurredOn = payload.occurredOn,
                occurredOnEnd = payload.occurredOnEnd.orEmpty(),
                occurredAt = payload.occurredAt.orEmpty(),
                occurredAtEnd = payload.occurredAtEnd.orEmpty(),
                note = payload.note.orEmpty(),
            ),
        )
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayDeleteTrackerEvent(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxTrackerEventDeletePayload.serializer(), mutation.payloadJson)
        api.deleteTrackerEvent(payload.serverId)
        outboxDao.delete(mutation.id)
        return true
    }

    // --- Journal domains (GitHub issue #83) ---

    private suspend fun replayCreateTrackerDomain(mutation: OutboxMutationEntity): Boolean {
        val localDomain = trackerDomainDao.getByOutboxId(mutation.id) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxTrackerDomainCreatePayload.serializer(), mutation.payloadJson)
        val response = api.createTrackerDomain(TrackerDomainPayload(name = payload.name, color = payload.color, icon = payload.icon))
        trackerDomainDao.markSynced(localDomain.id, response.id)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayUpdateTrackerDomain(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxTrackerDomainUpdatePayload.serializer(), mutation.payloadJson)
        api.updateTrackerDomain(payload.serverId, TrackerDomainPayload(name = payload.name, color = payload.color, icon = payload.icon))
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayDeleteTrackerDomain(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxTrackerDomainDeletePayload.serializer(), mutation.payloadJson)
        api.deleteTrackerDomain(payload.serverId)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayCreateBeerLog(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxBeerLogCreatePayload.serializer(), mutation.payloadJson)
        api.createBeerLog(BeerLogPayload(amountMl = payload.amountMl.toString(), date = payload.date))
        outboxDao.delete(mutation.id)
        onBeerLogSynced?.invoke()
        return true
    }

    // --- Kanban board (GitHub issue #62) — board/column/card create mirror
    // replayCreateList's shape (response echoes the assigned id directly),
    // update/move mirror replayUpdateList/replayUpdateListItem's
    // last-write-wins handling (update only — move has no basis on the
    // backend, see OutboxKanbanChecklistItemUpdatePayload's doc comment for
    // why checklist items don't need it either).

    private suspend fun replayCreateKanbanBoard(mutation: OutboxMutationEntity): Boolean {
        val localBoard = kanbanBoardDao.getByOutboxId(mutation.id) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxKanbanBoardPayload.serializer(), mutation.payloadJson)
        val response = api.createKanbanBoard(KanbanBoardCreatePayload(name = payload.name))
        kanbanBoardDao.markSynced(localBoard.id, response.id)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayUpdateKanbanBoard(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxKanbanBoardUpdatePayload.serializer(), mutation.payloadJson)
        try {
            api.renameKanbanBoard(
                payload.serverId,
                KanbanBoardRenamePayload(name = payload.name, updatedAt = mutation.createdAt.toUpdatedAtBasis()),
            )
        } catch (e: HttpException) {
            if (e.code() != 409 && e.code() != 404) throw e
            kanbanUpdateConflictSeen = true
        }
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayDeleteKanbanBoard(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxKanbanBoardDeletePayload.serializer(), mutation.payloadJson)
        api.deleteKanbanBoard(payload.serverId)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayCreateKanbanColumn(mutation: OutboxMutationEntity): Boolean {
        val localColumn = kanbanColumnDao.getByOutboxId(mutation.id) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxKanbanColumnPayload.serializer(), mutation.payloadJson)
        val resolvedBoardId = resolveKanbanBoardId(payload.boardId) ?: run {
            outboxDao.markFailed(mutation.id, "parent board not yet synced")
            return false
        }
        val response = api.createKanbanColumn(KanbanColumnCreatePayload(boardId = resolvedBoardId, name = payload.name))
        kanbanColumnDao.markSynced(localColumn.id, response.id, resolvedBoardId)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayUpdateKanbanColumn(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxKanbanColumnUpdatePayload.serializer(), mutation.payloadJson)
        try {
            api.renameKanbanColumn(
                payload.serverId,
                KanbanColumnRenamePayload(name = payload.name, updatedAt = mutation.createdAt.toUpdatedAtBasis()),
            )
        } catch (e: HttpException) {
            if (e.code() != 409 && e.code() != 404) throw e
            kanbanUpdateConflictSeen = true
        }
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayMoveKanbanColumn(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxKanbanColumnMovePayload.serializer(), mutation.payloadJson)
        val column = kanbanColumnDao.getById(payload.localColumnId) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val serverId = column.serverId ?: run {
            outboxDao.markFailed(mutation.id, "column not yet synced")
            return false
        }
        try {
            api.moveKanbanColumn(serverId, KanbanColumnMovePayload(index = payload.index))
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
        }
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayDeleteKanbanColumn(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxKanbanColumnDeletePayload.serializer(), mutation.payloadJson)
        api.deleteKanbanColumn(payload.serverId)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayCreateKanbanCard(mutation: OutboxMutationEntity): Boolean {
        val localCard = kanbanCardDao.getByOutboxId(mutation.id) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxKanbanCardPayload.serializer(), mutation.payloadJson)
        val resolvedColumnId = resolveKanbanColumnId(payload.columnId) ?: run {
            outboxDao.markFailed(mutation.id, "parent column not yet synced")
            return false
        }
        val response = api.createKanbanCard(
            KanbanCardCreatePayload(
                columnId = resolvedColumnId,
                title = payload.title,
                description = payload.description,
                dueDate = payload.dueDate.orEmpty(),
                priority = payload.priority,
                noteId = payload.linkedNoteId.orEmpty(),
                tags = payload.tags,
            ),
        )
        kanbanCardDao.markSynced(localCard.id, response.id, resolvedColumnId)
        outboxDao.delete(mutation.id)
        return true
    }

    // Identifies its target by localCardId, resolved to a serverId here at
    // replay time — same shape as replayUpdateNote.
    private suspend fun replayUpdateKanbanCard(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxKanbanCardUpdatePayload.serializer(), mutation.payloadJson)
        val card = kanbanCardDao.getById(payload.localCardId) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val serverId = card.serverId ?: run {
            outboxDao.markFailed(mutation.id, "card not yet synced")
            return false
        }
        try {
            api.updateKanbanCard(
                serverId,
                KanbanCardUpdatePayload(
                    title = payload.title,
                    description = payload.description,
                    dueDate = payload.dueDate.orEmpty(),
                    priority = payload.priority,
                    noteId = payload.linkedNoteId.orEmpty(),
                    tags = payload.tags,
                    updatedAt = mutation.createdAt.toUpdatedAtBasis(),
                ),
            )
            kanbanCardDao.clearPending(card.id)
        } catch (e: HttpException) {
            if (e.code() != 409 && e.code() != 404) throw e
            kanbanUpdateConflictSeen = true
        }
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayMoveKanbanCard(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxKanbanCardMovePayload.serializer(), mutation.payloadJson)
        val card = kanbanCardDao.getById(payload.localCardId) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val serverId = card.serverId ?: run {
            outboxDao.markFailed(mutation.id, "card not yet synced")
            return false
        }
        val resolvedColumnId = resolveKanbanColumnId(payload.targetColumnId) ?: run {
            outboxDao.markFailed(mutation.id, "target column not yet synced")
            return false
        }
        try {
            api.moveKanbanCard(serverId, KanbanCardMovePayload(columnId = resolvedColumnId, index = payload.index))
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
        }
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayDeleteKanbanCard(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxKanbanCardDeletePayload.serializer(), mutation.payloadJson)
        api.deleteKanbanCard(payload.serverId)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayCreateKanbanChecklistItem(mutation: OutboxMutationEntity): Boolean {
        val localItem = kanbanChecklistItemDao.getByOutboxId(mutation.id) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxKanbanChecklistItemPayload.serializer(), mutation.payloadJson)
        val resolvedCardId = resolveKanbanCardId(payload.cardId) ?: run {
            outboxDao.markFailed(mutation.id, "parent card not yet synced")
            return false
        }
        val response = api.createKanbanChecklistItem(KanbanChecklistItemCreatePayload(cardId = resolvedCardId, text = payload.text))
        kanbanChecklistItemDao.markSynced(localItem.id, response.id, resolvedCardId)
        outboxDao.delete(mutation.id)
        return true
    }

    // Identifies its target by localItemId, resolved to a serverId here at
    // replay time — same shape as replayUpdateNote. No last-write-wins
    // basis needed — see OutboxKanbanChecklistItemUpdatePayload's doc comment.
    private suspend fun replayUpdateKanbanChecklistItem(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxKanbanChecklistItemUpdatePayload.serializer(), mutation.payloadJson)
        val item = kanbanChecklistItemDao.getById(payload.localItemId) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val serverId = item.serverId ?: run {
            outboxDao.markFailed(mutation.id, "checklist item not yet synced")
            return false
        }
        try {
            api.updateKanbanChecklistItem(serverId, KanbanChecklistItemUpdatePayload(text = payload.text, done = payload.done))
            kanbanChecklistItemDao.clearPending(item.id)
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
        }
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayDeleteKanbanChecklistItem(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxKanbanChecklistItemDeletePayload.serializer(), mutation.payloadJson)
        api.deleteKanbanChecklistItem(payload.serverId)
        outboxDao.delete(mutation.id)
        return true
    }

    // Creates the asset together with any inline initial components in one
    // POST — see OutboxAssetPayload's doc comment. The response's components
    // array preserves submission order, so pendingComponents.zip(...) below
    // is a safe positional correlation (no client-side temp id exists to
    // match on instead).
    private suspend fun replayCreateAsset(mutation: OutboxMutationEntity): Boolean {
        val localAsset = assetDao.getByOutboxId(mutation.id) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxAssetPayload.serializer(), mutation.payloadJson)
        val oldPublicId = localAsset.publicId
        val response = api.createAsset(
            AssetCreatePayload(
                name = payload.name,
                category = payload.category,
                location = payload.location,
                tags = payload.tags,
                components = payload.components.map {
                    AssetComponentDto(description = it.description, manufacturer = it.manufacturer, price = it.price, purchaseDate = it.purchaseDate, dealer = it.dealer)
                },
            ),
        )
        assetDao.markSynced(localAsset.id, response.id)
        assetDao.updateTotalValue(localAsset.id, response.totalValue)

        val pendingComponents = assetComponentDao.getPendingInlineByAssetId(oldPublicId)
        pendingComponents.zip(response.components).forEach { (local, remote) ->
            assetComponentDao.markSynced(local.id, remote.id, response.id)
        }
        outboxDao.delete(mutation.id)
        return true
    }

    // Identifies its target by localAssetId, resolved to a serverId here at
    // replay time — same shape as replayUpdateNote. No last-write-wins basis
    // on this endpoint, same reasoning as replayUpdateVehicleService.
    private suspend fun replayUpdateAsset(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxAssetUpdatePayload.serializer(), mutation.payloadJson)
        val asset = assetDao.getById(payload.localAssetId) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val serverId = asset.serverId ?: run {
            outboxDao.markFailed(mutation.id, "asset not yet synced")
            return false
        }
        try {
            api.updateAsset(serverId, AssetUpdatePayload(name = payload.name, category = payload.category, location = payload.location, status = payload.status, tags = payload.tags))
            assetDao.clearPending(asset.id)
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
        }
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayDeleteAsset(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxAssetDeletePayload.serializer(), mutation.payloadJson)
        api.deleteAsset(payload.serverId)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayCreateAssetComponent(mutation: OutboxMutationEntity): Boolean {
        val localComponent = assetComponentDao.getByOutboxId(mutation.id) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxAssetComponentPayload.serializer(), mutation.payloadJson)
        val resolvedAssetId = resolveAssetId(payload.assetId) ?: run {
            outboxDao.markFailed(mutation.id, "parent asset not yet synced")
            return false
        }
        val response = api.createAssetComponent(
            AssetComponentCreatePayload(assetId = resolvedAssetId, description = payload.description, manufacturer = payload.manufacturer, price = payload.price, purchaseDate = payload.purchaseDate, dealer = payload.dealer),
        )
        assetComponentDao.markSynced(localComponent.id, response.id, resolvedAssetId)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayUpdateAssetComponent(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxAssetComponentUpdatePayload.serializer(), mutation.payloadJson)
        val component = assetComponentDao.getById(payload.localComponentId) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val serverId = component.serverId ?: run {
            outboxDao.markFailed(mutation.id, "component not yet synced")
            return false
        }
        try {
            api.updateAssetComponent(serverId, AssetComponentUpdatePayload(description = payload.description, manufacturer = payload.manufacturer, price = payload.price, purchaseDate = payload.purchaseDate, dealer = payload.dealer))
            assetComponentDao.clearPending(component.id)
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
        }
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayDeleteAssetComponent(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxAssetComponentDeletePayload.serializer(), mutation.payloadJson)
        api.deleteAssetComponent(payload.serverId)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayCreateAssetComment(mutation: OutboxMutationEntity): Boolean {
        val localComment = assetCommentDao.getByOutboxId(mutation.id) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val payload = json.decodeFromString(OutboxAssetCommentPayload.serializer(), mutation.payloadJson)
        val resolvedAssetId = resolveAssetId(payload.assetId) ?: run {
            outboxDao.markFailed(mutation.id, "parent asset not yet synced")
            return false
        }
        val response = api.createAssetComment(AssetCommentCreatePayload(assetId = resolvedAssetId, text = payload.text, date = payload.date))
        assetCommentDao.markSynced(localComment.id, response.id, resolvedAssetId)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayUpdateAssetComment(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxAssetCommentUpdatePayload.serializer(), mutation.payloadJson)
        val comment = assetCommentDao.getById(payload.localCommentId) ?: run {
            outboxDao.delete(mutation.id)
            return true
        }
        val serverId = comment.serverId ?: run {
            outboxDao.markFailed(mutation.id, "comment not yet synced")
            return false
        }
        try {
            api.updateAssetComment(serverId, AssetCommentUpdatePayload(text = payload.text, date = payload.date))
            assetCommentDao.clearPending(comment.id)
        } catch (e: HttpException) {
            if (e.code() != 404) throw e
        }
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun replayDeleteAssetComment(mutation: OutboxMutationEntity): Boolean {
        val payload = json.decodeFromString(OutboxAssetCommentDeletePayload.serializer(), mutation.payloadJson)
        api.deleteAssetComment(payload.serverId)
        outboxDao.delete(mutation.id)
        return true
    }

    private suspend fun resolveAssetId(value: String): String? {
        val localId = localAssetId(value) ?: return value
        return assetDao.getById(localId)?.serverId
    }

    private suspend fun resolveTrackerTypeId(value: String): String? {
        val localId = localTrackerTypeId(value) ?: return value
        return trackerTypeDao.getById(localId)?.serverId
    }

    private suspend fun resolveTrackerDomainId(value: String): String? {
        val localId = localTrackerDomainId(value) ?: return value
        return trackerDomainDao.getById(localId)?.serverId
    }

    private fun Long.toBakingDateString(): String =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(locationHistoryDateFormat)

    // Same wire format / device-local zone as toBakingDateString — the
    // backend compares this against the row's own updated_at for
    // last-write-wins on list/list-item updates.
    private fun Long.toUpdatedAtBasis(): String =
        Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(locationHistoryDateFormat)

    private suspend fun resolveListId(value: String): String? {
        val localId = localListId(value) ?: return value
        return listDao.getById(localId)?.serverId
    }

    private suspend fun resolveInventoryId(value: String): String? {
        val localId = localInventoryId(value) ?: return value
        return inventoryDao.getById(localId)?.serverId
    }

    private suspend fun resolveKanbanBoardId(value: String): String? {
        val localId = localKanbanBoardId(value) ?: return value
        return kanbanBoardDao.getById(localId)?.serverId
    }

    private suspend fun resolveKanbanColumnId(value: String): String? {
        val localId = localKanbanColumnId(value) ?: return value
        return kanbanColumnDao.getById(localId)?.serverId
    }

    private suspend fun resolveKanbanCardId(value: String): String? {
        val localId = localKanbanCardId(value) ?: return value
        return kanbanCardDao.getById(localId)?.serverId
    }
}
