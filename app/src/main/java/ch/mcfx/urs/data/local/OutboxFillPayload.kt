package ch.mcfx.urs.data.local

import kotlinx.serialization.Serializable

/**
 * What actually gets JSON-encoded into [OutboxMutationEntity.payloadJson]
 * for a queued fill creation. Deliberately its own type rather than reusing
 * [ch.mcfx.urs.data.remote.FillPayload] directly: the wire payload's
 * station counter is only knowable at replay time (the *current* cached
 * counter, not whatever was cached when the form was submitted — see
 * `SyncManager`), so it has no place in what gets queued here. Either
 * [stationId] is set (a known station) or [stationLatitude]/
 * [stationLongitude] are (an ad-hoc GPS-located stop) — never both, never
 * neither, enforced by [ch.mcfx.urs.fuel.FillFormState.isValid] before this
 * is ever built.
 */
@Serializable
data class OutboxFillPayload(
    val carId: String,
    val fuelId: String,
    val date: String,
    val odometer: String,
    val pricePerLiter: String,
    val liters: String,
    val driven: String,
    val isFullTank: Boolean = true,
    val currencyCode: String,
    val stationId: String? = null,
    val stationLatitude: String? = null,
    val stationLongitude: String? = null,
)

/**
 * What gets JSON-encoded into [OutboxMutationEntity.payloadJson] for a
 * queued update to a fill that's already confirmed by the backend —
 * [serverId] identifies it directly, so replay never needs to look up a
 * local row's current server id. No ad-hoc-GPS station support here (unlike
 * [OutboxFillPayload]) — the backend's `PUT /api/v1/fill/{id}` route has no
 * ad-hoc-station-creation branch, so editing an already-synced fill always
 * requires picking a known station (enforced by `FuelAddScreen` locking the
 * GPS toggle once `FillFormState.editingIsSynced` is true).
 */
@Serializable
data class OutboxFillUpdatePayload(
    val serverId: String,
    val carId: String,
    val fuelId: String,
    val date: String,
    val stationId: String,
    val odometer: String,
    val pricePerLiter: String,
    val liters: String,
    val isFullTank: Boolean = true,
    val currencyCode: String,
)

/**
 * What gets JSON-encoded for a queued delete. Carries [serverId] directly
 * rather than a local row reference, since the local row is removed
 * immediately (offline-first) and won't exist any more by replay time.
 */
@Serializable
data class OutboxFillDeletePayload(val serverId: String)
