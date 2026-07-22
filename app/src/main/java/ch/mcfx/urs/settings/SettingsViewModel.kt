package ch.mcfx.urs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.vpn.NetworkGate
import ch.mcfx.urs.vpn.VpnConfigRepository
import ch.mcfx.urs.vpn.VpnConnectionState
import ch.mcfx.urs.vpn.WireGuardManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Owns the VPN settings screen's editable draft state (config text, home
// SSIDs) and delegates connect/disconnect to WireGuardManager. The config
// text is only ever persisted (encrypted) on saveConfig() — never logged,
// never sent anywhere.
class SettingsViewModel(
    private val configRepository: VpnConfigRepository,
    val wireGuardManager: WireGuardManager,
    private val networkGate: NetworkGate,
) : ViewModel() {

    private val _configText = MutableStateFlow(configRepository.getConfigText() ?: "")
    val configText: StateFlow<String> = _configText.asStateFlow()

    private val _newSsidDraft = MutableStateFlow("")
    val newSsidDraft: StateFlow<String> = _newSsidDraft.asStateFlow()

    private val _homeSsids = MutableStateFlow(configRepository.getHomeSsids())
    val homeSsids: StateFlow<Set<String>> = _homeSsids.asStateFlow()

    private val _justSaved = MutableStateFlow(false)
    val justSaved: StateFlow<Boolean> = _justSaved.asStateFlow()

    private val _exclusiveModeEnabled = MutableStateFlow(configRepository.isExclusiveModeEnabled())
    val exclusiveModeEnabled: StateFlow<Boolean> = _exclusiveModeEnabled.asStateFlow()

    val tunnelState: StateFlow<VpnConnectionState> get() = wireGuardManager.state

    fun setConfigText(value: String) {
        _configText.value = value
        _justSaved.value = false
    }

    fun setNewSsidDraft(value: String) {
        _newSsidDraft.value = value
    }

    fun addSsid() {
        val ssid = _newSsidDraft.value.trim()
        if (ssid.isEmpty()) return
        configRepository.addHomeSsid(ssid)
        _homeSsids.value = configRepository.getHomeSsids()
        _newSsidDraft.value = ""
    }

    fun removeSsid(ssid: String) {
        configRepository.removeHomeSsid(ssid)
        _homeSsids.value = configRepository.getHomeSsids()
    }

    fun saveConfig() {
        configRepository.setConfigText(_configText.value)
        _justSaved.value = true
    }

    // A flip while the tunnel is already up needs an explicit reconnect to
    // take effect: NetworkGate.ensureReachableLocked() short-circuits without
    // calling WireGuardManager.connect() again whenever its cached state is
    // already CONNECTED (that's what stops routine Wi-Fi capability ticks
    // from rebuilding an already-good tunnel — see NetworkGate's own
    // comments) — so even pressing "Connect" again wouldn't have picked up
    // the new config on its own.
    fun setExclusiveMode(enabled: Boolean) {
        val changed = enabled != _exclusiveModeEnabled.value
        configRepository.setExclusiveModeEnabled(enabled)
        _exclusiveModeEnabled.value = enabled
        if (changed && wireGuardManager.state.value == VpnConnectionState.CONNECTED) {
            viewModelScope.launch {
                networkGate.userDisconnect()
                networkGate.userConnect()
            }
        }
    }

    // Routed through NetworkGate (not WireGuardManager directly) so a manual
    // disconnect actually sticks instead of being silently overridden by the
    // automatic Wi-Fi-triggered reconnect logic — see NetworkGate.userDisconnect().
    fun connect() {
        viewModelScope.launch { networkGate.userConnect() }
    }

    fun disconnect() {
        viewModelScope.launch { networkGate.userDisconnect() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                SettingsViewModel(app.container.vpnConfigRepository, app.container.wireGuardManager, app.container.networkGate)
            }
        }
    }
}
