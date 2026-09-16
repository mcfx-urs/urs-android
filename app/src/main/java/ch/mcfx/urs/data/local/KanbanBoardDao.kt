package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface KanbanBoardDao {

    @Query("SELECT * FROM kanban_board")
    fun observeAll(): Flow<List<KanbanBoardEntity>>

    @Query("SELECT * FROM kanban_board WHERE isFavorite = 1 ORDER BY name ASC")
    fun observeFavorites(): Flow<List<KanbanBoardEntity>>

    @Query("UPDATE kanban_board SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("SELECT * FROM kanban_board WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<KanbanBoardEntity?>

    @Query("SELECT * FROM kanban_board WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): KanbanBoardEntity?

    @Query("SELECT * FROM kanban_board WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): KanbanBoardEntity?

    @Query("SELECT id FROM kanban_board WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: String): Long?

    /** Local-only write — see [InventoryDao.upsert]'s doc comment. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(board: KanbanBoardEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(board: KanbanBoardEntity): Long

    /** Backend-refresh write path — see [InventoryDao.upsertFromServer]. */
    @Transaction
    suspend fun upsertFromServer(boards: List<KanbanBoardEntity>) {
        boards.forEach { upsertOne(it) }
        deleteSyncedAbsentFromServer(boards.mapNotNull { it.serverId })
    }

    /**
     * Single-board write path, deliberately without [upsertFromServer]'s
     * "delete every synced board absent from this list" reconciliation —
     * used by `KanbanRepository.refreshBoard`, which only ever refreshes
     * *one* board's own row at a time. Calling [upsertFromServer] with a
     * singleton list there would incorrectly reconcile away every other
     * already-synced board, since that method treats its input as the
     * complete server snapshot. Also carries the existing row's
     * [KanbanBoardEntity.isFavorite]/[KanbanBoardEntity.iconId] forward,
     * since those are local-only UI preferences never part of the server
     * payload — same rationale as [ListDao.upsertFromServer].
     */
    @Transaction
    suspend fun upsertOne(board: KanbanBoardEntity) {
        val serverId = board.serverId ?: return
        val existingLocalId = findLocalIdByServerId(serverId)
        val existing = existingLocalId?.let { getById(it) }
        replace(
            board.copy(
                id = existingLocalId ?: 0,
                isFavorite = existing?.isFavorite ?: false,
                iconId = existing?.iconId,
            ),
        )
    }

    // Reconciliation half of [upsertFromServer], scoped globally since a
    // board is the top-level entity — same rationale as [ListDao
    // .deleteSyncedAbsentFromServer].
    @Query("DELETE FROM kanban_board WHERE syncStatus = 'SYNCED' AND serverId NOT IN (:serverIds)")
    suspend fun deleteSyncedAbsentFromServer(serverIds: List<String>)

    @Query(
        "UPDATE kanban_board SET name = :name, syncStatus = :syncStatus, outboxId = :outboxId WHERE id = :id",
    )
    suspend fun updateFields(id: Long, name: String, syncStatus: SyncStatus, outboxId: Long?)

    @Query("UPDATE kanban_board SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun clearPending(id: Long)

    @Query("UPDATE kanban_board SET syncStatus = 'SYNCED', serverId = :serverId, outboxId = NULL WHERE id = :id")
    suspend fun markSynced(id: Long, serverId: String)

    @Query("DELETE FROM kanban_board WHERE id = :id")
    suspend fun delete(id: Long)
}
