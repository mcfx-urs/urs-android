package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One tag on one Note or one Kanban card — Notes and Kanban share one
 * server-side tag pool/color (mcfx-urs/urs-backend#11), replacing the
 * former separate `NoteTagEntity`/`KanbanCardTagEntity` tables. A purely
 * local, denormalized cache of whatever the backend last confirmed for that
 * note or card, not itself independently synced: a note's/card's full tag
 * set always rides along inside its own create/update outbox payload (same
 * "no separate child mutation" reasoning as [BakePlanStepEntity] for
 * steps), so there's no serverId/outboxId/syncStatus here, unlike a real
 * synced entity.
 *
 * Exactly one of [noteId]/[cardId] is set per row, never both, never
 * neither — both are the parent's stable local row id (not its public id),
 * same reasoning the two entities this replaces already used.
 *
 * [color] is server-assigned and never changes once a tag name first
 * exists for a user (GitHub issue #85 / mcfx-urs/urs-backend#7, extended to
 * Kanban card tags by mcfx-urs/urs-backend#11) - a hex string, parsed
 * client-side wherever the tag renders as a pill.
 */
@Entity(tableName = "tag")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long? = null,
    val cardId: Long? = null,
    val tagName: String,
    val color: String,
)
