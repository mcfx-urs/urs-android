package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single periodic GPS fix captured by
 * [ch.mcfx.urs.location.LocationCaptureWorker] for the life map feature
 * (Phase 1: local-only, browsable in-app). Deliberately minimal — no
 * server/outbox fields yet, unlike [FillEntity]; a later sync phase extends
 * this the same way that entity already carries `serverId`/`outboxId`.
 */
@Entity(tableName = "location_history", indices = [Index("capturedAt")])
data class LocationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val capturedAt: Long,
)
