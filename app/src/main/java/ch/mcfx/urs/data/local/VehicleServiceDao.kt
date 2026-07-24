package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleServiceDao {

    @Transaction
    @Query("SELECT * FROM vehicle_service ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<VehicleServiceWithTags>>

    @Transaction
    @Query("SELECT * FROM vehicle_service WHERE vehicleId = :vehicleId ORDER BY date DESC, id DESC")
    fun observeForVehicle(vehicleId: String): Flow<List<VehicleServiceWithTags>>

    @Query("SELECT * FROM vehicle_service WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): VehicleServiceEntity?

    @Query("SELECT * FROM vehicle_service WHERE id = :id")
    suspend fun getById(id: Long): VehicleServiceEntity?

    @Transaction
    @Query("SELECT * FROM vehicle_service WHERE id = :id")
    suspend fun getWithTagsById(id: Long): VehicleServiceWithTags?

    @Insert
    suspend fun insertService(service: VehicleServiceEntity): Long

    @Insert
    suspend fun insertTags(tags: List<VehicleServiceTagEntity>)

    @Query("DELETE FROM vehicle_service_tag WHERE serviceId = :serviceId")
    suspend fun deleteTags(serviceId: Long)

    @Transaction
    suspend fun replaceTags(serviceId: Long, tags: List<VehicleServiceTagEntity>) {
        deleteTags(serviceId)
        insertTags(tags)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceService(service: VehicleServiceEntity): Long

    @Query("SELECT id FROM vehicle_service WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: Long): Long?

    /**
     * Backend-refresh write path — same reconciliation shape as
     * [WorkTimeDao.upsertFromServer]: keyed by
     * [VehicleServiceEntity.serverId], never creates a second local row for
     * the same server entry, and replaces the local tag set wholesale
     * rather than diffing it.
     */
    @Transaction
    suspend fun upsertFromServer(service: VehicleServiceEntity, tags: List<VehicleServiceTagEntity>) {
        val serverId = service.serverId ?: return
        val existingLocalId = findLocalIdByServerId(serverId)
        val localId = replaceService(service.copy(id = existingLocalId ?: 0))
        replaceTags(localId, tags.map { it.copy(serviceId = localId) })
    }

    @Query(
        "UPDATE vehicle_service SET syncStatus = 'SYNCED', serverId = :serverId, outboxId = NULL " +
            "WHERE id = :id",
    )
    suspend fun markSynced(id: Long, serverId: Long)

    @Query("UPDATE vehicle_service SET syncStatus = 'FAILED' WHERE id = :id")
    suspend fun markFailed(id: Long)

    @Query(
        "UPDATE vehicle_service SET vehicleId = :vehicleId, date = :date, odometer = :odometer, " +
            "provider = :provider, isDiy = :isDiy, notes = :notes, costAmount = :costAmount, " +
            "currencyCode = :currencyCode, syncStatus = :syncStatus, outboxId = :outboxId " +
            "WHERE id = :id",
    )
    suspend fun updateFields(
        id: Long,
        vehicleId: String,
        date: String,
        odometer: String,
        provider: String,
        isDiy: Boolean,
        notes: String,
        costAmount: String,
        currencyCode: String,
        syncStatus: SyncStatus,
        outboxId: Long?,
    )

    @Query("DELETE FROM vehicle_service WHERE id = :id")
    suspend fun deleteServiceRow(id: Long)

    // vehicle_service_tag rows have no Room-level foreign key/cascade
    // (unlike the backend's ON DELETE CASCADE) — deleted explicitly here.
    @Transaction
    suspend fun deleteEntry(id: Long) {
        deleteTags(id)
        deleteServiceRow(id)
    }
}
