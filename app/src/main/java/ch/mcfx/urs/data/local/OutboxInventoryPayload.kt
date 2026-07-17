package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

/** What gets JSON-encoded into [OutboxMutationEntity.payloadJson] for a queued inventory creation. */
@Serializable
data class OutboxInventoryPayload(val name: String)

/**
 * What gets JSON-encoded for a queued rename of an inventory that's already
 * confirmed by the backend — [serverId] identifies it directly, same shape
 * as [OutboxListUpdatePayload].
 */
@Serializable
data class OutboxInventoryUpdatePayload(val serverId: String, val name: String)

/**
 * What gets JSON-encoded for a queued delete. Carries [serverId] directly
 * rather than a local row reference, since the local row is removed
 * immediately (offline-first) and won't exist any more by replay time —
 * same shape as [OutboxListDeletePayload].
 */
@Serializable
data class OutboxInventoryDeletePayload(val serverId: String)
