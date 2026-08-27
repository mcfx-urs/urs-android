package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ListDao {

    @Query("SELECT * FROM list")
    fun observeAll(): Flow<List<ListEntity>>

    @Query("SELECT * FROM list WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ListEntity?

    @Query("SELECT * FROM list WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): ListEntity?

    @Query("SELECT id FROM list WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: String): Long?

    /** Local-only write — see [InventoryDao.upsert]'s doc comment. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(list: ListEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(list: ListEntity): Long

    /** Backend-refresh write path — see [InventoryDao.upsertFromServer]. */
    @Transaction
    suspend fun upsertFromServer(lists: List<ListEntity>) {
        lists.forEach { list ->
            val serverId = list.serverId ?: return@forEach
            val existingLocalId = findLocalIdByServerId(serverId)
            replace(list.copy(id = existingLocalId ?: 0))
        }
        deleteSyncedAbsentFromServer(lists.mapNotNull { it.serverId })
    }

    // Reconciliation half of [upsertFromServer] — same rationale as
    // [ListItemDao.deleteSyncedAbsentFromServer], scoped globally since a
    // list is the top-level entity with no parent to scope by: a list
    // deleted (or unshared) on another device stops reappearing on the
    // next pull instead of sitting there permanently with no outbox link.
    @Query("DELETE FROM list WHERE syncStatus = 'SYNCED' AND serverId NOT IN (:serverIds)")
    suspend fun deleteSyncedAbsentFromServer(serverIds: List<String>)

    @Query(
        "UPDATE list SET name = :name, syncStatus = :syncStatus, outboxId = :outboxId WHERE id = :id",
    )
    suspend fun updateFields(id: Long, name: String, syncStatus: SyncStatus, outboxId: Long?)

    @Query("UPDATE list SET syncStatus = 'SYNCED', serverId = :serverId, outboxId = NULL WHERE id = :id")
    suspend fun markSynced(id: Long, serverId: String)

    @Query("DELETE FROM list WHERE id = :id")
    suspend fun delete(id: Long)
}
