package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryCategoryDao {

    @Query("SELECT * FROM inventory_category")
    fun observeAll(): Flow<List<InventoryCategoryEntity>>

    @Query("SELECT * FROM inventory_category WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): InventoryCategoryEntity?

    @Query("SELECT * FROM inventory_category WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): InventoryCategoryEntity?

    @Query("SELECT serverId FROM inventory_category WHERE serverId IS NOT NULL")
    suspend fun allServerIds(): List<String>

    @Insert
    suspend fun insert(category: InventoryCategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(category: InventoryCategoryEntity): Long

    @Query("SELECT id FROM inventory_category WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: String): Long?

    /**
     * Local-only write — used for both a fresh offline create (see
     * InventoryRepository.createCategory) and any other one-off local
     * upsert. Not to be confused with [upsertFromServer], which reconciles
     * against an existing row keyed by [InventoryCategoryEntity.serverId].
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: InventoryCategoryEntity): Long

    /**
     * Backend-refresh write path — same reconciliation shape as
     * [FillDao.upsertFromServer]: keyed by [InventoryCategoryEntity
     * .serverId], never creates a second local row for the same server
     * category. A not-yet-synced row (no serverId) is a no-op here — it's
     * reconciled by [markSynced] instead, once its own create replays.
     */
    @Transaction
    suspend fun upsertFromServer(categories: List<InventoryCategoryEntity>) {
        categories.forEach { category ->
            val serverId = category.serverId ?: return@forEach
            val existingLocalId = findLocalIdByServerId(serverId)
            replace(category.copy(id = existingLocalId ?: 0))
        }
    }

    @Query(
        "UPDATE inventory_category SET syncStatus = 'SYNCED', serverId = :serverId, outboxId = NULL WHERE id = :id",
    )
    suspend fun markSynced(id: Long, serverId: String)

    @Query("DELETE FROM inventory_category WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: String)
}
