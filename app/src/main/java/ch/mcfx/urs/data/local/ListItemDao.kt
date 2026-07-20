package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ListItemDao {

    @Query("SELECT * FROM list_item WHERE listId = :listId")
    fun observeByList(listId: String): Flow<List<ListItemEntity>>

    @Query("SELECT * FROM list_item WHERE listId = :listId")
    suspend fun getByListId(listId: String): List<ListItemEntity>

    @Query("SELECT * FROM list_item WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ListItemEntity?

    @Query("SELECT * FROM list_item WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): ListItemEntity?

    @Query("SELECT id FROM list_item WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: String): Long?

    /** Local-only write — see [ListDao.upsert]'s doc comment. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ListItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(item: ListItemEntity): Long

    /** Backend-refresh write path — see [ListDao.upsertFromServer]. */
    @Transaction
    suspend fun upsertFromServer(items: List<ListItemEntity>) {
        items.forEach { item ->
            val serverId = item.serverId ?: return@forEach
            val existingLocalId = findLocalIdByServerId(serverId)
            replace(item.copy(id = existingLocalId ?: 0))
        }
    }

    // listId is corrected here too (not just carried over from the queued
    // payload) — see ListItemEntity's doc comment: an item queued while its
    // parent list was still offline holds its stand-in id until this point.
    // catalogProductId never needs the same correction (see ListItemEntity's
    // doc comment), so it's untouched here.
    @Query(
        "UPDATE list_item SET syncStatus = 'SYNCED', serverId = :serverId, listId = :listId, " +
            "outboxId = NULL WHERE id = :id",
    )
    suspend fun markSynced(id: Long, serverId: String, listId: String)

    @Query(
        "UPDATE list_item SET note = :note, quantity = :quantity, onSale = :onSale, " +
            "syncStatus = :syncStatus, outboxId = :outboxId WHERE id = :id",
    )
    suspend fun updateFields(
        id: Long,
        note: String?,
        quantity: Int?,
        onSale: Boolean,
        syncStatus: SyncStatus,
        outboxId: Long?,
    )

    @Query("DELETE FROM list_item WHERE id = :id")
    suspend fun delete(id: Long)

    // Used by ShoppingListRepository.deleteList to clear a deleted list's
    // items locally too (Room has no cross-entity cascade, unlike the
    // backend's ON DELETE CASCADE — see WorkTimeDao.deleteEntry for the
    // same reasoning applied to work_time_break).
    @Query("DELETE FROM list_item WHERE listId = :listId")
    suspend fun deleteByListId(listId: String)
}
