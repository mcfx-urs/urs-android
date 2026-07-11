package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CarDao {

    @Query("SELECT * FROM car ORDER BY brand ASC, model ASC")
    fun observeAll(): Flow<List<CarEntity>>

    @Query("SELECT * FROM car WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CarEntity?

    @Upsert
    suspend fun upsertAll(cars: List<CarEntity>)
}
