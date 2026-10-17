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

const val RECENT_LOG_DISPLAY_LIMIT = 500

@Dao
interface LocationCaptureLogDao {

    @Insert
    suspend fun insert(entity: LocationCaptureLogEntity): Long

    @Query("SELECT * FROM location_capture_log ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = RECENT_LOG_DISPLAY_LIMIT): Flow<List<LocationCaptureLogEntity>>

    /** Re-emits on every write without loading any rows — for callers that only need a change signal. */
    @Query("SELECT MAX(id) FROM location_capture_log")
    fun observeChanges(): Flow<Long?>

    /** One page of the retained log by ascending id — for export (GitHub issue #86), read in chunks. */
    @Query("SELECT * FROM location_capture_log WHERE id > :afterId ORDER BY id ASC LIMIT :limit")
    suspend fun getExportPage(afterId: Long, limit: Int): List<LocationCaptureLogEntity>

    /** Deletes rows older than [LOCATION_CAPTURE_LOG_RETENTION_MILLIS] — call after every insert. */
    @Query("DELETE FROM location_capture_log WHERE timestampMillis < :cutoffMillis")
    suspend fun trim(cutoffMillis: Long)
}
