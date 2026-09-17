package ch.mcfx.urs.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

private const val TIMEOUT_MILLIS = 10_000L

/**
 * A single best-effort GPS fix for "capture where I'm standing right now"
 * (ad-hoc fuel stops) — not a live location feed. Built on the platform's
 * own [LocationManager] rather than Play Services' FusedLocationProviderClient
 * — a plain one-shot fix has no need for Play Services' extra machinery,
 * even though the app does now depend on it elsewhere (geofencing/activity
 * recognition for Location History's adaptive interval, GitHub issue #60).
 *
 * This is the app's single funnel for an explicit GPS read — every call site
 * (life-map periodic capture, geofence re-centering, ad-hoc fuel-stop
 * capture, the app-wide ambient location refresh) passes its own [captureLocation]
 * `source` tag, and every outcome is logged to [LocationCaptureDebugLog] so
 * "when did urs actually touch GPS" is answerable from the app's own log
 * instead of Android's system-level permission-usage screen (owner follow-up
 * to GitHub issue #60).
 */
class LocationCapture(private val context: Context, private val debugLog: LocationCaptureDebugLog) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * @param source short caller tag for the debug log, e.g. "history-worker",
     *   "history-geofence-center", "fuel-fill", "ambient-refresh".
     * @param mode the active capture mode (e.g. "moving,geofence-dense") for
     *   callers that have one — see [LocationCaptureModeManager.currentModeLabel]
     *   (GitHub issue #86); left `null` for callers this doesn't apply to
     *   (ad-hoc fuel-stop capture, the ambient refresh).
     * @param scheduledForMillis this run's expected trigger time, for drift logging
     *   (GitHub issue #86) — same caller-supplied value as `mode`, left `null` where
     *   there's no schedule to drift against. Logged on every outcome below, not only
     *   a successful fix, so a timeout or a stationary-dedup skip still leaves drift
     *   data behind — previously only a stored point did.
     * @return `null` on missing permission, no available provider, or a ~10s timeout with no fix.
     */
    @SuppressLint("MissingPermission") // guarded by hasPermission() above
    suspend fun captureLocation(source: String, mode: String? = null, scheduledForMillis: Long? = null): Location? {
        val skippedAt = System.currentTimeMillis()
        if (!hasPermission()) {
            debugLog.logCapture("GPS_READ_SKIPPED", "$source: no location permission", mode, skippedAt, skippedAt, scheduledForMillis)
            return null
        }
        val locationManager = ContextCompat.getSystemService(context, LocationManager::class.java)
        if (locationManager == null) {
            debugLog.logCapture("GPS_READ_SKIPPED", "$source: no LocationManager", mode, skippedAt, skippedAt, scheduledForMillis)
            return null
        }
        val provider = preferredProvider(locationManager)
        if (provider == null) {
            debugLog.logCapture("GPS_READ_SKIPPED", "$source: no location provider enabled", mode, skippedAt, skippedAt, scheduledForMillis)
            return null
        }

        val startMillis = System.currentTimeMillis()
        val location = withTimeoutOrNull(TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val signal = CancellationSignal()
                    locationManager.getCurrentLocation(
                        provider,
                        signal,
                        ContextCompat.getMainExecutor(context),
                    ) { location -> if (continuation.isActive) continuation.resume(location) }
                    continuation.invokeOnCancellation { signal.cancel() }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            locationManager.removeUpdates(this)
                            if (continuation.isActive) continuation.resume(location)
                        }
                    }
                    locationManager.requestSingleUpdate(provider, listener, context.mainLooper)
                    continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
                }
            }
        }
        val endMillis = System.currentTimeMillis()

        if (location == null) {
            debugLog.logCapture(
                "GPS_READ_TIMEOUT",
                "$source: no fix via $provider within ${TIMEOUT_MILLIS / 1000}s",
                mode,
                startMillis,
                endMillis,
                scheduledForMillis,
            )
        } else {
            debugLog.logCapture(
                "GPS_READ",
                "$source: %.5f, %.5f (±%.0fm, %s)".format(location.latitude, location.longitude, location.accuracy, provider),
                mode,
                startMillis,
                endMillis,
                scheduledForMillis,
            )
        }
        return location
    }

    private fun preferredProvider(locationManager: LocationManager): String? = when {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }
}
