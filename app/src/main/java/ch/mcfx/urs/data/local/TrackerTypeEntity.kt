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
 * [userId] filters [TrackerTypeDao]'s observe queries, same per-user
 * privacy defense-in-depth as [NoteEntity.userId].
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
    val archivedAtMillis: Long? = null,
    val lastExportedAtMillis: Long? = null,
    val syncStatus: SyncStatus,
)

/** Same stand-in-until-synced scheme as [ListEntity.publicId]. */
val TrackerTypeEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)

/** Reverses [publicId] — same shape as [localListId]. */
fun localTrackerTypeId(value: String): Long? = parseLocalIdStandIn(value)
