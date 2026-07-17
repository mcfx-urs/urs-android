package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryProductDao {

    @Query("SELECT * FROM inventory_product WHERE inventoryId = :inventoryId")
    fun observeByInventory(inventoryId: String): Flow<List<InventoryProductEntity>>

    @Query("SELECT * FROM inventory_product WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): InventoryProductEntity?

    @Query("SELECT * FROM inventory_product WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): InventoryProductEntity?

    // Dedup lookup for InventoryRepository.createProduct: is this catalog
    // product already tracked in this specific inventory — a product can be
    // tracked in some inventories and not others, unlike the old
    // household-wide dedup this replaces.
    @Query("SELECT * FROM inventory_product WHERE catalogProductId = :catalogProductId AND inventoryId = :inventoryId LIMIT 1")
    suspend fun findByCatalogProductId(catalogProductId: String, inventoryId: String): InventoryProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(product: InventoryProductEntity): Long

    @Query("SELECT id FROM inventory_product WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: String): Long?

    /** Local-only write — see [ListDao.upsert]'s doc comment. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: InventoryProductEntity): Long

    /** Backend-refresh write path — see [ListDao.upsertFromServer]. */
    @Transaction
    suspend fun upsertFromServer(products: List<InventoryProductEntity>) {
        products.forEach { product ->
            val serverId = product.serverId ?: return@forEach
            val existingLocalId = findLocalIdByServerId(serverId)
            replace(product.copy(id = existingLocalId ?: 0))
        }
    }

    // inventoryId is corrected here too (not just carried over from the
    // queued payload) — see InventoryProductEntity's doc comment: a product
    // queued while its parent inventory was still offline holds that
    // inventory's stand-in id until this point.
    @Query(
        "UPDATE inventory_product SET syncStatus = 'SYNCED', serverId = :serverId, inventoryId = :inventoryId, " +
            "outboxId = NULL WHERE id = :id",
    )
    suspend fun markSynced(id: Long, serverId: String, inventoryId: String)

    @Query("UPDATE inventory_product SET quantity = :quantity WHERE serverId = :serverId")
    suspend fun updateQuantityByServerId(serverId: String, quantity: Int?)

    @Query(
        "UPDATE inventory_product SET firstThreshold = :firstThreshold, secondThreshold = :secondThreshold, " +
            "reminderThreshold = :reminderThreshold, reminderHour = :reminderHour, reminderMinute = :reminderMinute " +
            "WHERE serverId = :serverId",
    )
    suspend fun updateSettingsByServerId(
        serverId: String,
        firstThreshold: Int?,
        secondThreshold: Int?,
        reminderThreshold: Int?,
        reminderHour: Int?,
        reminderMinute: Int?,
    )

    @Query("DELETE FROM inventory_product WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: String)

    // Used by InventoryRepository.deleteInventory to clear a deleted
    // inventory's products locally too (Room has no cross-entity cascade —
    // see ListItemDao.deleteByListId's doc comment for the same reasoning).
    @Query("DELETE FROM inventory_product WHERE inventoryId = :inventoryId")
    suspend fun deleteByInventoryId(inventoryId: String)

    @Query("SELECT * FROM inventory_product WHERE inventoryId = :inventoryId")
    suspend fun getByInventoryId(inventoryId: String): List<InventoryProductEntity>
}
