package ch.mcfx.urs.obd

enum class ObdConnectionState { DISCONNECTED, CONNECTING, INITIALIZING, CONNECTED, RECONNECTING, ERROR }

/**
 * Latest known value per PID. A `null` field means that PID's last poll
 * returned `NO DATA`/an error/a parse failure, not that it was never
 * queried - callers should keep showing the previous non-null value rather
 * than treating `null` as "zero".
 */
data class ObdReading(
    val rpm: Int? = null,
    val speedKmh: Int? = null,
    val coolantTempC: Int? = null,
    val fuelLevelPercent: Int? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
)
