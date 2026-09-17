package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerDomainDao {

    @Query("SELECT * FROM tracker_domain WHERE userId = :userId ORDER BY position ASC")
    fun observeForUser(userId: String): Flow<List<TrackerDomainEntity>>

    @Query("SELECT * FROM tracker_domain WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TrackerDomainEntity?

    @Query("SELECT * FROM tracker_domain WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): TrackerDomainEntity?

    @Query("SELECT id FROM tracker_domain WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: String): Long?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM tracker_domain WHERE userId = :userId")
    suspend fun nextPosition(userId: String): Int

    /** Local-only write — see [ListDao.upsert]'s doc comment. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(domain: TrackerDomainEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(domain: TrackerDomainEntity): Long

    /** Backend-refresh write path — see [ListDao.upsertFromServer]. */
    @Transaction
    suspend fun upsertFromServer(userId: String, domains: List<TrackerDomainEntity>) {
        domains.forEach { domain ->
            val serverId = domain.serverId ?: return@forEach
            val existingLocalId = findLocalIdByServerId(serverId)
            replace(domain.copy(id = existingLocalId ?: 0))
        }
        deleteSyncedAbsentFromServer(userId, domains.mapNotNull { it.serverId })
    }

    @Query(
        "DELETE FROM tracker_domain WHERE userId = :userId AND syncStatus = 'SYNCED' " +
            "AND serverId NOT IN (:serverIds)",
    )
    suspend fun deleteSyncedAbsentFromServer(userId: String, serverIds: List<String>)

    @Query("UPDATE tracker_domain SET name = :name, color = :color, icon = :icon, syncStatus = :syncStatus, outboxId = :outboxId WHERE id = :id")
    suspend fun updateFields(id: Long, name: String, color: String, icon: String, syncStatus: SyncStatus, outboxId: Long?)

    @Query("UPDATE tracker_domain SET syncStatus = 'SYNCED', serverId = :serverId, outboxId = NULL WHERE id = :id")
    suspend fun markSynced(id: Long, serverId: String)

    @Query("DELETE FROM tracker_domain WHERE id = :id")
    suspend fun delete(id: Long)
}
