package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, always-available mirror of one occurrence of a tracker type
 * (GitHub issue #27). Same offline-first / outbox shape as [ListItemEntity].
 *
 * [trackerTypeId] is the parent type's [TrackerTypeEntity.publicId] — a real
 * `tracker_type_id` or a stand-in until the parent's own create has synced,
 * resolved at replay time (see `SyncManager.replayCreateTrackerEvent`).
 * [occurredOn] is `yyyy-MM-dd`; [occurredAt] is `HH:mm` or null ("no
 * particular time that day"). Future dates are allowed.
 */
@Entity(tableName = "tracker_event")
data class TrackerEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String? = null,
    val outboxId: Long? = null,
    val userId: String,
    val trackerTypeId: String,
    val occurredOn: String,
    val occurredAt: String? = null,
    val note: String? = null,
    val source: String = "manual",
    val syncStatus: SyncStatus,
)

/** Same stand-in-until-synced scheme as [ListItemEntity]'s. */
val TrackerEventEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)
