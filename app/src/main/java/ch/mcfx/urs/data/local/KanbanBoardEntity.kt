package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, always-available mirror of a Kanban board (GitHub issue #62) — same
 * offline-first shape as [ListEntity], including full create/rename/delete
 * outbox coverage (see `KanbanRepository.renameBoard`/`deleteBoard`).
 */
@Entity(tableName = "kanban_board")
data class KanbanBoardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Null until this row has been confirmed by the backend — the real kanban_board_id.
    val serverId: String? = null,
    // Links back to the OutboxMutationEntity row still driving this board's
    // sync, null once synced.
    val outboxId: Long? = null,
    val name: String,
    val syncStatus: SyncStatus,
)

/** Same stand-in-until-synced scheme as [ListEntity.publicId]. */
val KanbanBoardEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)

/** Reverses [publicId] — same shape as [localListId]. */
fun localKanbanBoardId(value: String): Long? = parseLocalIdStandIn(value)
