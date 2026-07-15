package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkTimeDao {

    @Transaction
    @Query("SELECT * FROM work_time_entry ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<WorkTimeEntryWithBreaks>>

    @Query("SELECT * FROM work_time_entry WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): WorkTimeEntryEntity?

    @Insert
    suspend fun insertEntry(entry: WorkTimeEntryEntity): Long

    @Insert
    suspend fun insertBreaks(breaks: List<WorkTimeBreakEntity>)

    @Query("DELETE FROM work_time_break WHERE entryId = :entryId")
    suspend fun deleteBreaks(entryId: Long)

    @Transaction
    suspend fun replaceBreaks(entryId: Long, breaks: List<WorkTimeBreakEntity>) {
        deleteBreaks(entryId)
        insertBreaks(breaks)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceEntry(entry: WorkTimeEntryEntity): Long

    @Query("SELECT id FROM work_time_entry WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: Long): Long?

    /**
     * Backend-refresh write path — same reconciliation shape as
     * [FillDao.upsertFromServer]: keyed by [WorkTimeEntryEntity.serverId],
     * never creates a second local row for the same server entry, and
     * replaces the local break set wholesale rather than diffing it.
     */
    @Transaction
    suspend fun upsertFromServer(entry: WorkTimeEntryEntity, breaks: List<WorkTimeBreakEntity>) {
        val serverId = entry.serverId ?: return
        val existingLocalId = findLocalIdByServerId(serverId)
        // With an explicit id, REPLACE reuses it; with 0 it autogenerates —
        // either way the returned rowid is the row's actual local id.
        val localId = replaceEntry(entry.copy(id = existingLocalId ?: 0))
        replaceBreaks(localId, breaks.map { it.copy(entryId = localId) })
    }

    @Query(
        "UPDATE work_time_entry SET syncStatus = 'SYNCED', serverId = :serverId, outboxId = NULL " +
            "WHERE id = :id",
    )
    suspend fun markSynced(id: Long, serverId: Long)

    @Query("UPDATE work_time_entry SET syncStatus = 'FAILED' WHERE id = :id")
    suspend fun markFailed(id: Long)
}
