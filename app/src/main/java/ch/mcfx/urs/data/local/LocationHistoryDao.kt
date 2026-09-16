package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationHistoryDao {

    @Insert
    suspend fun insert(entity: LocationHistoryEntity): Long

    @Query("SELECT * FROM location_history WHERE capturedAt >= :sinceMillis ORDER BY capturedAt ASC")
    fun observeSince(sinceMillis: Long): Flow<List<LocationHistoryEntity>>

    /** Explicit from/to window (GitHub issue #76) — unlike [observeSince], bounded on both ends, not just "until now". */
    @Query("SELECT * FROM location_history WHERE capturedAt >= :sinceMillis AND capturedAt <= :untilMillis ORDER BY capturedAt ASC")
    fun observeBetween(sinceMillis: Long, untilMillis: Long): Flow<List<LocationHistoryEntity>>

    @Query("SELECT * FROM location_history ORDER BY capturedAt DESC LIMIT 1")
    suspend fun getLatest(): LocationHistoryEntity?

    /** Newest first — used by the geofence-adaptive settle-check (GitHub issue #60). */
    @Query("SELECT * FROM location_history ORDER BY capturedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<LocationHistoryEntity>

    @Query("SELECT * FROM location_history WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): LocationHistoryEntity?

    @Query("UPDATE location_history SET syncStatus = 'SYNCED', serverId = :serverId, outboxId = NULL WHERE id = :id")
    suspend fun markSynced(id: Long, serverId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(entity: LocationHistoryEntity): Long

    @Query("SELECT id FROM location_history WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: Long): Long?

    @Query("SELECT serverId FROM location_history WHERE syncStatus = 'SYNCED' AND serverId IS NOT NULL")
    suspend fun syncedServerIds(): List<Long>

    @Query("DELETE FROM location_history WHERE syncStatus = 'SYNCED' AND serverId = :serverId")
    suspend fun deleteSyncedByServerId(serverId: Long)

    /**
     * Backend-refresh write path: replaces the local SYNCED set
     * with exactly what the backend returned, deleting a local SYNCED row
     * whose serverId is no longer present server-side (e.g. pruned directly
     * in the DB) instead of just leaving it stale. PENDING/FAILED rows are
     * never touched here — they exist solely via the outbox replay path
     * (SyncManager), and a capture not yet uploaded must survive this pass
     * untouched.
     */
    @Transaction
    suspend fun reconcileFromServer(serverPoints: List<LocationHistoryEntity>) {
        val serverIds = serverPoints.mapNotNull { it.serverId }.toSet()
        syncedServerIds().filterNot { it in serverIds }.forEach { deleteSyncedByServerId(it) }
        serverPoints.forEach { point ->
            val serverId = point.serverId ?: return@forEach
            val existingLocalId = findLocalIdByServerId(serverId)
            replace(point.copy(id = existingLocalId ?: 0))
        }
    }
}
