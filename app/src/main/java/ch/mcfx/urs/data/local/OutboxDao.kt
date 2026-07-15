package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {

    @Query("SELECT * FROM outbox_mutation ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<OutboxMutationEntity>>

    // FIFO replay order, oldest first. SYNCING rows are excluded so a
    // re-entrant call (shouldn't happen under SyncManager's own mutex, but
    // kept as a defensive filter) never double-picks an in-flight row.
    @Query("SELECT * FROM outbox_mutation WHERE status != 'SYNCING' ORDER BY createdAt ASC")
    suspend fun pendingOrdered(): List<OutboxMutationEntity>

    @Insert
    suspend fun insert(mutation: OutboxMutationEntity): Long

    // Rewrites a still-pending mutation's payload in place — used when a
    // not-yet-synced entry is edited again before its original mutation has
    // replayed, so the edit doesn't need its own separate round-trip.
    @Query("UPDATE outbox_mutation SET payloadJson = :payloadJson WHERE id = :id")
    suspend fun updatePayload(id: Long, payloadJson: String)

    @Query("UPDATE outbox_mutation SET status = 'SYNCING' WHERE id = :id")
    suspend fun markSyncing(id: Long)

    @Query(
        "UPDATE outbox_mutation SET status = 'FAILED', retryCount = retryCount + 1, lastError = :error " +
            "WHERE id = :id",
    )
    suspend fun markFailed(id: Long, error: String?)

    @Query("DELETE FROM outbox_mutation WHERE id = :id")
    suspend fun delete(id: Long)
}
