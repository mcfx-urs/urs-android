package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One debug-trail entry for Location History's adaptive-interval feature
 * (GitHub issue #60) — a geofence transition, an activity-state change, an
 * effective-interval switch, or a skipped/fired capture. Read back by the
 * About screen's "Location capture" card so it's traceable why a
 * measurement did or didn't happen; see [LocationCaptureLogDao] for the
 * retention window.
 *
 * The fields below (GitHub issue #86) are only populated for capture-attempt
 * and worker-run entries — logged via [ch.mcfx.urs.location.LocationCaptureDebugLog.logCapture]
 * rather than its plain `log`, so most non-capture entries (geofence/activity
 * bookkeeping) leave them all null.
 * - [mode] is the active capture mode (e.g. "moving,geofence-dense") at the
 *   time of the entry, so it doesn't need to be looked up from the nearest
 *   earlier `INTERVAL` entry.
 * - [startMillis]/[endMillis] bracket the GPS fix attempt itself (acquisition
 *   latency is itself a battery-cost signal); [scheduledForMillis] is when
 *   the triggering [ch.mcfx.urs.location.LocationCaptureWorker] run was
 *   expected to fire, letting WorkManager batching/deferral show up in the
 *   data instead of only in a code comment.
 * - [batteryPercent]/[isCharging]/[isPowerSaveMode]/[isDeviceIdleMode] are a
 *   snapshot of the device's power state at the moment of the attempt.
 */
@Entity(tableName = "location_capture_log", indices = [Index("timestampMillis")])
data class LocationCaptureLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val eventType: String,
    val detail: String,
    val mode: String? = null,
    val startMillis: Long? = null,
    val endMillis: Long? = null,
    val scheduledForMillis: Long? = null,
    val batteryPercent: Int? = null,
    val isCharging: Boolean? = null,
    val isPowerSaveMode: Boolean? = null,
    val isDeviceIdleMode: Boolean? = null,
)
