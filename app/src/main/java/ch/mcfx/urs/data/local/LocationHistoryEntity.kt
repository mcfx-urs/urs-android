package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single periodic GPS fix captured by
 * [ch.mcfx.urs.location.LocationCaptureWorker] for the life map feature.
 * Phase 1 was local-only, browsable in-app; this now carries the same
 * `serverId`/`outboxId`/`syncStatus` sync fields as [FillEntity], added in
 * Phase 3 once the outbox sync engine was wired up to actually replay
 * queued points to the backend.
 */
@Entity(tableName = "location_history", indices = [Index("capturedAt")])
data class LocationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Null until this row has been confirmed by the backend — the real
    // location_history_id (see LocationHistoryDao.markSynced).
    val serverId: Long? = null,
    // Links back to the OutboxMutationEntity row still driving this point's
    // sync, null once synced (see LocationHistoryDao.markSynced).
    val outboxId: Long? = null,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val capturedAt: Long,
    val syncStatus: SyncStatus,
)
