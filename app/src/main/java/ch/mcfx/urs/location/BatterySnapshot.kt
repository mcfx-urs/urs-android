package ch.mcfx.urs.location

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * A point-in-time battery/power-state reading, attached to capture log
 * entries (GitHub issue #86) so battery cost can be correlated with when and
 * how a capture actually ran, rather than only with Android's own coarse
 * per-session location-access indicator.
 */
data class BatterySnapshot(
    val batteryPercent: Int?,
    val isCharging: Boolean?,
    val isPowerSaveMode: Boolean?,
    val isDeviceIdleMode: Boolean?,
) {
    companion object {
        fun capture(context: Context): BatterySnapshot {
            val batteryManager = ContextCompat.getSystemService(context, BatteryManager::class.java)
            val powerManager = ContextCompat.getSystemService(context, PowerManager::class.java)
            val percent = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
            return BatterySnapshot(
                batteryPercent = percent,
                isCharging = batteryManager?.isCharging,
                isPowerSaveMode = powerManager?.isPowerSaveMode,
                isDeviceIdleMode = powerManager?.isDeviceIdleMode,
            )
        }
    }
}
