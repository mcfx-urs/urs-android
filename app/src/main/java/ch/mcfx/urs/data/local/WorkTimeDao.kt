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

    @Query("SELECT * FROM work_time_entry WHERE id = :id")
    suspend fun getById(id: Long): WorkTimeEntryEntity?

    @Transaction
    @Query("SELECT * FROM work_time_entry WHERE id = :id")
    suspend fun getWithBreaksById(id: Long): WorkTimeEntryWithBreaks?

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

    @Query(
        "UPDATE work_time_entry SET date = :date, workStart = :workStart, workEnd = :workEnd, " +
            "targetDailyHours = :targetDailyHours, syncStatus = :syncStatus, outboxId = :outboxId " +
            "WHERE id = :id",
    )
    suspend fun updateFields(
        id: Long,
        date: String,
        workStart: String,
        workEnd: String,
        targetDailyHours: String,
        syncStatus: SyncStatus,
        outboxId: Long?,
    )

    @Query("DELETE FROM work_time_entry WHERE id = :id")
    suspend fun deleteEntryRow(id: Long)

    // work_time_break rows have no Room-level foreign key/cascade (unlike
    // the backend's ON DELETE CASCADE) — deleted explicitly here.
    @Transaction
    suspend fun deleteEntry(id: Long) {
        deleteBreaks(id)
        deleteEntryRow(id)
    }
}
