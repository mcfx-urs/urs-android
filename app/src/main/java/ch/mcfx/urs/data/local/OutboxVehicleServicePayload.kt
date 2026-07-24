package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

@Serializable
data class OutboxVehicleServiceTagPayload(
    val code: String,
    val label: String? = null,
)

/** What gets JSON-encoded into [OutboxMutationEntity.payloadJson] for a queued vehicle service creation. */
@Serializable
data class OutboxVehicleServicePayload(
    val vehicleId: String,
    val date: String,
    val odometer: String,
    val provider: String = "",
    val isDiy: Boolean = false,
    val notes: String = "",
    val costAmount: String,
    val currencyCode: String,
    val tags: List<OutboxVehicleServiceTagPayload> = emptyList(),
)

/**
 * What gets JSON-encoded for a queued update to a service entry that's
 * already confirmed by the backend — [serverId] identifies it directly, so
 * replay never needs to look up a local row's current server id.
 */
@Serializable
data class OutboxVehicleServiceUpdatePayload(
    val serverId: String,
    val vehicleId: String,
    val date: String,
    val odometer: String,
    val provider: String = "",
    val isDiy: Boolean = false,
    val notes: String = "",
    val costAmount: String,
    val currencyCode: String,
    val tags: List<OutboxVehicleServiceTagPayload> = emptyList(),
)

/**
 * What gets JSON-encoded for a queued delete. Carries [serverId] directly
 * rather than a local row reference, since the local row is removed
 * immediately (offline-first) and won't exist any more by replay time.
 */
@Serializable
data class OutboxVehicleServiceDeletePayload(val serverId: String)
