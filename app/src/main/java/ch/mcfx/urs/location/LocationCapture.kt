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
 * (ad-hoc fuel stops) — not a live location feed. Deliberately built on the
 * platform's own [LocationManager] rather than Play Services'
 * FusedLocationProviderClient, which this app has no other dependency on
 * and isn't worth adding just for this.
 */
class LocationCapture(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** @return `null` on missing permission, no available provider, or a ~10s timeout with no fix. */
    @SuppressLint("MissingPermission") // guarded by hasPermission() above
    suspend fun captureLocation(): Location? {
        if (!hasPermission()) return null
        val locationManager = ContextCompat.getSystemService(context, LocationManager::class.java) ?: return null
        val provider = preferredProvider(locationManager) ?: return null

        return withTimeoutOrNull(TIMEOUT_MILLIS) {
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
    }

    private fun preferredProvider(locationManager: LocationManager): String? = when {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }
}
