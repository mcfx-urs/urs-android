package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    // See InventoryEntity's doc comment on userId scoping — same
    // reasoning applies here, with an even stricter privacy bar.
    @Query("SELECT * FROM note WHERE userId = :userId AND status = :status ORDER BY id DESC")
    fun observeByStatus(userId: String, status: String): Flow<List<NoteEntity>>

    /** List/detail UI reads through this, not [observeByStatus] directly — needs each note's tags too. */
    @Transaction
    @Query("SELECT * FROM note WHERE userId = :userId AND status = :status ORDER BY id DESC")
    fun observeWithTagsByStatus(userId: String, status: String): Flow<List<NoteWithTags>>

    @Transaction
    @Query("SELECT * FROM note WHERE id = :id LIMIT 1")
    fun observeWithTagsById(id: Long): Flow<NoteWithTags?>

    @Query("SELECT * FROM note WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM note WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): NoteEntity?

    @Query("SELECT id FROM note WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: String): Long?

    /** One-shot lookup for boot-time reminder re-arming — see `NoteRepository.rearmPendingReminders`. */
    @Query("SELECT * FROM note WHERE userId = :userId AND status = 'active' AND reminderAtMillis IS NOT NULL")
    suspend fun getActiveWithReminder(userId: String): List<NoteEntity>

    /** Local-only write — see [InventoryDao.upsert]'s doc comment. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(note: NoteEntity): Long

    /**
     * Backend-refresh write path — see [ListDao.upsertFromServer]. Returns
     * the resolved local id so [ch.mcfx.urs.data.NoteRepository.refreshFromBackend]
     * can reconcile that note's tags against it (tags live in the separate
     * [NoteTagDao], so that part can't happen inside this single-Dao
     * [Transaction] the way [VehicleServiceDao.upsertFromServer] does it).
     */
    @Transaction
    suspend fun upsertFromServer(note: NoteEntity): Long {
        val serverId = note.serverId ?: return -1
        val existingLocalId = findLocalIdByServerId(serverId)
        return replace(note.copy(id = existingLocalId ?: 0))
    }

    @Query("UPDATE note SET syncStatus = 'SYNCED', serverId = :serverId, outboxId = NULL WHERE id = :id")
    suspend fun markSynced(id: Long, serverId: String)

    @Query(
        "UPDATE note SET title = :title, content = :content, reminderAtMillis = :reminderAtMillis, syncStatus = :syncStatus WHERE id = :id",
    )
    suspend fun updateFields(id: Long, title: String, content: String, reminderAtMillis: Long?, syncStatus: SyncStatus)

    @Query("UPDATE note SET status = :status, completedAtMillis = :completedAtMillis, syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, completedAtMillis: Long?, syncStatus: SyncStatus)

    @Query("UPDATE note SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun clearPending(id: Long)

    @Query("DELETE FROM note WHERE id = :id")
    suspend fun delete(id: Long)
}
