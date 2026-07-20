package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogCategoryDao {

    @Query("SELECT * FROM catalog_category")
    fun observeAll(): Flow<List<CatalogCategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<CatalogCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOne(entity: CatalogCategoryEntity)

    //  edit form write path — see CatalogProductDao.updateFields.
    @Query("UPDATE catalog_category SET name = :name, catalogImageId = :catalogImageId WHERE id = :id")
    suspend fun updateFields(id: String, name: String, catalogImageId: Int?)

    @Query("DELETE FROM catalog_category WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM catalog_category")
    suspend fun deleteAll()

    /** Backend-refresh write path — full replace, same shape as [CatalogProductDao.upsertAll]. */
    @Transaction
    suspend fun upsertAll(entities: List<CatalogCategoryEntity>) {
        deleteAll()
        insertAll(entities)
    }
}
