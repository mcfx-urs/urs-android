package ch.mcfx.urs.location

import android.content.Context
import android.location.Location
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.local.LocationHistoryEntity
import ch.mcfx.urs.data.local.OutboxLocationHistoryPayload
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.local.SyncStatus
import kotlinx.serialization.encodeToString

/** A fix this imprecise (typically poor sky visibility/indoors) is more likely noise than a real position. */
private const val MAX_ACCURACY_METERS = 100f

/**
 * Periodic background GPS fix for the life map feature — deliberately
 * carries no [androidx.work.Constraints], unlike
 * [ch.mcfx.urs.data.sync.SyncWorker], since capture must keep working
 * offline. Reuses the existing single-shot [LocationCapture] helper for the
 * actual fix rather than reimplementing it. Phase 1 wrote the
 * [LocationHistoryEntity] straight to Room, local-only; Phase 3 added the
 * outbox row alongside it (same offline-first, fire-and-forget shape as
 * `FuelRepository.createFill` — see that function's doc comment), so a
 * background sync eventually replays it to the backend.
 */
class LocationCaptureWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as UrsApplication
        val store = app.container.locationHistorySettingsStore
        if (!store.isEnabled()) return Result.success()

        // Below WorkManager's 15-minute periodic-work floor, this worker
        // drives its own repeat schedule — arm the next run unconditionally
        // (even if the capture below fails) so a single missed/timed-out fix
        // doesn't break the chain. Must use APPEND_OR_REPLACE, not REPLACE:
        // this call runs under the same WORK_NAME as the run currently in
        // progress (this one), and REPLACE cancels whatever currently holds
        // that name — including this still-running invocation — the moment
        // WorkManager processes the enqueue. That raced against the GPS fix
        // below and always lost on a real device (a fix takes seconds; the
        // cancellation lands in milliseconds), silently killing every
        // capture. APPEND_OR_REPLACE instead queues the next run to start
        // once this one finishes, without touching it.
        //
        // Precision mode skips this entirely: LocationCaptureAlarmReceiver
        // already re-arms the next exact alarm itself, and this worker only
        // runs there as the alarm's immediate zero-delay payload — self-chaining
        // here too would double-schedule the next run.
        val intervalMinutes = store.intervalMinutes()
        if (!store.isPrecisionModeEnabled() && intervalMinutes < LocationCaptureScheduler.PERIODIC_FLOOR_MINUTES) {
            LocationCaptureScheduler.scheduleNext(applicationContext, intervalMinutes, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }

        // A missing permission or a timed-out fix (see LocationCapture's own
        // doc comment) just leaves a gap in the track for this run — not
        // worth Result.retry()'s backoff churn, since a permanently-missing
        // permission would otherwise retry forever.
        val location = app.container.locationCapture.captureLocation() ?: return Result.success()

        // Drop unreliable fixes outright, before they can ever look like a
        // spurious jump in the track.
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY_METERS) return Result.success()

        // Stationary dedup: skip storing (and syncing) a fix that's
        // essentially the same spot as the last one — the common case for
        // long overnight/at-work/on-vacation stretches with periodic
        // capture. Only compares against the single last stored point, not
        // a longer history, so movement below the threshold sustained
        // across many consecutive captures would never register — an
        // accepted trade-off for this coarse life-map use case, not a
        // precise-tracking one.
        val stationaryThresholdMeters = store.stationaryThresholdMeters()
        if (stationaryThresholdMeters > 0) {
            val lastPoint = app.container.database.locationHistoryDao().getLatest()
            if (lastPoint != null) {
                val distanceMeters = FloatArray(1)
                Location.distanceBetween(lastPoint.latitude, lastPoint.longitude, location.latitude, location.longitude, distanceMeters)
                if (distanceMeters[0] < stationaryThresholdMeters) return Result.success()
            }
        }

        val payload = OutboxLocationHistoryPayload(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            capturedAt = System.currentTimeMillis(),
        )

        val outboxId = app.container.database.outboxDao().insert(
            OutboxMutationEntity(
                type = OutboxMutationEntity.TYPE_CREATE_LOCATION_HISTORY,
                payloadJson = app.container.json.encodeToString(payload),
                createdAt = System.currentTimeMillis(),
            ),
        )

        app.container.database.locationHistoryDao().insert(
            LocationHistoryEntity(
                outboxId = outboxId,
                latitude = payload.latitude,
                longitude = payload.longitude,
                accuracyMeters = payload.accuracyMeters,
                capturedAt = payload.capturedAt,
                syncStatus = SyncStatus.PENDING,
            ),
        )
        return Result.success()
    }
}
