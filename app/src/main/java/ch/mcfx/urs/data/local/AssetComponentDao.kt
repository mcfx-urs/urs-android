package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetComponentDao {

    @Query("SELECT * FROM asset_component")
    fun observeAll(): Flow<List<AssetComponentEntity>>

    @Query("SELECT * FROM asset_component WHERE assetId = :assetId ORDER BY id ASC")
    suspend fun getByAssetId(assetId: String): List<AssetComponentEntity>

    @Query("SELECT * FROM asset_component WHERE id = :id")
    suspend fun getById(id: Long): AssetComponentEntity?

    @Query("SELECT * FROM asset_component WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): AssetComponentEntity?

    // Initial components created inline with a not-yet-synced asset (see
    // AssetRepository.createAsset) have no outbox mutation of their own —
    // their sync rides entirely on the asset's own create-mutation replay,
    // which looks these up by the asset's pre-sync stand-in id to correct
    // once the create response comes back (see SyncManager.replayCreateAsset).
    @Query("SELECT * FROM asset_component WHERE assetId = :assetId AND outboxId IS NULL AND syncStatus = 'PENDING' ORDER BY id ASC")
    suspend fun getPendingInlineByAssetId(assetId: String): List<AssetComponentEntity>

    @Query("SELECT id FROM asset_component WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(component: AssetComponentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(component: AssetComponentEntity): Long

    @Transaction
    suspend fun upsertFromServer(assetId: String, components: List<AssetComponentEntity>) {
        components.forEach { component ->
            val serverId = component.serverId ?: return@forEach
            val existingLocalId = findLocalIdByServerId(serverId)
            replace(component.copy(id = existingLocalId ?: 0))
        }
        deleteSyncedAbsentFromServer(assetId, components.mapNotNull { it.serverId })
    }

    @Query(
        "DELETE FROM asset_component WHERE assetId = :assetId AND syncStatus = 'SYNCED' " +
            "AND serverId NOT IN (:serverIds)",
    )
    suspend fun deleteSyncedAbsentFromServer(assetId: String, serverIds: List<String>)

    @Query(
        "UPDATE asset_component SET description = :description, manufacturer = :manufacturer, price = :price, " +
            "purchaseDate = :purchaseDate, dealer = :dealer, syncStatus = :syncStatus WHERE id = :id",
    )
    suspend fun updateFields(id: Long, description: String, manufacturer: String, price: String, purchaseDate: String, dealer: String, syncStatus: SyncStatus)

    @Query("UPDATE asset_component SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun clearPending(id: Long)

    // assetId is corrected here too — same reasoning as
    // KanbanChecklistItemDao.markSynced's cardId correction.
    @Query(
        "UPDATE asset_component SET syncStatus = 'SYNCED', serverId = :serverId, assetId = :assetId, " +
            "outboxId = NULL WHERE id = :id",
    )
    suspend fun markSynced(id: Long, serverId: String, assetId: String)

    @Query("DELETE FROM asset_component WHERE id = :id")
    suspend fun delete(id: Long)

    // Used by AssetRepository.deleteAsset to clear a deleted asset's
    // components locally too — Room has no cross-entity cascade.
    @Query("DELETE FROM asset_component WHERE assetId = :assetId")
    suspend fun deleteByAssetId(assetId: String)
}
