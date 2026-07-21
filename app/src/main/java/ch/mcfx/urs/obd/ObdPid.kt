package ch.mcfx.urs.obd

/**
 * A standard SAE J1979 mode-01 PID: the raw command string sent to the
 * adapter, how many data bytes the response carries, and the formula to
 * turn those bytes into a physical value. Only the four PIDs this module
 * currently polls are defined - add more here as needed, no other layer
 * needs to change.
 */
sealed class ObdPid<T>(val command: String, val dataByteCount: Int) {

    abstract fun parse(data: List<Int>): T

    /** RPM = ((A*256)+B)/4. */
    object EngineRpm : ObdPid<Int>("010C", 2) {
        override fun parse(data: List<Int>): Int = ((data[0] * 256) + data[1]) / 4
    }

    /** Speed in km/h = A. */
    object VehicleSpeed : ObdPid<Int>("010D", 1) {
        override fun parse(data: List<Int>): Int = data[0]
    }

    /** Coolant temp in °C = A-40. */
    object CoolantTemp : ObdPid<Int>("0105", 1) {
        override fun parse(data: List<Int>): Int = data[0] - 40
    }

    /** Fuel level in % = A*100/255. */
    object FuelLevel : ObdPid<Int>("012F", 1) {
        override fun parse(data: List<Int>): Int = (data[0] * 100) / 255
    }

    companion object {
        /** All PIDs this module polls, in poll order. */
        val ALL: List<ObdPid<Int>> = listOf(EngineRpm, VehicleSpeed, CoolantTemp, FuelLevel)
    }
}
