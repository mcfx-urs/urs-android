package ch.mcfx.urs.location

import android.content.Context
import android.location.Location
import ch.mcfx.urs.data.local.LocationHistoryDao
import ch.mcfx.urs.data.local.LocationHistoryEntity
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
 * one-shot calls, which the ad-hoc fuel-stop flow and the breadcrumbs capture
 * worker keep using unchanged — a fresh fetch at the moment of capture
 * matters more than latency for those two.
 */
class LocationProvider(
    context: Context,
    private val locationCapture: LocationCapture,
    private val locationHistoryDao: LocationHistoryDao,
    private val historySettingsStore: LocationHistorySettingsStore,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

    fun hasPermission(): Boolean = locationCapture.hasPermission()

    fun hasPromptedPermission(): Boolean = prefs.getBoolean(KEY_HAS_PROMPTED, false)

    fun markPermissionPrompted() {
        prefs.edit().putBoolean(KEY_HAS_PROMPTED, true).apply()
    }

    /**
     * With Breadcrumbs capture on, takes the latest stored point and makes no GPS read of its own
     * (capture may be paused, e.g. on a home Wi-Fi). With it off, reads a fix. No-ops without
     * permission or on a failed/timed-out fix — keeps whatever value it already had.
     */
    suspend fun refresh() {
        if (!hasPermission()) return
        if (historySettingsStore.isEnabled()) {
            locationHistoryDao.getLatest()?.let { _currentLocation.value = it.toLocation() }
            return
        }
        locationCapture.captureLocation("ambient-refresh")?.let { _currentLocation.value = it }
    }

    private fun LocationHistoryEntity.toLocation() = Location("breadcrumbs").also {
        it.latitude = latitude
        it.longitude = longitude
        accuracyMeters?.let { accuracy -> it.accuracy = accuracy }
        it.time = capturedAt
    }
}
