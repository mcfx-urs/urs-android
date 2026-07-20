package ch.mcfx.urs.location

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.local.LocationHistoryEntity

/**
 * Periodic background GPS fix for the life map feature (Phase 1,
 * local-only) — deliberately carries no [androidx.work.Constraints], unlike
 * [ch.mcfx.urs.data.sync.SyncWorker], since capture must keep working
 * offline. Reuses the existing single-shot [LocationCapture] helper for the
 * actual fix rather than reimplementing it.
 */
class LocationCaptureWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as UrsApplication
        if (!app.container.locationHistorySettingsStore.isEnabled()) return Result.success()

        // A missing permission or a timed-out fix (see LocationCapture's own
        // doc comment) just leaves a gap in the track for this run — not
        // worth Result.retry()'s backoff churn, since a permanently-missing
        // permission would otherwise retry forever.
        val location = app.container.locationCapture.captureLocation() ?: return Result.success()

        app.container.database.locationHistoryDao().insert(
            LocationHistoryEntity(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                capturedAt = System.currentTimeMillis(),
            ),
        )
        return Result.success()
    }
}
