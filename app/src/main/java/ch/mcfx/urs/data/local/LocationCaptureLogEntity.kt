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
 * retention cap.
 */
@Entity(tableName = "location_capture_log", indices = [Index("timestampMillis")])
data class LocationCaptureLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val eventType: String,
    val detail: String,
)
