package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Query
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
}
