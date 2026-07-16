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

    // Used by ShoppingListRepository.observeItems to resolve each list
    // item's product name/category across every category at once, not just
    // one — see that repository's doc comment for why this is a plain Room
    // query combined in Kotlin rather than a cross-table SQL join.
    @Query("SELECT * FROM inventory_product")
    fun observeAll(): Flow<List<InventoryProductEntity>>

    // Household-product half of AddProductViewModel's search (the other
    // half is CatalogProductDao.search) — global across every category,
    // unlike observeByCategory, since AddProductScreen searches the whole
    // household, not one category at a time.
    @Query("SELECT * FROM inventory_product WHERE name LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<InventoryProductEntity>>

    @Query("SELECT * FROM inventory_product WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): InventoryProductEntity?

    @Query("SELECT * FROM inventory_product WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): InventoryProductEntity?

    // Dedup lookup for ShoppingListRepository.addCatalogProduct: is there
    // already a household product for this catalog entry, so adding the
    // same catalog product to a list twice reuses one inventory_product row
    // instead of creating a second one each time.
    @Query("SELECT * FROM inventory_product WHERE catalogProductId = :catalogProductId LIMIT 1")
    suspend fun findByCatalogProductId(catalogProductId: String): InventoryProductEntity?

    @Query("SELECT serverId FROM inventory_product WHERE categoryId = :categoryId AND serverId IS NOT NULL")
    suspend fun serverIdsInCategory(categoryId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(product: InventoryProductEntity): Long

    @Query("SELECT id FROM inventory_product WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: String): Long?

    /** Local-only write — see [InventoryCategoryDao.upsert]'s doc comment. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(product: InventoryProductEntity): Long

    /**
     * Backend-refresh write path — see [InventoryCategoryDao.upsertFromServer].
     * [InventoryProductEntity.catalogProductId] is carried over from
     * whatever local row already exists rather than taken from [products]
     * (effectively always empty there — see that field's doc comment), so a
     * refresh never erases what a local create already knows. Every other
     * field, including the recent-note trio, is taken as-is from [products]
     * — those have no local write path, so the server value is always
     * authoritative for them.
     */
    @Transaction
    suspend fun upsertFromServer(products: List<InventoryProductEntity>) {
        products.forEach { product ->
            val serverId = product.serverId ?: return@forEach
            val existingLocalId = findLocalIdByServerId(serverId)
            val preservedCatalogProductId = existingLocalId?.let { getById(it)?.catalogProductId }
            replace(product.copy(id = existingLocalId ?: 0, catalogProductId = preservedCatalogProductId))
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
