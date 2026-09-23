package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

/**
 * One inline initial component, queued as part of [OutboxAssetPayload] —
 * the backend's `POST /api/v1/asset` accepts these in the same request that
 * creates the asset, so a simple single-purchase asset is one outbox
 * mutation, not a create-then-N-more-mutations sequence. Added later
 * (editing an already-existing asset) goes through
 * [OutboxAssetComponentPayload] instead, same as Kanban checklist items.
 */
@Serializable
data class OutboxAssetInitialComponent(
    val description: String,
    val manufacturer: String = "",
    val price: String,
    val purchaseDate: String,
    val dealer: String = "",
)

@Serializable
data class OutboxAssetPayload(
    val name: String,
    val category: String,
    val location: String = "",
    val tags: List<String> = emptyList(),
    val components: List<OutboxAssetInitialComponent> = emptyList(),
)

/**
 * Identifies its target by the asset's stable local row id — same
 * "resolved at replay time" shape as [OutboxNoteUpdatePayload]. Never
 * touches components/comments (see the dedicated payload types below).
 */
@Serializable
data class OutboxAssetUpdatePayload(
    val localAssetId: Long,
    val name: String,
    val category: String,
    val location: String = "",
    val status: String,
    val tags: List<String> = emptyList(),
)

@Serializable
data class OutboxAssetDeletePayload(val serverId: String)
