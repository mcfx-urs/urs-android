package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AssetCommentDao {

    @Query("SELECT * FROM asset_comment")
    fun observeAll(): Flow<List<AssetCommentEntity>>

    @Query("SELECT * FROM asset_comment WHERE assetId = :assetId ORDER BY date DESC, id DESC")
    suspend fun getByAssetId(assetId: String): List<AssetCommentEntity>

    @Query("SELECT * FROM asset_comment WHERE id = :id")
    suspend fun getById(id: Long): AssetCommentEntity?

    @Query("SELECT * FROM asset_comment WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): AssetCommentEntity?

    @Query("SELECT id FROM asset_comment WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(comment: AssetCommentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(comment: AssetCommentEntity): Long

    @Transaction
    suspend fun upsertFromServer(assetId: String, comments: List<AssetCommentEntity>) {
        comments.forEach { comment ->
            val serverId = comment.serverId ?: return@forEach
            val existingLocalId = findLocalIdByServerId(serverId)
            replace(comment.copy(id = existingLocalId ?: 0))
        }
        deleteSyncedAbsentFromServer(assetId, comments.mapNotNull { it.serverId })
    }

    @Query(
        "DELETE FROM asset_comment WHERE assetId = :assetId AND syncStatus = 'SYNCED' " +
            "AND serverId NOT IN (:serverIds)",
    )
    suspend fun deleteSyncedAbsentFromServer(assetId: String, serverIds: List<String>)

    @Query("UPDATE asset_comment SET text = :text, date = :date, syncStatus = :syncStatus WHERE id = :id")
    suspend fun updateFields(id: Long, text: String, date: String, syncStatus: SyncStatus)

    @Query("UPDATE asset_comment SET syncStatus = 'SYNCED' WHERE id = :id")
    suspend fun clearPending(id: Long)

    @Query(
        "UPDATE asset_comment SET syncStatus = 'SYNCED', serverId = :serverId, assetId = :assetId, " +
            "outboxId = NULL WHERE id = :id",
    )
    suspend fun markSynced(id: Long, serverId: String, assetId: String)

    @Query("DELETE FROM asset_comment WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM asset_comment WHERE assetId = :assetId")
    suspend fun deleteByAssetId(assetId: String)
}
