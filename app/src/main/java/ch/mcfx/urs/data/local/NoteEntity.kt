package ch.mcfx.urs.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * Local, always-available mirror of a note (GitHub issue #10) — same
 * offline-first shape as [BakePlanEntity]. [status] is `"active"`/
 * `"completed"`, mirroring the backend's plain-string convention.
 *
 * [userId] is the locally logged-in user this row was written under — same
 * defense-in-depth reasoning as [InventoryEntity.userId]:
 * notes are strictly private per user, never shared, so [NoteDao]'s observe
 * queries filter on this column rather than relying solely on the
 * logout-wipe to keep one user's notes from ever appearing under another.
 */
@Entity(tableName = "note")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Null until this row has been confirmed by the backend — the real note_id.
    val serverId: String? = null,
    // Links back to the OutboxMutationEntity row still driving this note's
    // sync, null once synced.
    val outboxId: Long? = null,
    val userId: String,
    val title: String,
    val content: String = "",
    val reminderAtMillis: Long? = null,
    val status: String,
    val completedAtMillis: Long? = null,
    val syncStatus: SyncStatus,
)

/** Same stand-in-until-synced scheme as [ListEntity.publicId]. */
val NoteEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)

/** Reverses [publicId] — same shape as [localListId]. */
fun localNoteId(value: String): Long? = parseLocalIdStandIn(value)

/** A note joined with its tags — what the list/detail UI actually renders, built via Room's [Relation]. */
data class NoteWithTags(
    @Embedded val note: NoteEntity,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val tags: List<TagEntity>,
)
