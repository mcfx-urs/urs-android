package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Mirrors [ListDao] exactly — see [InventoryEntity]'s doc comment for why. */
@Dao
interface InventoryDao {

    // See InventoryEntity's doc comment on [InventoryEntity.userId] — this
    // filter is what stops a previous user's synced inventories from
    // showing up after switching the logged-in user on one device.
    @Query("SELECT * FROM inventory WHERE userId = :userId")
    fun observeAll(userId: String): Flow<List<InventoryEntity>>

    @Query("SELECT * FROM inventory WHERE userId = :userId AND isFavorite = 1 ORDER BY name ASC")
    fun observeFavorites(userId: String): Flow<List<InventoryEntity>>

    @Query("UPDATE inventory SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun setFavorite(id: Long, isFavorite: Boolean)

    @Query("SELECT * FROM inventory WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): InventoryEntity?

    @Query("SELECT * FROM inventory WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): InventoryEntity?

    @Query("SELECT id FROM inventory WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: String): Long?

    /** Local-only write — see [ListDao.upsert]'s doc comment. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(inventory: InventoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(inventory: InventoryEntity): Long

    /** Backend-refresh write path — see [ListDao.upsertFromServer]. */
    @Transaction
    suspend fun upsertFromServer(inventories: List<InventoryEntity>) {
        inventories.forEach { inventory ->
            val serverId = inventory.serverId ?: return@forEach
            val existingLocalId = findLocalIdByServerId(serverId)
            // isFavorite is a local-only UI preference, never part of the
            // server payload — carry the existing row's value forward so a
            // backend refresh doesn't silently un-favorite it.
            val existingIsFavorite = existingLocalId?.let { getById(it)?.isFavorite } ?: false
            replace(inventory.copy(id = existingLocalId ?: 0, isFavorite = existingIsFavorite))
        }
    }

    @Query(
        "UPDATE inventory SET name = :name, syncStatus = :syncStatus, outboxId = :outboxId WHERE id = :id",
    )
    suspend fun updateFields(id: Long, name: String, syncStatus: SyncStatus, outboxId: Long?)

    @Query("UPDATE inventory SET syncStatus = 'SYNCED', serverId = :serverId, outboxId = NULL WHERE id = :id")
    suspend fun markSynced(id: Long, serverId: String)

    @Query("DELETE FROM inventory WHERE id = :id")
    suspend fun delete(id: Long)
}
