package ch.mcfx.urs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.R
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.local.OutboxDao
import ch.mcfx.urs.data.local.OutboxMutationEntity
import ch.mcfx.urs.data.local.OutboxStatus
import ch.mcfx.urs.data.sync.ReachabilityChecker
import ch.mcfx.urs.data.sync.SyncManager
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

/** One queued outbox mutation, resolved for display in the sync-details sheet. `verbRes`/`domainRes` are `R.string` ids. */
data class OutboxEntryUi(
    val id: Long,
    val verbRes: Int,
    val domainRes: Int,
    val status: OutboxStatus,
    val lastError: String?,
    val retryCount: Int,
    val createdAt: Long,
)

sealed interface BackendState {
    data object Checking : BackendState
    data object Reachable : BackendState
    data object Unreachable : BackendState
}

/**
 * Backs the About screen's "State" section — observation of existing
 * sync/backend/VPN state, plus the two explicit, user-triggered actions it
 * offers: [recheckBackend] and [syncNow]. It never triggers a sync or a
 * backend call on its own; both only run in response to a tap.
 */
class AboutViewModel(
    private val reachabilityChecker: ReachabilityChecker,
    private val syncManager: SyncManager,
    val wireGuardManager: WireGuardManager,
    outboxDao: OutboxDao,
    syncStatusStore: SyncStatusStore,
) : ViewModel() {

    val vpnState: StateFlow<VpnConnectionState> get() = wireGuardManager.state

    private val _backendState = MutableStateFlow<BackendState>(BackendState.Checking)
    val backendState: StateFlow<BackendState> = _backendState.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

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

    // observeAll() only ever holds not-yet-confirmed writes (a successful
    // replay deletes its row), so every entry here is genuinely pending,
    // syncing, or failed — no extra filtering needed. Order is the DAO's
    // own createdAt ASC, i.e. the FIFO order they'll actually replay in.
    val outboxEntries: StateFlow<List<OutboxEntryUi>> = outboxDao.observeAll()
        .map { mutations -> mutations.map { it.toEntryUi() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /** Immediate, user-initiated outbox replay — mirrors the Fuel hub's "sync now" tile. No-ops while one is already running. */
    fun syncNow() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                syncManager.syncNow()
            } finally {
                _isSyncing.value = false
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                AboutViewModel(
                    reachabilityChecker = app.container.reachabilityChecker,
                    syncManager = app.container.syncManager,
                    wireGuardManager = app.container.wireGuardManager,
                    outboxDao = app.container.database.outboxDao(),
                    syncStatusStore = app.container.syncStatusStore,
                )
            }
        }
    }
}

private fun OutboxMutationEntity.toEntryUi(): OutboxEntryUi {
    val (verbRes, domainRes) = outboxLabelRes(type)
    return OutboxEntryUi(
        id = id,
        verbRes = verbRes,
        domainRes = domainRes,
        status = status,
        lastError = lastError,
        retryCount = retryCount,
        createdAt = createdAt,
    )
}

private fun outboxLabelRes(type: String): Pair<Int, Int> = when (type) {
    OutboxMutationEntity.TYPE_CREATE_FILL -> R.string.outbox_verb_create to R.string.outbox_domain_fill
    OutboxMutationEntity.TYPE_UPDATE_FILL -> R.string.outbox_verb_update to R.string.outbox_domain_fill
    OutboxMutationEntity.TYPE_DELETE_FILL -> R.string.outbox_verb_delete to R.string.outbox_domain_fill
    OutboxMutationEntity.TYPE_CREATE_WORK_TIME_ENTRY -> R.string.outbox_verb_create to R.string.outbox_domain_work_time
    OutboxMutationEntity.TYPE_UPDATE_WORK_TIME_ENTRY -> R.string.outbox_verb_update to R.string.outbox_domain_work_time
    OutboxMutationEntity.TYPE_DELETE_WORK_TIME_ENTRY -> R.string.outbox_verb_delete to R.string.outbox_domain_work_time
    OutboxMutationEntity.TYPE_CREATE_INVENTORY -> R.string.outbox_verb_create to R.string.outbox_domain_inventory
    OutboxMutationEntity.TYPE_UPDATE_INVENTORY -> R.string.outbox_verb_update to R.string.outbox_domain_inventory
    OutboxMutationEntity.TYPE_DELETE_INVENTORY -> R.string.outbox_verb_delete to R.string.outbox_domain_inventory
    OutboxMutationEntity.TYPE_CREATE_INVENTORY_PRODUCT -> R.string.outbox_verb_create to R.string.outbox_domain_inventory_product
    OutboxMutationEntity.TYPE_CREATE_LIST -> R.string.outbox_verb_create to R.string.outbox_domain_list
    OutboxMutationEntity.TYPE_UPDATE_LIST -> R.string.outbox_verb_update to R.string.outbox_domain_list
    OutboxMutationEntity.TYPE_DELETE_LIST -> R.string.outbox_verb_delete to R.string.outbox_domain_list
    OutboxMutationEntity.TYPE_CREATE_LIST_ITEM -> R.string.outbox_verb_create to R.string.outbox_domain_list_item
    OutboxMutationEntity.TYPE_UPDATE_LIST_ITEM -> R.string.outbox_verb_update to R.string.outbox_domain_list_item
    OutboxMutationEntity.TYPE_DELETE_LIST_ITEM -> R.string.outbox_verb_delete to R.string.outbox_domain_list_item
    OutboxMutationEntity.TYPE_CREATE_LOCATION_HISTORY -> R.string.outbox_verb_create to R.string.outbox_domain_location_history
    OutboxMutationEntity.TYPE_CREATE_VEHICLE_SERVICE -> R.string.outbox_verb_create to R.string.outbox_domain_vehicle_service
    OutboxMutationEntity.TYPE_UPDATE_VEHICLE_SERVICE -> R.string.outbox_verb_update to R.string.outbox_domain_vehicle_service
    OutboxMutationEntity.TYPE_DELETE_VEHICLE_SERVICE -> R.string.outbox_verb_delete to R.string.outbox_domain_vehicle_service
    OutboxMutationEntity.TYPE_CREATE_BAKE_PLAN -> R.string.outbox_verb_create to R.string.outbox_domain_bake_plan
    OutboxMutationEntity.TYPE_UPDATE_BAKE_PLAN_STEP -> R.string.outbox_verb_update to R.string.outbox_domain_bake_plan_step
    OutboxMutationEntity.TYPE_CANCEL_BAKE_PLAN -> R.string.outbox_verb_cancel to R.string.outbox_domain_bake_plan
    OutboxMutationEntity.TYPE_CREATE_NOTE -> R.string.outbox_verb_create to R.string.outbox_domain_note
    OutboxMutationEntity.TYPE_UPDATE_NOTE -> R.string.outbox_verb_update to R.string.outbox_domain_note
    OutboxMutationEntity.TYPE_UPDATE_NOTE_STATUS -> R.string.outbox_verb_update to R.string.outbox_domain_note_status
    OutboxMutationEntity.TYPE_DELETE_NOTE -> R.string.outbox_verb_delete to R.string.outbox_domain_note
    else -> R.string.outbox_verb_update to R.string.outbox_domain_unknown
}
