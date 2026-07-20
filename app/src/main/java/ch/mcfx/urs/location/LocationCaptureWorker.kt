package ch.mcfx.urs.location

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.local.LocationHistoryEntity
import ch.mcfx.urs.data.local.OutboxLocationHistoryPayload
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.local.SyncStatus
import kotlinx.serialization.encodeToString

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
        val intervalMinutes = store.intervalMinutes()
        if (intervalMinutes < LocationCaptureScheduler.PERIODIC_FLOOR_MINUTES) {
            LocationCaptureScheduler.scheduleNext(applicationContext, intervalMinutes, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }

        // A missing permission or a timed-out fix (see LocationCapture's own
        // doc comment) just leaves a gap in the track for this run — not
        // worth Result.retry()'s backoff churn, since a permanently-missing
        // permission would otherwise retry forever.
        val location = app.container.locationCapture.captureLocation() ?: return Result.success()

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
