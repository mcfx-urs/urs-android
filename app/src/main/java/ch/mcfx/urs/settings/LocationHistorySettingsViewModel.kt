package ch.mcfx.urs.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.location.LocationCaptureScheduler
import ch.mcfx.urs.location.LocationHistorySettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocationHistorySettingsViewModel(
    private val context: Context,
    private val settingsStore: LocationHistorySettingsStore,
) : ViewModel() {

    private val _enabled = MutableStateFlow(settingsStore.isEnabled())
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _intervalMinutes = MutableStateFlow(settingsStore.intervalMinutes())
    val intervalMinutes: StateFlow<Long> = _intervalMinutes.asStateFlow()

    private val _stationaryThresholdMeters = MutableStateFlow(settingsStore.stationaryThresholdMeters())
    val stationaryThresholdMeters: StateFlow<Long> = _stationaryThresholdMeters.asStateFlow()

    private val _precisionModeEnabled = MutableStateFlow(settingsStore.isPrecisionModeEnabled())
    val precisionModeEnabled: StateFlow<Boolean> = _precisionModeEnabled.asStateFlow()

    private val _trackColors = MutableStateFlow(settingsStore.trackColors())
    val trackColors: StateFlow<List<Int>> = _trackColors.asStateFlow()

    private val _trackHaloEnabled = MutableStateFlow(settingsStore.isTrackHaloEnabled())
    val trackHaloEnabled: StateFlow<Boolean> = _trackHaloEnabled.asStateFlow()

    fun canScheduleExactAlarms(): Boolean = LocationCaptureScheduler.canScheduleExactAlarms(context)

    fun setEnabled(enabled: Boolean) {
        settingsStore.setEnabled(enabled)
        _enabled.value = enabled
        if (enabled) {
            LocationCaptureScheduler.reschedule(context, settingsStore.intervalMinutes(), settingsStore.isPrecisionModeEnabled())
        } else {
            LocationCaptureScheduler.cancel(context)
        }
    }

    fun setIntervalMinutes(minutes: Long) {
        settingsStore.setIntervalMinutes(minutes)
        _intervalMinutes.value = minutes
        // Only re-arms if capture is already running — picking an interval
        // while disabled just changes what setEnabled(true) will use later.
        if (settingsStore.isEnabled()) {
            LocationCaptureScheduler.reschedule(context, minutes, settingsStore.isPrecisionModeEnabled())
        }
    }

    fun setStationaryThresholdMeters(meters: Long) {
        settingsStore.setStationaryThresholdMeters(meters)
        _stationaryThresholdMeters.value = meters
    }

    fun setPrecisionModeEnabled(enabled: Boolean) {
        settingsStore.setPrecisionModeEnabled(enabled)
        _precisionModeEnabled.value = enabled
        // Same only-if-running rationale as setIntervalMinutes above.
        if (settingsStore.isEnabled()) {
            LocationCaptureScheduler.reschedule(context, settingsStore.intervalMinutes(), enabled)
        }
    }

    /** [index] 0 = oldest gradient stop, 1 = middle, 2 = newest. */
    fun setTrackColor(index: Int, argb: Int) {
        settingsStore.setTrackColor(index, argb)
        _trackColors.value = settingsStore.trackColors()
    }

    fun setTrackHaloEnabled(enabled: Boolean) {
        settingsStore.setTrackHaloEnabled(enabled)
        _trackHaloEnabled.value = enabled
    }

    /**
     * Called on every screen resume while precision mode is on: armExact()
     * silently no-ops without the exact-alarm permission (see its own doc
     * comment), so turning the toggle on before granting that permission
     * otherwise leaves nothing actually scheduled. This re-arms once the
     * user comes back from granting it — safe to call redundantly (matches
     * UrsApplication's own every-process-start re-arm), since reschedule()
     * is idempotent.
     */
    fun rearmIfNeeded() {
        if (settingsStore.isEnabled() && settingsStore.isPrecisionModeEnabled()) {
            LocationCaptureScheduler.reschedule(context, settingsStore.intervalMinutes(), precisionModeEnabled = true)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                LocationHistorySettingsViewModel(app, app.container.locationHistorySettingsStore)
            }
        }
    }
}
