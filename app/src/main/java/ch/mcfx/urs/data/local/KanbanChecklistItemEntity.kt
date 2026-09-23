package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One checklist item on a Kanban card — its own entity with full
 * create/update/delete outbox coverage, unlike [TagEntity]: the
 * backend exposes independent checklist-item CRUD endpoints
 * (`POST/PUT/DELETE /kanban/checklist-item`), so this mirrors
 * [ListItemEntity] rather than [TagEntity]. [cardId] holds the parent
 * [KanbanCardEntity.publicId] as it stood at creation time, resolved to the
 * real backend id at replay time same as [KanbanCardEntity.columnId].
 */
@Entity(tableName = "kanban_checklist_item")
data class KanbanChecklistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Null until this row has been confirmed by the backend — the real
    // kanban_checklist_item_id.
    val serverId: String? = null,
    // Links back to the OutboxMutationEntity row still driving this item's
    // sync, null once synced.
    val outboxId: Long? = null,
    val cardId: String,
    val text: String,
    val done: Boolean = false,
    val position: Int,
    val syncStatus: SyncStatus,
)

/** Same stand-in-until-synced scheme as [ListEntity.publicId]. */
val KanbanChecklistItemEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)

/** Reverses [publicId] — same shape as [localListId]. */
fun localKanbanChecklistItemId(value: String): Long? = parseLocalIdStandIn(value)
