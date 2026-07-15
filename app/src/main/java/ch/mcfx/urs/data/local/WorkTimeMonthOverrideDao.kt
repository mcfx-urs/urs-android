package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkTimeMonthOverrideDao {

    @Query("SELECT * FROM work_time_month_override")
    fun observeAll(): Flow<List<WorkTimeMonthOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: WorkTimeMonthOverrideEntity)

    @Query("DELETE FROM work_time_month_override WHERE year = :year AND month = :month")
    suspend fun delete(year: Int, month: Int)

    @Query("DELETE FROM work_time_month_override")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceAll(overrides: List<WorkTimeMonthOverrideEntity>) {
        deleteAll()
        overrides.forEach { upsert(it) }
    }
}
