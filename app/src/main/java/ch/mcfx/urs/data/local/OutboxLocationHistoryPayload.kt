package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

/** What gets JSON-encoded into [OutboxMutationEntity.payloadJson] for a queued location-history point creation. */
@Serializable
data class OutboxLocationHistoryPayload(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val capturedAt: Long,
)
