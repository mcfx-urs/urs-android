package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface KanbanChecklistItemDao {

    @Query("SELECT * FROM kanban_checklist_item")
    fun observeAll(): Flow<List<KanbanChecklistItemEntity>>

    @Query("SELECT * FROM kanban_checklist_item WHERE cardId = :cardId ORDER BY position ASC")
    suspend fun getByCardId(cardId: String): List<KanbanChecklistItemEntity>

    @Query("SELECT * FROM kanban_checklist_item WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): KanbanChecklistItemEntity?

    @Query("SELECT * FROM kanban_checklist_item WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): KanbanChecklistItemEntity?

    @Query("SELECT id FROM kanban_checklist_item WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: String): Long?

    /** Local-only write — see [InventoryDao.upsert]'s doc comment. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: KanbanChecklistItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(item: KanbanChecklistItemEntity): Long

    /** Backend-refresh write path — see [ListDao.upsertFromServer]. */
    @Transaction
    suspend fun upsertFromServer(cardId: String, items: List<KanbanChecklistItemEntity>) {
        items.forEach { item ->
            val serverId = item.serverId ?: return@forEach
            val existingLocalId = findLocalIdByServerId(serverId)
            replace(item.copy(id = existingLocalId ?: 0))
        }
        deleteSyncedAbsentFromServer(cardId, items.mapNotNull { it.serverId })
    }

    // Reconciliation half of [upsertFromServer] — same rationale as
    // [ListItemDao.deleteSyncedAbsentFromServer], scoped per card.
    @Query(
        "DELETE FROM kanban_checklist_item WHERE cardId = :cardId AND syncStatus = 'SYNCED' " +
            "AND serverId NOT IN (:serverIds)",
    )
    suspend fun deleteSyncedAbsentFromServer(cardId: String, serverIds: List<String>)

    @Query("UPDATE kanban_checklist_item SET text = :text, done = :done, syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateFields(id: Long, text: String, done: Boolean, syncStatus: SyncStatus)

    @Query("UPDATE kanban_checklist_item SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun clearPending(id: Long)

    // cardId is corrected here too — same reasoning as [ListItemDao
    // .markSynced]'s listId correction.
    @Query(
        "UPDATE kanban_checklist_item SET syncStatus = 'SYNCED', serverId = :serverId, cardId = :cardId, " +
            "outboxId = NULL WHERE id = :id",
    )
    suspend fun markSynced(id: Long, serverId: String, cardId: String)

    @Query("DELETE FROM kanban_checklist_item WHERE id = :id")
    suspend fun delete(id: Long)

    // Used by KanbanRepository.deleteCard to clear a deleted card's
    // checklist locally too — Room has no cross-entity cascade.
    @Query("DELETE FROM kanban_checklist_item WHERE cardId = :cardId")
    suspend fun deleteByCardId(cardId: String)
}
