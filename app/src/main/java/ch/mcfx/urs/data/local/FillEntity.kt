package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SyncStatus { SYNCED, PENDING, FAILED }

/**
 * Local, always-available mirror of a fuel fill-up — the source of truth
 * the UI reads from (see [FillDao.observeAll]), independent of whether the
 * row has actually reached the backend yet.
 */
@Entity(tableName = "fill")
data class FillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Null until this row has been confirmed by the backend (see
    // FillDao.markSynced) — the real fill_id, not the local autoincrement id.
    val serverId: Long? = null,
    // Links back to the OutboxMutationEntity row still driving this fill's
    // sync, null once synced (see FillDao.markSynced).
    val outboxId: Long? = null,
    // Null while an ad-hoc station's real ID is still unknown — resolved to
    // the backend's assigned filling_station_id once this fill syncs.
    val stationId: String? = null,
    val vehicleId: String,
    val fuelId: String,
    val date: String,
    val pricePerLiter: String,
    val liters: String,
    val odometer: String,
    val driven: String,
    val isFullTank: Boolean = true,
    val currencyCode: String,
    val syncStatus: SyncStatus,
)
