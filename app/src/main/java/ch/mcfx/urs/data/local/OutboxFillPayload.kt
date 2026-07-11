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
    val currencyCode: String,
    val stationId: String? = null,
    val stationLatitude: String? = null,
    val stationLongitude: String? = null,
)
