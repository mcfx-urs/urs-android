package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryProductDao {

    @Query("SELECT * FROM inventory_product WHERE categoryId = :categoryId")
    fun observeByCategory(categoryId: String): Flow<List<InventoryProductEntity>>

    @Query("SELECT * FROM inventory_product WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): InventoryProductEntity?

    @Query("SELECT serverId FROM inventory_product WHERE categoryId = :categoryId AND serverId IS NOT NULL")
    suspend fun serverIdsInCategory(categoryId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(product: InventoryProductEntity): Long

    @Query("SELECT id FROM inventory_product WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: String): Long?

    /** Local-only write — see [InventoryCategoryDao.upsert]'s doc comment. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: InventoryProductEntity): Long

    /** Backend-refresh write path — see [InventoryCategoryDao.upsertFromServer]. */
    @Transaction
    suspend fun upsertFromServer(products: List<InventoryProductEntity>) {
        products.forEach { product ->
            val serverId = product.serverId ?: return@forEach
            val existingLocalId = findLocalIdByServerId(serverId)
            replace(product.copy(id = existingLocalId ?: 0))
        }
    }

    // categoryId is corrected here too (not just carried over from the
    // queued payload) — see InventoryProductEntity's doc comment: a product
    // queued while its parent category was still offline holds that
    // category's stand-in id until this point.
    @Query(
        "UPDATE inventory_product SET syncStatus = 'SYNCED', serverId = :serverId, categoryId = :categoryId, " +
            "outboxId = NULL WHERE id = :id",
    )
    suspend fun markSynced(id: Long, serverId: String, categoryId: String)

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
}
