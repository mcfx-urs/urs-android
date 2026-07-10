package ch.mcfx.urs.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.StringReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class VpnConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

private const val TUNNEL_NAME = "urs"

// Wraps the official WireGuard Android tunnel library (GoBackend) to bring a
// single named tunnel up/down on demand — not an always-on background
// service. connect() is only called when NetworkGate determines the backend
// isn't directly reachable (e.g. away from the home Wi-Fi).
class WireGuardManager(
    private val context: Context,
    private val configRepository: VpnConfigRepository,
) : Tunnel {

    private val backend by lazy { GoBackend(context) }

    private val _state = MutableStateFlow(VpnConnectionState.DISCONNECTED)
    val state: StateFlow<VpnConnectionState> = _state.asStateFlow()

    override fun getName(): String = TUNNEL_NAME

    override fun onStateChange(newState: Tunnel.State) {
        _state.value = when (newState) {
            Tunnel.State.UP -> VpnConnectionState.CONNECTED
            Tunnel.State.DOWN -> VpnConnectionState.DISCONNECTED
            else -> _state.value
        }
    }

    fun isConfigured(): Boolean = !configRepository.getConfigText().isNullOrBlank()

    // Null if VPN permission is already granted; otherwise an Intent the
    // caller must launch (ActivityResultLauncher) for the user to approve.
    fun permissionIntentIfNeeded(): Intent? = VpnService.prepare(context)

    suspend fun connect(): Boolean {
        val configText = configRepository.getConfigText()
        if (configText.isNullOrBlank()) return false

        _state.value = VpnConnectionState.CONNECTING
        return try {
            val config = withContext(Dispatchers.IO) {
                Config.parse(BufferedReader(StringReader(configText)))
            }
            withContext(Dispatchers.IO) {
                backend.setState(this@WireGuardManager, Tunnel.State.UP, config)
            }
            _state.value = VpnConnectionState.CONNECTED
            true
        } catch (e: Exception) {
            _state.value = VpnConnectionState.ERROR
            false
        }
    }

    suspend fun disconnect() {
        try {
            withContext(Dispatchers.IO) {
                backend.setState(this@WireGuardManager, Tunnel.State.DOWN, null)
            }
        } catch (_: Exception) {
            // best-effort
        } finally {
            _state.value = VpnConnectionState.DISCONNECTED
        }
    }
}
