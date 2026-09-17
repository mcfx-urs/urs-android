package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, always-available mirror of a tracker type (GitHub issue #27) — one
 * kind of thing the user logs ("changed bedsheets"). Same offline-first
 * shape as [ListEntity]: a stand-in [publicId] until the backend confirms a
 * real `tracker_type_id`.
 *
 * [icon] is either a Material icon token or a single emoji — the UI decides
 * how to render. [archivedAtMillis] non-null = soft-archived: hidden from
 * pickers, but its past events still render in the calendar.
 *
 * [calendar] is an optional label that groups this type's events into one
 * `.ics` file on export (GitHub issue #28); [lastExportedAtMillis] marks
 * how far the last "only new events" export got.
 *
 * [expectedIntervalDays] is an optional cadence in days (GitHub issue
 * #29): when set, the stats strip flags the type overdue once it has been
 * that long since the last event, and an opt-in reminder can fire.
 *
 * [userId] filters [TrackerTypeDao]'s observe queries, same per-user
 * privacy defense-in-depth as [NoteEntity.userId].
 *
 * [domainId] is the parent [TrackerDomainEntity.publicId] (GitHub issue #83,
 * Journal) — a real `tracker_domain_id` or a stand-in until the domain's own
 * create has synced, resolved at replay time same as
 * [TrackerEventEntity.trackerTypeId]. Nullable only because a type created
 * before Journal existed has none until the next [TrackerTypeDao.upsertFromServer]
 * refresh backfills it — Journal's own type editor always sets one.
 */
@Entity(tableName = "tracker_type")
data class TrackerTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String? = null,
    val outboxId: Long? = null,
    val userId: String,
    val name: String,
    val color: String,
    val icon: String,
    val calendar: String? = null,
    val expectedIntervalDays: Int? = null,
    val archivedAtMillis: Long? = null,
    val lastExportedAtMillis: Long? = null,
    val domainId: String? = null,
    val syncStatus: SyncStatus,
)

/** Same stand-in-until-synced scheme as [ListEntity.publicId]. */
val TrackerTypeEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)

/** Reverses [publicId] — same shape as [localListId]. */
fun localTrackerTypeId(value: String): Long? = parseLocalIdStandIn(value)
