package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

/**
 * What gets JSON-encoded into [OutboxMutationEntity.payloadJson] for a
 * queued inventory product creation. [categoryId] is the parent category's
 * [InventoryCategoryEntity.publicId] as it stood when this was queued — a
 * real backend id, or (if the category itself was still offline) a
 * not-yet-synced stand-in that `SyncManager` resolves to the real id at
 * replay time, once the category's own create has gone through.
 */
@Serializable
data class OutboxInventoryProductPayload(
    val categoryId: String,
    val name: String,
    val quantity: Int? = null,
)
