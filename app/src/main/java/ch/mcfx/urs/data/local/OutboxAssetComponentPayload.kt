package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

/**
 * What gets JSON-encoded for a queued component creation added to an
 * already-existing asset — [assetId] is the parent [AssetEntity.publicId]
 * as it stood when this was queued, resolved to the real id at replay time
 * (same shape as [OutboxKanbanChecklistItemPayload.cardId]).
 */
@Serializable
data class OutboxAssetComponentPayload(
    val assetId: String,
    val description: String,
    val manufacturer: String = "",
    val price: String,
    val purchaseDate: String,
    val dealer: String = "",
)

/** Identifies its target by the component's stable local row id. */
@Serializable
data class OutboxAssetComponentUpdatePayload(
    val localComponentId: Long,
    val description: String,
    val manufacturer: String = "",
    val price: String,
    val purchaseDate: String,
    val dealer: String = "",
)

@Serializable
data class OutboxAssetComponentDeletePayload(val serverId: String)
