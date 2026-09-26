package ch.mcfx.urs.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local, always-available mirror of one column on a Kanban board — same
 * offline-first shape as [ListEntity]. [boardId] holds the parent
 * [KanbanBoardEntity.publicId] as it stood at creation time (a real backend
 * id, or a not-yet-synced stand-in) — a queued column create resolves it to
 * the board's real backend id at replay time (see `SyncManager`) and
 * corrects this column then, same as [ListItemEntity.listId]. [position]
 * drives display order, same convention as [RecentlyUsedProductEntity.rank]/
 * [BakePlanStepEntity.stepIndex].
 */
@Entity(tableName = "kanban_column")
data class KanbanColumnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Null until this row has been confirmed by the backend — the real kanban_column_id.
    val serverId: String? = null,
    // Links back to the OutboxMutationEntity row still driving this column's
    // sync, null once synced.
    val outboxId: Long? = null,
    val boardId: String,
    val name: String,
    // Applied server-side to a new card created in this column
    // (mcfx-urs/urs-backend#13) — null when unset.
    val defaultTagName: String? = null,
    val position: Int,
    val syncStatus: SyncStatus,
)

/** Same stand-in-until-synced scheme as [ListEntity.publicId]. */
val KanbanColumnEntity.publicId: String
    get() = serverId ?: localIdStandIn(id)

/** Reverses [publicId] — same shape as [localListId]. */
fun localKanbanColumnId(value: String): Long? = parseLocalIdStandIn(value)
