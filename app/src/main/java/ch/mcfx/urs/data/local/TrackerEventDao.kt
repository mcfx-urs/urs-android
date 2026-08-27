package ch.mcfx.urs.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerEventDao {

    @Query("SELECT * FROM tracker_event WHERE userId = :userId")
    fun observeForUser(userId: String): Flow<List<TrackerEventEntity>>

    @Query("SELECT * FROM tracker_event WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TrackerEventEntity?

    @Query("SELECT * FROM tracker_event WHERE outboxId = :outboxId LIMIT 1")
    suspend fun getByOutboxId(outboxId: Long): TrackerEventEntity?

    @Query("SELECT id FROM tracker_event WHERE serverId = :serverId LIMIT 1")
    suspend fun findLocalIdByServerId(serverId: String): Long?

    /** Local-only write — see [ListDao.upsert]'s doc comment. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(event: TrackerEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replace(event: TrackerEventEntity): Long

    /** Backend-refresh write path — see [ListDao.upsertFromServer]. */
    @Transaction
    suspend fun upsertFromServer(userId: String, events: List<TrackerEventEntity>) {
        events.forEach { event ->
            val serverId = event.serverId ?: return@forEach
            val existingLocalId = findLocalIdByServerId(serverId)
            replace(event.copy(id = existingLocalId ?: 0))
        }
        deleteSyncedAbsentFromServer(userId, events.mapNotNull { it.serverId })
    }

    // Reconciliation half of [upsertFromServer] — same rationale as
    // [ListItemDao.deleteSyncedAbsentFromServer]. Scoped by user (events are
    // pulled for the whole user, not per type), so a full refresh is the
    // authority on which SYNCED events still exist.
    @Query(
        "DELETE FROM tracker_event WHERE userId = :userId AND syncStatus = 'SYNCED' " +
            "AND serverId NOT IN (:serverIds)",
    )
    suspend fun deleteSyncedAbsentFromServer(userId: String, serverIds: List<String>)

    // trackerTypeId is corrected here too — an event queued while its parent
    // type was still offline holds the stand-in id until this point (same as
    // ListItemDao.markSynced's listId correction).
    @Query(
        "UPDATE tracker_event SET syncStatus = 'SYNCED', serverId = :serverId, trackerTypeId = :trackerTypeId, " +
            "outboxId = NULL WHERE id = :id",
    )
    suspend fun markSynced(id: Long, serverId: String, trackerTypeId: String)

    @Query(
        "UPDATE tracker_event SET trackerTypeId = :trackerTypeId, occurredOn = :occurredOn, occurredAt = :occurredAt, " +
            "note = :note, syncStatus = :syncStatus, outboxId = :outboxId WHERE id = :id",
    )
    suspend fun updateFields(
        id: Long,
        trackerTypeId: String,
        occurredOn: String,
        occurredAt: String?,
        note: String?,
        syncStatus: SyncStatus,
        outboxId: Long?,
    )

    @Query("DELETE FROM tracker_event WHERE id = :id")
    suspend fun delete(id: Long)

    // Used when a type is hard-removed locally (not the normal soft-archive
    // path) — mirrors ListItemDao.deleteByListId.
    @Query("DELETE FROM tracker_event WHERE trackerTypeId = :trackerTypeId")
    suspend fun deleteByTypeId(trackerTypeId: String)
}
