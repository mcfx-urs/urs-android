package ch.mcfx.urs.vpn

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import androidx.core.content.ContextCompat

private const val UNKNOWN_SSID = "<unknown ssid>"

// Tracks the currently connected Wi-Fi SSID. Deliberately callback-fed rather
// than queried synchronously: even a NetworkCallback-delivered WifiInfo comes
// back redacted (SSID "<unknown ssid>") unless both ACCESS_FINE_LOCATION and
// NEARBY_WIFI_DEVICES are granted, AND the callback itself is registered with
// FLAG_INCLUDE_LOCATION_INFO (see NetworkGate.startObserving) — without that
// flag, location-sensitive fields are stripped regardless of permissions
// held. NetworkGate feeds this from the Wi-Fi NetworkCallback it already
// keeps registered.
class WifiSsidReader(private val context: Context) {

    @Volatile
    private var lastKnownSsid: String? = null

    fun hasPermission(): Boolean {
        val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val hasNearbyWifi = ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) ==
            PackageManager.PERMISSION_GRANTED
        return hasLocation && hasNearbyWifi
    }

    fun currentSsid(): String? = if (hasPermission()) lastKnownSsid else null

    fun onWifiCapabilitiesChanged(capabilities: NetworkCapabilities) {
        val info = capabilities.transportInfo as? WifiInfo ?: return
        val ssid = info.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != UNKNOWN_SSID } ?: return
        lastKnownSsid = ssid
    }

    fun onWifiLost() {
        lastKnownSsid = null
    }
}
