package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One tag on one Kanban card — a purely local, denormalized cache of
 * whatever the backend last confirmed for that card, not itself
 * independently synced: a card's full tag set always rides along inside
 * that card's own create/update outbox payload (backend: `PUT
 * /kanban/card/:id` takes a full `tags: []` replace, no separate tag
 * endpoints) — same shape as [NoteTagEntity], see that entity's own doc
 * comment for why [cardId] is the parent's stable local row id rather than
 * its [KanbanCardEntity.publicId].
 */
@Entity(tableName = "kanban_card_tag")
data class KanbanCardTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val tagName: String,
)
