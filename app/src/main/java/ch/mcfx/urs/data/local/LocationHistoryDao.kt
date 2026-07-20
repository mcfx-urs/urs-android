package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationHistoryDao {

    @Insert
    suspend fun insert(entity: LocationHistoryEntity)

    @Query("SELECT * FROM location_history WHERE capturedAt >= :sinceMillis ORDER BY capturedAt ASC")
    fun observeSince(sinceMillis: Long): Flow<List<LocationHistoryEntity>>
}
