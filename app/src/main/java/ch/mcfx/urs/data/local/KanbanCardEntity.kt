package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, always-available mirror of one Kanban card — same offline-first
 * shape as [ListEntity]. [columnId] holds the parent [KanbanColumnEntity
 * .publicId] as it stood at creation time, resolved to the real backend id
 * at replay time same as [KanbanColumnEntity.boardId]. [dueDate] is
 * date-only (`yyyy-MM-dd`, no time component — reminders always fire at a
 * fixed 08:00, see `KanbanCardAlarmScheduler`). [linkedNoteId] is an
 * optional [NoteEntity.publicId], never resolved/corrected the way
 * [columnId] is — a card linking to a note that hasn't synced yet simply
 * keeps showing its stand-in until the note itself syncs and the card is
 * next edited, same "no eager cross-entity correction" tradeoff already
 * accepted for [NoteTagEntity].
 */
@Entity(tableName = "kanban_card")
data class KanbanCardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Null until this row has been confirmed by the backend — the real kanban_card_id.
    val serverId: String? = null,
    // Links back to the OutboxMutationEntity row still driving this card's
    // sync, null once synced.
    val outboxId: Long? = null,
    val columnId: String,
    val position: Int,
    val title: String,
    val description: String = "",
    val dueDate: String? = null,
    val priority: String = PRIORITY_MEDIUM,
    val linkedNoteId: String? = null,
    val syncStatus: SyncStatus,
) {
    companion object {
        const val PRIORITY_LOW = "low"
        const val PRIORITY_MEDIUM = "medium"
        const val PRIORITY_HIGH = "high"
    }
}

/** Same stand-in-until-synced scheme as [ListEntity.publicId]. */
val KanbanCardEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)

/** Reverses [publicId] — same shape as [localListId]. */
fun localKanbanCardId(value: String): Long? = parseLocalIdStandIn(value)
