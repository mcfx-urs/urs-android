package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, always-available mirror of a Journal domain (GitHub issue #83) — a
 * user-defined grouping for tracker types (e.g. Health, Household, Car).
 * Same offline-first / stand-in-publicId shape as [TrackerTypeEntity].
 *
 * [position] orders the domain list; new domains are appended (see
 * [TrackerDomainDao.nextPosition]).
 */
@Entity(tableName = "tracker_domain")
data class TrackerDomainEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String? = null,
    val outboxId: Long? = null,
    val userId: String,
    val name: String,
    val color: String,
    val icon: String,
    val position: Int = 0,
    val syncStatus: SyncStatus,
)

/** Same stand-in-until-synced scheme as [TrackerTypeEntity.publicId]. */
val TrackerDomainEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)

/** Reverses [publicId] — same shape as [localTrackerTypeId]. */
fun localTrackerDomainId(value: String): Long? = parseLocalIdStandIn(value)
