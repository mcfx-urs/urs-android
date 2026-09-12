package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

/** What gets JSON-encoded into [OutboxMutationEntity.payloadJson] for a queued board creation. */
@Serializable
data class OutboxKanbanBoardPayload(val name: String)

/**
 * What gets JSON-encoded for a queued rename of a board that's already
 * confirmed by the backend — [serverId] identifies it directly, same shape
 * as [OutboxListUpdatePayload]. The last-write-wins basis sent to the
 * backend is [OutboxMutationEntity.createdAt] at replay time (see
 * `SyncManager.replayUpdateKanbanBoard`), not carried in this payload —
 * always the device's current time at the moment this mutation was queued,
 * never a previously-fetched `updated_at`.
 */
@Serializable
data class OutboxKanbanBoardUpdatePayload(val serverId: String, val name: String)

/** Same shape as [OutboxListDeletePayload]. */
@Serializable
data class OutboxKanbanBoardDeletePayload(val serverId: String)
