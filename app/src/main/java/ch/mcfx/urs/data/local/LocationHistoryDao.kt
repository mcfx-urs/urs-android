package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationHistoryDao {

    @Insert
    suspend fun insert(entity: LocationHistoryEntity): Long

    @Query("SELECT * FROM location_history WHERE capturedAt >= :sinceMillis ORDER BY capturedAt ASC")
    fun observeSince(sinceMillis: Long): Flow<List<LocationHistoryEntity>>

    @Query("SELECT * FROM location_history WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): LocationHistoryEntity?

    @Query("UPDATE location_history SET syncStatus = 'SYNCED', serverId = :serverId, outboxId = NULL WHERE id = :id")
    suspend fun markSynced(id: Long, serverId: Long)
}
