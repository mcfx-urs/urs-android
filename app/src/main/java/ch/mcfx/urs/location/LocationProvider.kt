package ch.mcfx.urs.location

import android.content.Context
import android.location.Location
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "location_provider_prefs"
private const val KEY_HAS_PROMPTED = "has_prompted_permission"

/**
 * App-wide ambient "roughly where is the device right now" value, refreshed
 * on app foreground (see [ch.mcfx.urs.UrsApplication]'s `ProcessLifecycleOwner`
 * observer) so any screen that opens already has a location to work with
 * without waiting on its own fetch. Distinct from [LocationCapture]'s direct
 * one-shot calls, which the ad-hoc fuel-stop flow and the life-map capture
 * worker keep using unchanged — a fresh fetch at the moment of capture
 * matters more than latency for those two.
 */
class LocationProvider(context: Context, private val locationCapture: LocationCapture) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    fun hasPermission(): Boolean = locationCapture.hasPermission()

    fun hasPromptedPermission(): Boolean = prefs.getBoolean(KEY_HAS_PROMPTED, false)

    fun markPermissionPrompted() {
        prefs.edit().putBoolean(KEY_HAS_PROMPTED, true).apply()
    }

    /** No-ops without permission or on a failed/timed-out fix — keeps whatever value it already had. */
    suspend fun refresh() {
        if (!hasPermission()) return
        locationCapture.captureLocation("ambient-refresh")?.let { _currentLocation.value = it }
    }
}
