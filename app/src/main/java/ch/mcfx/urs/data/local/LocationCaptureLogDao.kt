package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Switched from a fixed 500-row cap to a 7-day time-based retention window
// (GitHub issue #86) — the previous cap could fill within a single day of
// active movement at a 1-minute interval, evicting data before it could be
// exported.
const val LOCATION_CAPTURE_LOG_RETENTION_MILLIS = 7L * 24 * 60 * 60 * 1000

@Dao
interface LocationCaptureLogDao {

    @Insert
    suspend fun insert(entity: LocationCaptureLogEntity): Long

    @Query("SELECT * FROM location_capture_log ORDER BY timestampMillis DESC")
    fun observeRecent(): Flow<List<LocationCaptureLogEntity>>

    /** One-shot read of the full retained log, oldest first — for export (GitHub issue #86). */
    @Query("SELECT * FROM location_capture_log ORDER BY timestampMillis ASC")
    suspend fun getAllForExport(): List<LocationCaptureLogEntity>

    /** Deletes rows older than [LOCATION_CAPTURE_LOG_RETENTION_MILLIS] — call after every insert. */
    @Query("DELETE FROM location_capture_log WHERE timestampMillis < :cutoffMillis")
    suspend fun trim(cutoffMillis: Long)
}
