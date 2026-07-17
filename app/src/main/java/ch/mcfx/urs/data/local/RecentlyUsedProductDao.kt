package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyUsedProductDao {

    @Query("SELECT * FROM recently_used_product ORDER BY rank ASC")
    fun observeAll(): Flow<List<RecentlyUsedProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<RecentlyUsedProductEntity>)

    @Query("DELETE FROM recently_used_product")
    suspend fun deleteAll()

    /** Full replace on every refresh — see this entity's own doc comment. */
    @Transaction
    suspend fun replaceAll(entities: List<RecentlyUsedProductEntity>) {
        deleteAll()
        insertAll(entities)
    }
}
