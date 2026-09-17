package ch.mcfx.urs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.local.LocationCaptureLogDao
import ch.mcfx.urs.location.LocationHistorySettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * One row of the adaptive-interval debug trail (GitHub issue #60), read back
 * for [LocationCaptureLogScreen]. The fields below GitHub issue #86 added
 * are only populated for capture-attempt/worker-run entries — see
 * [ch.mcfx.urs.data.local.LocationCaptureLogEntity]'s own doc comment.
 */
data class LocationCaptureLogEntryUi(
    val id: Long,
    val timestampMillis: Long,
    val eventType: String,
    val detail: String,
    val mode: String?,
    val startMillis: Long?,
    val endMillis: Long?,
    val scheduledForMillis: Long?,
    val batteryPercent: Int?,
    val isCharging: Boolean?,
    val isPowerSaveMode: Boolean?,
    val isDeviceIdleMode: Boolean?,
)

/**
 * Live snapshot of the two adaptive-interval toggles' own sub-state (owner
 * follow-up to GitHub issue #60: "what activity is it seeing right now, and
 * since when has the current tier been active") — as opposed to
 * [LocationCaptureStatus] in [AboutViewModel], which is the one combined
 * effective status shown on the About screen's pill.
 */
data class LocationCaptureCurrentStateUi(
    val activityEnabled: Boolean,
    val activityLabel: String?,
    val activityConfidence: Int,
    val activityIsStill: Boolean,
    val activityStillSinceMillis: Long,
    val geofenceEnabled: Boolean,
    val geofenceIsDense: Boolean,
    val geofenceDenseSinceMillis: Long,
)

/** Backs the dedicated "Location capture log" screen — a plain, live-updating read of [LocationCaptureLogDao]. */
class LocationCaptureLogViewModel(
    private val locationCaptureLogDao: LocationCaptureLogDao,
    private val settingsStore: LocationHistorySettingsStore,
) : ViewModel() {

    val entries: StateFlow<List<LocationCaptureLogEntryUi>> = locationCaptureLogDao.observeRecent()
        .map { entries ->
            entries.map {
                LocationCaptureLogEntryUi(
                    id = it.id,
                    timestampMillis = it.timestampMillis,
                    eventType = it.eventType,
                    detail = it.detail,
                    mode = it.mode,
                    startMillis = it.startMillis,
                    endMillis = it.endMillis,
                    scheduledForMillis = it.scheduledForMillis,
                    batteryPercent = it.batteryPercent,
                    isCharging = it.isCharging,
                    isPowerSaveMode = it.isPowerSaveMode,
                    isDeviceIdleMode = it.isDeviceIdleMode,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Recomputed on every log write, same rationale as AboutViewModel's own
    // locationCaptureStatus flow: a plain SharedPreferences read otherwise
    // has nothing to observe reactively, and every store change this screen
    // cares about is followed by a log write (ACTIVITY_POLL fires on every
    // activity-recognition callback, which is also when setLastActivity()
    // and setCurrentlyStill() run).
    val currentState: StateFlow<LocationCaptureCurrentStateUi> = locationCaptureLogDao.observeRecent()
        .map { currentState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), currentState())

    /** The full retained log (not just what's displayed), oldest first — for CSV export (GitHub issue #86). */
    suspend fun exportEntries() = locationCaptureLogDao.getAllForExport()

    private fun currentState() = LocationCaptureCurrentStateUi(
        activityEnabled = settingsStore.isActivityPauseEnabled(),
        activityLabel = settingsStore.lastActivityLabel(),
        activityConfidence = settingsStore.lastActivityConfidence(),
        activityIsStill = settingsStore.isCurrentlyStill(),
        activityStillSinceMillis = settingsStore.activityStillSinceMillis(),
        geofenceEnabled = settingsStore.isGeofenceAdaptiveEnabled(),
        geofenceIsDense = settingsStore.isGeofenceDense(),
        geofenceDenseSinceMillis = settingsStore.geofenceDenseSinceMillis(),
    )

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                LocationCaptureLogViewModel(app.container.database.locationCaptureLogDao(), app.container.locationHistorySettingsStore)
            }
        }
    }
}
