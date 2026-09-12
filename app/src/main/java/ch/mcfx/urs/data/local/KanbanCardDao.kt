package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface KanbanCardDao {

    @Query("SELECT * FROM kanban_card")
    fun observeAll(): Flow<List<KanbanCardEntity>>

    @Query("SELECT * FROM kanban_card WHERE columnId = :columnId")
    suspend fun getByColumnId(columnId: String): List<KanbanCardEntity>

    @Query("SELECT * FROM kanban_card WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): KanbanCardEntity?

    @Query("SELECT * FROM kanban_card WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): KanbanCardEntity?

    @Query("SELECT id FROM kanban_card WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: String): Long?

    /** Local-only write — see [InventoryDao.upsert]'s doc comment. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: KanbanCardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(card: KanbanCardEntity): Long

    /**
     * Backend-refresh write path — see [ListDao.upsertFromServer]. Returns
     * the resolved local id so [ch.mcfx.urs.data.KanbanRepository
     * .refreshBoard] can reconcile that card's tags/checklist against it.
     */
    @Transaction
    suspend fun upsertFromServer(columnId: String, cards: List<KanbanCardEntity>): List<Long> {
        val resolvedIds = cards.mapNotNull { card ->
            val serverId = card.serverId ?: return@mapNotNull null
            val existingLocalId = findLocalIdByServerId(serverId)
            replace(card.copy(id = existingLocalId ?: 0))
        }
        deleteSyncedAbsentFromServer(columnId, cards.mapNotNull { it.serverId })
        return resolvedIds
    }

    // Reconciliation half of [upsertFromServer] — same rationale as
    // [ListItemDao.deleteSyncedAbsentFromServer], scoped per column.
    @Query(
        "DELETE FROM kanban_card WHERE columnId = :columnId AND syncStatus = 'SYNCED' " +
            "AND serverId NOT IN (:serverIds)",
    )
    suspend fun deleteSyncedAbsentFromServer(columnId: String, serverIds: List<String>)

    @Query(
        "UPDATE kanban_card SET title = :title, description = :description, dueDate = :dueDate, " +
            "priority = :priority, linkedNoteId = :linkedNoteId, syncStatus = :syncStatus WHERE id = :id",
    )
    suspend fun updateFields(
        id: Long,
        title: String,
        description: String,
        dueDate: String?,
        priority: String,
        linkedNoteId: String?,
        syncStatus: SyncStatus,
    )

    @Query("UPDATE kanban_card SET columnId = :columnId, position = :position, syncStatus = :syncStatus WHERE id = :id")
    suspend fun updatePosition(id: Long, columnId: String, position: Int, syncStatus: SyncStatus)

    @Query("UPDATE kanban_card SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun clearPending(id: Long)

    // columnId is corrected here too — same reasoning as [ListItemDao
    // .markSynced]'s listId correction.
    @Query(
        "UPDATE kanban_card SET syncStatus = 'SYNCED', serverId = :serverId, columnId = :columnId, " +
            "outboxId = NULL WHERE id = :id",
    )
    suspend fun markSynced(id: Long, serverId: String, columnId: String)

    @Query("DELETE FROM kanban_card WHERE id = :id")
    suspend fun delete(id: Long)

    // Used by KanbanRepository.deleteColumn (and, per affected column, by
    // deleteBoard) to clear cards locally too — Room has no cross-entity
    // cascade, unlike the backend's ON DELETE CASCADE.
    @Query("DELETE FROM kanban_card WHERE columnId = :columnId")
    suspend fun deleteByColumnId(columnId: String)
}
