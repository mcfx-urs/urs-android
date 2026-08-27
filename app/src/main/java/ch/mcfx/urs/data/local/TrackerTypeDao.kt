package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerTypeDao {

    @Query("SELECT * FROM tracker_type WHERE userId = :userId")
    fun observeForUser(userId: String): Flow<List<TrackerTypeEntity>>

    @Query("SELECT * FROM tracker_type WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TrackerTypeEntity?

    @Query("SELECT * FROM tracker_type WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): TrackerTypeEntity?

    @Query("SELECT id FROM tracker_type WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: String): Long?

    /** Local-only write — see [ListDao.upsert]'s doc comment. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(type: TrackerTypeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(type: TrackerTypeEntity): Long

    /** Backend-refresh write path — see [ListDao.upsertFromServer]. */
    @Transaction
    suspend fun upsertFromServer(userId: String, types: List<TrackerTypeEntity>) {
        types.forEach { type ->
            val serverId = type.serverId ?: return@forEach
            val existingLocalId = findLocalIdByServerId(serverId)
            replace(type.copy(id = existingLocalId ?: 0))
        }
        deleteSyncedAbsentFromServer(userId, types.mapNotNull { it.serverId })
    }

    // Reconciliation half of [upsertFromServer] — same rationale as
    // [ListItemDao.deleteSyncedAbsentFromServer]. A type archived server-side
    // still comes back from the pull (archived types are returned), so this
    // only removes types genuinely gone server-side.
    @Query(
        "DELETE FROM tracker_type WHERE userId = :userId AND syncStatus = 'SYNCED' " +
            "AND serverId NOT IN (:serverIds)",
    )
    suspend fun deleteSyncedAbsentFromServer(userId: String, serverIds: List<String>)

    @Query("UPDATE tracker_type SET name = :name, color = :color, icon = :icon, calendar = :calendar, syncStatus = :syncStatus, outboxId = :outboxId WHERE id = :id")
    suspend fun updateFields(id: Long, name: String, color: String, icon: String, calendar: String?, syncStatus: SyncStatus, outboxId: Long?)

    @Query("UPDATE tracker_type SET lastExportedAtMillis = :millis WHERE id = :id")
    suspend fun updateLastExported(id: Long, millis: Long)

    @Query("UPDATE tracker_type SET archivedAtMillis = :archivedAtMillis, syncStatus = :syncStatus, outboxId = :outboxId WHERE id = :id")
    suspend fun updateArchived(id: Long, archivedAtMillis: Long?, syncStatus: SyncStatus, outboxId: Long?)

    @Query("UPDATE tracker_type SET syncStatus = 'SYNCED', serverId = :serverId, outboxId = NULL WHERE id = :id")
    suspend fun markSynced(id: Long, serverId: String)

    @Query("DELETE FROM tracker_type WHERE id = :id")
    suspend fun delete(id: Long)
}
