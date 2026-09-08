package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// Raised from 200 (GitHub issue #60) to accommodate the activity-recognition
// poll heartbeat logged on every ~30s callback, not just on state changes.
private const val MAX_LOG_ENTRIES = 500

@Dao
interface LocationCaptureLogDao {

    @Insert
    suspend fun insert(entity: LocationCaptureLogEntity): Long

    @Query("SELECT * FROM location_capture_log ORDER BY timestampMillis DESC LIMIT $MAX_LOG_ENTRIES")
    fun observeRecent(): Flow<List<LocationCaptureLogEntity>>

    /** Keeps only the [MAX_LOG_ENTRIES] most recent rows — call after every insert. */
    @Query(
        "DELETE FROM location_capture_log WHERE id NOT IN " +
            "(SELECT id FROM location_capture_log ORDER BY timestampMillis DESC LIMIT $MAX_LOG_ENTRIES)",
    )
    suspend fun trim()
}
