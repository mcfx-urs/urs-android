package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface KanbanColumnDao {

    @Query("SELECT * FROM kanban_column")
    fun observeAll(): Flow<List<KanbanColumnEntity>>

    @Query("SELECT * FROM kanban_column WHERE boardId = :boardId")
    suspend fun getByBoardId(boardId: String): List<KanbanColumnEntity>

    @Query("SELECT * FROM kanban_column WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): KanbanColumnEntity?

    @Query("SELECT * FROM kanban_column WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): KanbanColumnEntity?

    @Query("SELECT id FROM kanban_column WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: String): Long?

    /** Local-only write — see [InventoryDao.upsert]'s doc comment. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(column: KanbanColumnEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(column: KanbanColumnEntity): Long

    /** Backend-refresh write path — see [ListDao.upsertFromServer]. */
    @Transaction
    suspend fun upsertFromServer(boardId: String, columns: List<KanbanColumnEntity>) {
        columns.forEach { column ->
            val serverId = column.serverId ?: return@forEach
            val existingLocalId = findLocalIdByServerId(serverId)
            replace(column.copy(id = existingLocalId ?: 0))
        }
        deleteSyncedAbsentFromServer(boardId, columns.mapNotNull { it.serverId })
    }

    // Reconciliation half of [upsertFromServer] — same rationale as
    // [ListItemDao.deleteSyncedAbsentFromServer], scoped per board.
    @Query(
        "DELETE FROM kanban_column WHERE boardId = :boardId AND syncStatus = 'SYNCED' " +
            "AND serverId NOT IN (:serverIds)",
    )
    suspend fun deleteSyncedAbsentFromServer(boardId: String, serverIds: List<String>)

    @Query(
        "UPDATE kanban_column SET name = :name, syncStatus = :syncStatus, outboxId = :outboxId WHERE id = :id",
    )
    suspend fun updateFields(id: Long, name: String, syncStatus: SyncStatus, outboxId: Long?)

    @Query("UPDATE kanban_column SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    // boardId is corrected here too (not just carried over from the queued
    // payload) — same reasoning as [ListItemDao.markSynced]'s listId
    // correction: a column queued while its parent board was still offline
    // holds its stand-in id until this point.
    @Query(
        "UPDATE kanban_column SET syncStatus = 'SYNCED', serverId = :serverId, boardId = :boardId, " +
            "outboxId = NULL WHERE id = :id",
    )
    suspend fun markSynced(id: Long, serverId: String, boardId: String)

    @Query("DELETE FROM kanban_column WHERE id = :id")
    suspend fun delete(id: Long)

    // Used by KanbanRepository.deleteBoard to clear a deleted board's
    // columns locally too — Room has no cross-entity cascade, unlike the
    // backend's ON DELETE CASCADE.
    @Query("DELETE FROM kanban_column WHERE boardId = :boardId")
    suspend fun deleteByBoardId(boardId: String)
}
