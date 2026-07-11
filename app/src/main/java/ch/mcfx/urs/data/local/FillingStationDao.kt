package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FillingStationDao {

    @Query("SELECT * FROM filling_station ORDER BY name ASC")
    fun observeAll(): Flow<List<FillingStationEntity>>

    @Query("SELECT * FROM filling_station WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): FillingStationEntity?

    @Upsert
    suspend fun upsert(station: FillingStationEntity)

    @Upsert
    suspend fun upsertAll(stations: List<FillingStationEntity>)
}
