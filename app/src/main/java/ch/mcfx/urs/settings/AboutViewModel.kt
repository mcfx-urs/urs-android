package ch.mcfx.urs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.local.OutboxDao
import ch.mcfx.urs.data.local.OutboxStatus
import ch.mcfx.urs.data.sync.ReachabilityChecker
import ch.mcfx.urs.data.sync.SyncStatusStore
import ch.mcfx.urs.vpn.VpnConnectionState
import ch.mcfx.urs.vpn.WireGuardManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AboutSyncState(
    val pendingCount: Int,
    val failedCount: Int,
    val lastSyncedAt: Long?,
)

sealed interface BackendState {
    data object Checking : BackendState
    data object Reachable : BackendState
    data object Unreachable : BackendState
}

/**
 * Backs the About screen's "State" section — read-only observation of
 * existing sync/backend/VPN state. Deliberately has no side effects on the
 * rest of the app beyond the explicit, user-triggered [recheckBackend]: this
 * is a status view, not a place that should itself trigger a sync.
 */
class AboutViewModel(
    private val reachabilityChecker: ReachabilityChecker,
    val wireGuardManager: WireGuardManager,
    outboxDao: OutboxDao,
    syncStatusStore: SyncStatusStore,
) : ViewModel() {

    val vpnState: StateFlow<VpnConnectionState> get() = wireGuardManager.state

    private val _backendState = MutableStateFlow<BackendState>(BackendState.Checking)
    val backendState: StateFlow<BackendState> = _backendState.asStateFlow()

    val syncState: StateFlow<AboutSyncState> = outboxDao.observeAll()
        .map { mutations ->
            AboutSyncState(
                pendingCount = mutations.count { it.status != OutboxStatus.FAILED },
                failedCount = mutations.count { it.status == OutboxStatus.FAILED },
                lastSyncedAt = syncStatusStore.getLastSuccessAt(),
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AboutSyncState(pendingCount = 0, failedCount = 0, lastSyncedAt = syncStatusStore.getLastSuccessAt()),
        )

    init {
        recheckBackend()
    }

    fun recheckBackend() {
        _backendState.value = BackendState.Checking
        viewModelScope.launch {
            _backendState.value = if (reachabilityChecker.isReachable()) {
                BackendState.Reachable
            } else {
                BackendState.Unreachable
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                AboutViewModel(
                    reachabilityChecker = app.container.reachabilityChecker,
                    wireGuardManager = app.container.wireGuardManager,
                    outboxDao = app.container.database.outboxDao(),
                    syncStatusStore = app.container.syncStatusStore,
                )
            }
        }
    }
}
