package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One tag on one note — a purely local, denormalized cache of whatever the
 * backend last confirmed for that note (see `data.Note.Tags` in
 * urs-backend), not itself independently synced: a note's full tag set
 * always rides along inside that note's own create/update outbox payload
 * (same "no separate child mutation" reasoning as [BakePlanStepEntity] for
 * steps), so there's no serverId/outboxId/syncStatus here, unlike a real
 * synced entity. [noteId] is the parent [NoteEntity]'s stable local row id
 * (not its [NoteEntity.publicId]) — unlike a bake plan step, a tag has no
 * server identity of its own to re-parent when the note syncs, so there's
 * nothing gained from tracking the public id the way steps do.
 *
 * [color] is server-assigned and never changes once a tag name first exists
 * for a user (GitHub issue #85 / mcfx-urs/urs-backend#7) - a hex string,
 * parsed client-side wherever the tag renders as a pill.
 */
@Entity(tableName = "note_tag")
data class NoteTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val tagName: String,
    val color: String,
)
