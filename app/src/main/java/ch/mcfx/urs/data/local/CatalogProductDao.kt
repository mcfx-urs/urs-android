package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogProductDao {

    @Query("SELECT * FROM catalog_product")
    fun observeAll(): Flow<List<CatalogProductEntity>>

    @Query(
        "SELECT * FROM catalog_product WHERE name LIKE '%' || :query || '%' " +
            "OR searchTerms LIKE '%' || :query || '%' OR brands LIKE '%' || :query || '%'",
    )
    fun search(query: String): Flow<List<CatalogProductEntity>>

    // Household-management (Inventory) and Shopping List "browse by
    // category" tab both drill from a CatalogCategoryEntity into its own
    // products this way.
    @Query("SELECT * FROM catalog_product WHERE catalogCategoryId = :categoryId")
    fun observeByCategory(categoryId: String): Flow<List<CatalogProductEntity>>

    // Sorted server-side would need its own endpoint; popularityIndex is
    // already cached locally, so "most popular" (AddProductScreen's HÄUFIG
    // tab) is just a Room ORDER BY over the existing cache. NULLS LAST via
    // the "IS NULL" trick since SQLite has no native NULLS LAST clause.
    @Query(
        "SELECT * FROM catalog_product ORDER BY popularityIndex IS NULL, popularityIndex DESC LIMIT :limit",
    )
    fun observeMostPopular(limit: Int): Flow<List<CatalogProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CatalogProductEntity>)

    // Single-row upsert for CatalogRepository.createProduct's direct-REST
    // create response — a full upsertAll() would otherwise wipe out every
    // other already-cached row, since that path is a delete-then-reinsert.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOne(entity: CatalogProductEntity)

    //  edit form write path — targeted column update so unrelated
    // cached fields (categoryName, searchTerms, brands, popularityIndex,
    // recentNote*) survive an edit untouched, unlike upsertOne's full-row
    // replace.
    @Query("UPDATE catalog_product SET name = :name, catalogCategoryId = :catalogCategoryId, catalogImageId = :catalogImageId WHERE id = :id")
    suspend fun updateFields(id: String, name: String, catalogCategoryId: String?, catalogImageId: Int?)

    @Query("DELETE FROM catalog_product WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM catalog_product")
    suspend fun deleteAll()

    /**
     * Backend-refresh write path — full replace, not a per-row
     * reconciliation: [CatalogProductEntity.id] is already the real server
     * id (no local-only rows ever exist for this entity), so there's no
     * local identity to preserve across a refresh.
     */
    @Transaction
    suspend fun upsertAll(entities: List<CatalogProductEntity>) {
        deleteAll()
        insertAll(entities)
    }
}
