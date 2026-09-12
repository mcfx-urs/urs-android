package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

/**
 * What gets JSON-encoded into [OutboxMutationEntity.payloadJson] for a
 * queued checklist-item creation. [cardId] is the parent card's
 * [KanbanCardEntity.publicId] as it stood when this was queued — resolved
 * to the real id at replay time, same as [OutboxListItemPayload.listId].
 */
@Serializable
data class OutboxKanbanChecklistItemPayload(val cardId: String, val text: String)

/**
 * Identifies its target by the item's stable local row id — same
 * "resolved at replay time, retry if not yet synced" shape as
 * [OutboxNoteUpdatePayload]. The backend's checklist-item toggle has no
 * last-write-wins basis of its own (see `UpdateKanbanChecklistItem` in
 * urs-backend — a checkbox toggle has no meaningful "losing edit" to
 * protect against, same reasoning as `UpdateNoteStatus`), so unlike the
 * board/column/card update payloads above, nothing here needs
 * [OutboxMutationEntity.createdAt] as a basis.
 */
@Serializable
data class OutboxKanbanChecklistItemUpdatePayload(val localItemId: Long, val text: String, val done: Boolean)

/** Same shape as [OutboxListItemDeletePayload]. */
@Serializable
data class OutboxKanbanChecklistItemDeletePayload(val serverId: String)
