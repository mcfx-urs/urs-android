package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyUsedProductDao {

    @Query("SELECT * FROM recently_used_product WHERE listId = :listId ORDER BY rank ASC")
    fun observeForList(listId: String): Flow<List<RecentlyUsedProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<RecentlyUsedProductEntity>)

    @Query("DELETE FROM recently_used_product WHERE listId = :listId")
    suspend fun deleteForList(listId: String)

    /** Full replace of just this list's rows on every refresh — other lists' cached rows are untouched. */
    @Transaction
    suspend fun replaceForList(listId: String, entities: List<RecentlyUsedProductEntity>) {
        deleteForList(listId)
        insertAll(entities)
    }
}
