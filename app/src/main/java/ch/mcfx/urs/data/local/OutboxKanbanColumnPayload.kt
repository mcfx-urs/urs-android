package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

/**
 * What gets JSON-encoded into [OutboxMutationEntity.payloadJson] for a
 * queued column creation. [boardId] is the parent board's
 * [KanbanBoardEntity.publicId] as it stood when this was queued — resolved
 * to the real id at replay time, same as [OutboxListItemPayload.listId].
 */
@Serializable
data class OutboxKanbanColumnPayload(val boardId: String, val name: String, val defaultTagName: String? = null)

/** Same shape as [OutboxKanbanBoardUpdatePayload]. */
@Serializable
data class OutboxKanbanColumnUpdatePayload(val serverId: String, val name: String, val defaultTagName: String? = null)

/**
 * Identifies its target by the column's stable local row id, not its
 * (possibly still-null) serverId — a column can be reordered before its own
 * create mutation has synced, same "resolved at replay time, retry if the
 * parent isn't synced yet" shape as [OutboxNoteUpdatePayload]. Never rides
 * along inside the create payload — the backend always appends a newly
 * created column at the end (see `InsertKanbanColumn`), so a move queued
 * before the create syncs still has real work to do once it does.
 */
@Serializable
data class OutboxKanbanColumnMovePayload(val localColumnId: Long, val index: Int)

/** Same shape as [OutboxListDeletePayload]. */
@Serializable
data class OutboxKanbanColumnDeletePayload(val serverId: String)
