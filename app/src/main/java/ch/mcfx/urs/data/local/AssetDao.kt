package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import ch.mcfx.urs.data.AssetCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetDao {

    @Query("SELECT * FROM asset ORDER BY id DESC")
    fun observeAll(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM asset WHERE id = :id")
    suspend fun getById(id: Long): AssetEntity?

    @Query("SELECT * FROM asset WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): AssetEntity?

    @Query("SELECT id FROM asset WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(asset: AssetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(asset: AssetEntity): Long

    /** Backend-refresh write path — see [ListDao.upsertFromServer]. */
    @Transaction
    suspend fun upsertFromServer(assets: List<AssetEntity>) {
        val serverIds = mutableListOf<String>()
        assets.forEach { asset ->
            val serverId = asset.serverId ?: return@forEach
            serverIds += serverId
            val existingLocalId = findLocalIdByServerId(serverId)
            replace(asset.copy(id = existingLocalId ?: 0))
        }
        deleteSyncedAbsentFromServer(serverIds)
    }

    // Reconciliation half of upsertFromServer — a synced row no longer
    // present server-side was deleted elsewhere, drop the local mirror too.
    @Query("DELETE FROM asset WHERE syncStatus = 'SYNCED' AND serverId NOT IN (:serverIds)")
    suspend fun deleteSyncedAbsentFromServer(serverIds: List<String>)

    @Query(
        "UPDATE asset SET name = :name, category = :category, location = :location, " +
            "status = :status, tags = :tags, syncStatus = :syncStatus WHERE id = :id",
    )
    suspend fun updateFields(id: Long, name: String, category: AssetCategory, location: String, status: String, tags: List<String>, syncStatus: SyncStatus)

    @Query("UPDATE asset SET totalValue = :totalValue WHERE id = :id")
    suspend fun updateTotalValue(id: Long, totalValue: String)

    @Query("UPDATE asset SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun clearPending(id: Long)

    @Query("UPDATE asset SET syncStatus = 'SYNCED', serverId = :serverId, outboxId = NULL WHERE id = :id")
    suspend fun markSynced(id: Long, serverId: String)

    @Query("DELETE FROM asset WHERE id = :id")
    suspend fun delete(id: Long)
}
