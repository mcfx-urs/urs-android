package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FillDao {

    @Query("SELECT * FROM fill ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<FillEntity>>

    @Query("SELECT * FROM fill WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): FillEntity?

    @Query("SELECT * FROM fill WHERE id = :id")
    suspend fun getById(id: Long): FillEntity?

    @Insert
    suspend fun insert(fill: FillEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(fill: FillEntity): Long

    @Query("SELECT id FROM fill WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: Long): Long?

    /**
     * Backend-refresh write path: reconciles a server-confirmed fill against
     * whatever local row (if any) already mirrors it, keyed by [FillEntity
     * .serverId] — never creates a second local row for the same server
     * fill. A [FillEntity] with no serverId (i.e. still only local) has no
     * business going through this path and is a no-op here.
     */
    @Transaction
    suspend fun upsertFromServer(fill: FillEntity) {
        val serverId = fill.serverId ?: return
        val existingLocalId = findLocalIdByServerId(serverId)
        replace(fill.copy(id = existingLocalId ?: 0))
    }

    @Query(
        "UPDATE fill SET syncStatus = 'SYNCED', serverId = :serverId, stationId = :stationId, outboxId = NULL " +
            "WHERE id = :id",
    )
    suspend fun markSynced(id: Long, serverId: Long, stationId: String)

    @Query("UPDATE fill SET syncStatus = 'FAILED' WHERE id = :id")
    suspend fun markFailed(id: Long)

    @Query(
        "UPDATE fill SET stationId = :stationId, sourceVehicleId = :sourceVehicleId, date = :date, " +
            "pricePerLiter = :pricePerLiter, liters = :liters, odometer = :odometer, isFullTank = :isFullTank, " +
            "currencyCode = :currencyCode, syncStatus = :syncStatus, outboxId = :outboxId WHERE id = :id",
    )
    suspend fun updateFields(
        id: Long,
        stationId: String?,
        sourceVehicleId: String?,
        date: String,
        pricePerLiter: String,
        liters: String,
        odometer: String,
        isFullTank: Boolean,
        currencyCode: String,
        syncStatus: SyncStatus,
        outboxId: Long?,
    )

    @Query("DELETE FROM fill WHERE id = :id")
    suspend fun deleteEntry(id: Long)
}
