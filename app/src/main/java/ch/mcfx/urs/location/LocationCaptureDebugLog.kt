package ch.mcfx.urs.location

import android.content.Context
import ch.mcfx.urs.data.local.LOCATION_CAPTURE_LOG_RETENTION_MILLIS
import ch.mcfx.urs.data.local.LocationCaptureLogDao
import ch.mcfx.urs.data.local.LocationCaptureLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fire-and-forget debug trail for the adaptive-interval feature (GitHub
 * issue #60) — the About screen's "Location capture" card reads this back
 * so it's traceable why a measurement did or didn't happen. Best-effort
 * only: a lost log entry never affects the actual capture logic.
 */
class LocationCaptureDebugLog(
    private val context: Context,
    private val dao: LocationCaptureLogDao,
    private val scope: CoroutineScope,
) {
    fun log(eventType: String, detail: String) {
        insertAndTrim(LocationCaptureLogEntity(timestampMillis = System.currentTimeMillis(), eventType = eventType, detail = detail))
    }

    /**
     * Same as [log], plus the active capture mode, optional timing, and a
     * battery/system-state snapshot (GitHub issue #86) — used for actual GPS
     * capture attempts and [LocationCaptureWorker] runs, so each entry is
     * self-contained for export/analysis instead of requiring the reader to
     * cross-reference the nearest earlier plain log entry.
     */
    fun logCapture(
        eventType: String,
        detail: String,
        mode: String? = null,
        startMillis: Long? = null,
        endMillis: Long? = null,
        scheduledForMillis: Long? = null,
    ) {
        val snapshot = BatterySnapshot.capture(context)
        insertAndTrim(
            LocationCaptureLogEntity(
                timestampMillis = System.currentTimeMillis(),
                eventType = eventType,
                detail = detail,
                mode = mode,
                startMillis = startMillis,
                endMillis = endMillis,
                scheduledForMillis = scheduledForMillis,
                batteryPercent = snapshot.batteryPercent,
                isCharging = snapshot.isCharging,
                isPowerSaveMode = snapshot.isPowerSaveMode,
                isDeviceIdleMode = snapshot.isDeviceIdleMode,
            ),
        )
    }

    private fun insertAndTrim(entity: LocationCaptureLogEntity) {
        scope.launch(Dispatchers.IO) {
            dao.insert(entity)
            dao.trim(System.currentTimeMillis() - LOCATION_CAPTURE_LOG_RETENTION_MILLIS)
        }
    }
}
