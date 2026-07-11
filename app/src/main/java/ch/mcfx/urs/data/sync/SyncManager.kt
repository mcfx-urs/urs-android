package ch.mcfx.urs.data.sync

import ch.mcfx.urs.data.local.FillDao
import ch.mcfx.urs.data.local.FillingStationDao
import ch.mcfx.urs.data.local.FillingStationEntity
import ch.mcfx.urs.data.local.OutboxDao
import ch.mcfx.urs.data.local.OutboxFillPayload
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.remote.FillPayload
import ch.mcfx.urs.data.remote.UrsApi
import java.io.IOException
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
    private val outboxDao: OutboxDao,
    private val reachabilityChecker: ReachabilityChecker,
    private val json: Json,
) {
    private val mutex = Mutex()

    /** @return `true` if the backend was reachable and every queued mutation replayed cleanly. */
    suspend fun syncNow(): Boolean = mutex.withLock { replayOutbox() }

    private suspend fun replayOutbox(): Boolean {
        if (!reachabilityChecker.isReachable()) return false

        var allSucceeded = true
        // Continues past individual failures rather than aborting the whole
        // batch — one bad row (e.g. a since-deleted car) shouldn't block
        // every other queued fill from syncing.
        for (mutation in outboxDao.pendingOrdered()) {
            if (!replay(mutation)) allSucceeded = false
        }
        return allSucceeded
    }

    private suspend fun replay(mutation: OutboxMutationEntity): Boolean {
        outboxDao.markSyncing(mutation.id)
        return try {
            when (mutation.type) {
                OutboxMutationEntity.TYPE_CREATE_FILL -> replayCreateFill(mutation)
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
                carId = payload.carId,
                stationId = payload.stationId,
                fuelId = payload.fuelId,
                pricePerLiter = payload.pricePerLiter,
                liters = payload.liters,
                odometer = payload.odometer,
                driven = payload.driven,
                stationCounter = stationCounter,
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
            // about it).
            fillingStationDao.upsert(
                FillingStationEntity(
                    id = response.stationId,
                    name = "Ad-hoc fuel stop",
                    counter = stationCounter,
                    address = "",
                    latitude = payload.stationLatitude ?: "",
                    longitude = payload.stationLongitude ?: "",
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
}
