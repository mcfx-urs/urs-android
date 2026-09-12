package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

/**
 * What gets JSON-encoded into [OutboxMutationEntity.payloadJson] for a
 * queued card creation. [columnId] is the parent column's
 * [KanbanColumnEntity.publicId] as it stood when this was queued — resolved
 * to the real id at replay time, same as [OutboxListItemPayload.listId].
 */
@Serializable
data class OutboxKanbanCardPayload(
    val columnId: String,
    val title: String,
    val description: String = "",
    val dueDate: String? = null,
    val priority: String = KanbanCardEntity.PRIORITY_MEDIUM,
    val linkedNoteId: String? = null,
    val tags: List<String> = emptyList(),
)

/**
 * Identifies its target by the card's stable local row id — same
 * "resolved at replay time, retry if not yet synced" shape as
 * [OutboxNoteUpdatePayload], since a card can be edited before its own
 * create mutation has synced. The last-write-wins basis is
 * [OutboxMutationEntity.createdAt] at replay time, same as
 * [OutboxKanbanBoardUpdatePayload].
 */
@Serializable
data class OutboxKanbanCardUpdatePayload(
    val localCardId: Long,
    val title: String,
    val description: String = "",
    val dueDate: String? = null,
    val priority: String = KanbanCardEntity.PRIORITY_MEDIUM,
    val linkedNoteId: String? = null,
    val tags: List<String> = emptyList(),
)

/**
 * Same "resolved at replay time" shape as [OutboxKanbanColumnMovePayload].
 * [targetColumnId] is the destination column's publicId as it stood when
 * this was queued (may itself still be a stand-in) — resolved at replay
 * time same as create's own [OutboxKanbanCardPayload.columnId].
 */
@Serializable
data class OutboxKanbanCardMovePayload(val localCardId: Long, val targetColumnId: String, val index: Int)

/** Same shape as [OutboxListItemDeletePayload]. */
@Serializable
data class OutboxKanbanCardDeletePayload(val serverId: String)
