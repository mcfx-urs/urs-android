package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

/** What gets JSON-encoded into [OutboxMutationEntity.payloadJson] for a queued inventory category creation. */
@Serializable
data class OutboxInventoryCategoryPayload(val name: String)
