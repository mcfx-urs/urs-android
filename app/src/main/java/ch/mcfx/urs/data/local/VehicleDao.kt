package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Query("SELECT * FROM vehicle ORDER BY brand ASC, model ASC")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicle WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VehicleEntity?

    @Upsert
    suspend fun upsertAll(vehicles: List<VehicleEntity>)

    @Upsert
    suspend fun upsert(vehicle: VehicleEntity)

    @Query("DELETE FROM vehicle WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM vehicle")
    suspend fun count(): Int

    @Query("DELETE FROM vehicle WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /** Delta write path: upserts the changed vehicles and removes the deleted ones. */
    @Transaction
    suspend fun applyDelta(vehicles: List<VehicleEntity>, deletedIds: List<String>) {
        upsertAll(vehicles)
        if (deletedIds.isNotEmpty()) deleteByIds(deletedIds)
    }
}
