package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

/**
 * What gets JSON-encoded into [OutboxMutationEntity.payloadJson] for a
 * queued inventory product creation. [inventoryId] is the parent
 * inventory's [InventoryEntity.publicId] as it stood when this was queued —
 * a real backend id, or (if the inventory itself was still offline) a
 * not-yet-synced stand-in that `SyncManager` resolves to the real id at
 * replay time, once the inventory's own create has gone through.
 * [catalogProductId] is always a real `catalog_product` id — the
 * product identity and its inventory are both fixed at creation time, no
 * manually-typed name — see [ch.mcfx.urs.data.InventoryRepository
 * .createProduct].
 */
@Serializable
data class OutboxInventoryProductPayload(
    val inventoryId: String,
    val catalogProductId: String,
    val quantity: Int? = null,
)
