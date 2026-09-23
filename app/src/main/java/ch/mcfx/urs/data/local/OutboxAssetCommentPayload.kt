package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

/** Same shape as [OutboxAssetComponentPayload], against comments instead. */
@Serializable
data class OutboxAssetCommentPayload(val assetId: String, val text: String, val date: String)

@Serializable
data class OutboxAssetCommentUpdatePayload(val localCommentId: Long, val text: String, val date: String)

@Serializable
data class OutboxAssetCommentDeletePayload(val serverId: String)
