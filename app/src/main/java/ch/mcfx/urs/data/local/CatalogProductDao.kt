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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CatalogProductEntity>)

    @Query("DELETE FROM catalog_product")
    suspend fun deleteAll()

    /**
     * Backend-refresh write path — full replace, not a per-row reconciliation
     * like [InventoryCategoryDao.upsertFromServer]: [CatalogProductEntity.id]
     * is already the real server id (no local-only rows ever exist for this
     * entity), so there's no local identity to preserve across a refresh.
     */
    @Transaction
    suspend fun upsertAll(entities: List<CatalogProductEntity>) {
        deleteAll()
        insertAll(entities)
    }
}
