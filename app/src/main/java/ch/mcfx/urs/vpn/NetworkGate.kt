package ch.mcfx.urs.vpn

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Decides whether the WireGuard tunnel needs to come up before reaching the
// backend: skip it (and tear it down if already up) on the configured home
// Wi-Fi, otherwise bring the tunnel up on demand. Not an always-on
// background service in the sense of a persistent foreground notification —
// but it does keep a lightweight ConnectivityManager callback registered for
// the lifetime of the process, so a live Wi-Fi change (e.g. walking home
// with the app already open) reacts immediately, not just at app startup.
class NetworkGate(
    private val context: Context,
    private val ssidReader: WifiSsidReader,
    private val configRepository: VpnConfigRepository,
    private val wireGuardManager: WireGuardManager,
    // Invoked whenever this class's own Wi-Fi callback observes connectivity
    // becoming available — used to opportunistically trigger a faster-than-
    // the-15-minute-floor outbox sync attempt (see SyncWorker.enqueueOneTime),
    // layered alongside (not replacing) this class's own VPN-tunnel decision.
    // No WorkManager dependency here; the caller supplies what "available"
    // should do.
    private val onConnectivityAvailable: () -> Unit = {},
) {

    sealed interface Result {
        data object OnHomeNetwork : Result
        data object Connected : Result
        data class NeedsVpnPermission(val intent: Intent) : Result
        data object NeedsLocationPermission : Result
        data object NotConfigured : Result
        data object ConnectFailed : Result
        data object UserDisabled : Result
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Set by userDisconnect() (the Settings screen's manual "Disconnect"
    // button) and cleared by userConnect() (manual "Connect"). Without this,
    // a manual disconnect away from home Wi-Fi looked like it did nothing:
    // the Wi-Fi NetworkCallback below re-runs ensureReachable() on the very
    // next routine capability tick (a few seconds away) and immediately
    // reconnects, since nothing distinguished "not connected because the
    // user turned it off" from "not connected yet". Per-process only, same
    // as WireGuardManager's own state — resets on a fresh app start, in
    // keeping with this class's existing "best-effort, no persisted intent"
    // design (see the class-level comment above).
    private var userDisabledTunnel = false

    // Startup can trigger ensureReachable() from two places at once (the
    // initial app-launch check and startObserving()'s onCapabilitiesChanged,
    // which fires immediately for an already-connected Wi-Fi network) —
    // without serializing them, two concurrent runs could interleave and
    // reach opposite conclusions (one disconnecting, the other reconnecting)
    // off of inconsistent in-flight state.
    private val mutex = Mutex()

    suspend fun ensureReachable(): Result = mutex.withLock { ensureReachableLocked() }

    // Settings screen's manual "Disconnect" action.
    suspend fun userDisconnect() = mutex.withLock {
        userDisabledTunnel = true
        wireGuardManager.disconnect()
    }

    // Settings screen's manual "Connect" action — clears the override above
    // so the automatic home/away logic resumes normally afterward.
    suspend fun userConnect(): Result = mutex.withLock {
        userDisabledTunnel = false
        ensureReachableLocked()
    }

    private suspend fun ensureReachableLocked(): Result {
        val homeSsids = configRepository.getHomeSsids()
        if (homeSsids.isNotEmpty()) {
            if (!ssidReader.hasPermission()) return Result.NeedsLocationPermission
            awaitSsidIfWifiPresentAndUnknown()
            if (ssidReader.currentSsid() in homeSsids) {
                // Always call disconnect() here, not just when our in-memory
                // state says CONNECTED: that state is per-process and resets
                // to DISCONNECTED on a fresh app start, even though the
                // actual OS-level tunnel (brought up by a previous process
                // instance) can still be running. GoBackend.setState(DOWN)
                // is a safe no-op if nothing is actually up.
                wireGuardManager.disconnect()
                onConnectivityAvailable()
                return Result.OnHomeNetwork
            }
        }

        if (!wireGuardManager.isConfigured()) return Result.NotConfigured

        wireGuardManager.permissionIntentIfNeeded()?.let { intent ->
            return Result.NeedsVpnPermission(intent)
        }

        // The Wi-Fi NetworkCallback re-runs ensureReachable() on every
        // onCapabilitiesChanged tick, including routine RSSI-only updates
        // Android sends for an already-connected, unchanged Wi-Fi network —
        // not just on a real SSID change. Without this check, an already-up
        // tunnel got torn down and rebuilt from scratch on every such tick
        // (confirmed via on-device logcat), which is what made the VPN/Wi-Fi
        // status-bar icons flap repeatedly while the app was open.
        if (wireGuardManager.state.value == VpnConnectionState.CONNECTED) {
            onConnectivityAvailable()
            return Result.Connected
        }

        if (userDisabledTunnel) return Result.UserDisabled

        if (!wireGuardManager.connect()) return Result.ConnectFailed
        // The Wi-Fi NetworkCallback below only fires onConnectivityAvailable()
        // for live Wi-Fi transport changes — it never sees a cold app start
        // away from home Wi-Fi, where this is the only place a freshly
        // established VPN tunnel is observed. Without this, a periodic
        // SyncWorker run that happens to race the tunnel coming up (or fails
        // for any other transient reason) has no opportunistic retry to fall
        // back on and is left waiting out WorkManager's exponential backoff,
        // even after the tunnel is confirmed up and reachable.
        onConnectivityAvailable()
        return Result.Connected
    }

    // Registering a NetworkCallback is documented to immediately dispatch the
    // current state of any already-matching network, but on-device testing
    // showed this isn't reliable for a Wi-Fi network that's been connected
    // and steady for a while (as opposed to one that just (re)associated):
    // the very first ensureReachable() at app startup can race ahead of that
    // dispatch and see no SSID yet, wrongly concluding "not home" and
    // bringing the tunnel up for no reason. If Wi-Fi is connected at all but
    // its SSID hasn't reached WifiSsidReader yet, wait briefly for the
    // callback rather than deciding on ambiguous state.
    private suspend fun awaitSsidIfWifiPresentAndUnknown() {
        if (ssidReader.currentSsid() != null) return
        if (!isWifiConnectedNow()) return
        repeat(10) {
            if (ssidReader.currentSsid() != null) return
            delay(150)
        }
    }

    private fun isWifiConnectedNow(): Boolean {
        val connectivityManager = ContextCompat.getSystemService(context, ConnectivityManager::class.java)
            ?: return false
        return connectivityManager.allNetworks.any { network ->
            connectivityManager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    // Registers a process-lifetime Wi-Fi connectivity listener so
    // ensureReachable() re-runs on every Wi-Fi connect/disconnect, not just
    // at app startup — otherwise arriving home (or leaving it) while the app
    // is already open would go unnoticed until the next cold start. Safe to
    // call more than once; only the first registration takes effect.
    fun startObserving(scope: CoroutineScope) {
        if (networkCallback != null) return
        val connectivityManager = ContextCompat.getSystemService(context, ConnectivityManager::class.java)
            ?: return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        // FLAG_INCLUDE_LOCATION_INFO (API 31+): without it, the WifiInfo
        // delivered here always carries a redacted SSID ("<unknown ssid>")
        // regardless of permissions held. Not available below API 31 — the
        // plain no-arg constructor there means home-SSID detection simply
        // can't work pre-31 (a real platform limitation, not a bug), so the
        // gate always tunnels rather than risk skipping VPN on a network it
        // can't actually verify.
        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            wifiCallbackWithLocationInfo(scope)
        } else {
            wifiCallbackLegacy(scope)
        }
        connectivityManager.registerNetworkCallback(request, callback)
        networkCallback = callback
    }

    // onCapabilitiesChanged fires on every routine capability tick (RSSI-only
    // updates included, see ensureReachableLocked()'s own comment on this),
    // not just on a genuine connectivity change. ensureReachableLocked()
    // already calls onConnectivityAvailable() itself on every outcome that's
    // actually reachable (OnHomeNetwork, already-Connected, freshly
    // Connected) — an unconditional extra call here fired it a second time on
    // every such tick, and even on outcomes that aren't reachable at all
    // (e.g. ConnectFailed), triggering a redundant PullCoordinator.pullAll()
    // pass each time (GitHub issue #71).
    @RequiresApi(Build.VERSION_CODES.S)
    private fun wifiCallbackWithLocationInfo(scope: CoroutineScope): ConnectivityManager.NetworkCallback =
        object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                ssidReader.onWifiCapabilitiesChanged(capabilities)
                scope.launch { ensureReachable() }
            }

            override fun onLost(network: Network) {
                ssidReader.onWifiLost()
                scope.launch { ensureReachable() }
            }
        }

    private fun wifiCallbackLegacy(scope: CoroutineScope): ConnectivityManager.NetworkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                ssidReader.onWifiCapabilitiesChanged(capabilities)
                scope.launch { ensureReachable() }
            }

            override fun onLost(network: Network) {
                ssidReader.onWifiLost()
                scope.launch { ensureReachable() }
            }
        }

    fun stopObserving() {
        val callback = networkCallback ?: return
        val connectivityManager = ContextCompat.getSystemService(context, ConnectivityManager::class.java)
        connectivityManager?.unregisterNetworkCallback(callback)
        networkCallback = null
    }
}
