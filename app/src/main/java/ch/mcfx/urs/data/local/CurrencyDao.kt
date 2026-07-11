package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyDao {

    @Query("SELECT * FROM currency ORDER BY code ASC")
    fun observeAll(): Flow<List<CurrencyEntity>>

    @Query("SELECT COUNT(*) FROM currency")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(currencies: List<CurrencyEntity>)
}
