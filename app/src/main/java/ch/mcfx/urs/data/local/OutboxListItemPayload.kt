package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

/**
 * What gets JSON-encoded into [OutboxMutationEntity.payloadJson] for a
 * queued list-item creation. [listId]/[productId] are the parent rows'
 * [ListEntity.publicId]/[InventoryProductEntity.publicId] as they stood
 * when this was queued — real backend ids, or (if either parent was still
 * offline) not-yet-synced stand-ins that `SyncManager` resolves to the real
 * ids at replay time, once each parent's own create has gone through.
 */
@Serializable
data class OutboxListItemPayload(
    val listId: String,
    val productId: String,
    val note: String? = null,
)

/**
 * What gets JSON-encoded for a queued update to an item that's already
 * confirmed by the backend — [serverId] identifies it directly, same shape
 * as [OutboxWorkTimeEntryUpdatePayload].
 */
@Serializable
data class OutboxListItemUpdatePayload(
    val serverId: String,
    val note: String? = null,
    val checked: Boolean = false,
)

/** Same shape as [OutboxWorkTimeEntryDeletePayload]. */
@Serializable
data class OutboxListItemDeletePayload(val serverId: String)
