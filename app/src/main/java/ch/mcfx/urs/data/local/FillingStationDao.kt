package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT COUNT(*) FROM filling_station")
    suspend fun count(): Int

    @Query("DELETE FROM filling_station WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    /** Delta write path: upserts the changed stations and removes the deleted ones. */
    @Transaction
    suspend fun applyDelta(stations: List<FillingStationEntity>, deletedIds: List<String>) {
        upsertAll(stations)
        if (deletedIds.isNotEmpty()) deleteByIds(deletedIds)
    }
}
