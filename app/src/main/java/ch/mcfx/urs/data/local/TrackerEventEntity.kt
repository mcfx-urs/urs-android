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
 *
 * [occurredOnEnd] (GitHub issue #83, Journal) is null for a single-day event,
 * or a later `yyyy-MM-dd` for one spanning a date range. [occurredAtEnd] is
 * the matching end time (`HH:mm`), only meaningful when [occurredAt] is also
 * set — an "all day" event (Journal's toggle) leaves both time fields null
 * regardless of whether it spans one day or several.
 */
@Entity(tableName = "tracker_event")
data class TrackerEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String? = null,
    val outboxId: Long? = null,
    val userId: String,
    val trackerTypeId: String,
    val occurredOn: String,
    val occurredOnEnd: String? = null,
    val occurredAt: String? = null,
    val occurredAtEnd: String? = null,
    val note: String? = null,
    val source: String = "manual",
    val syncStatus: SyncStatus,
)

/** Same stand-in-until-synced scheme as [ListItemEntity]'s. */
val TrackerEventEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)
