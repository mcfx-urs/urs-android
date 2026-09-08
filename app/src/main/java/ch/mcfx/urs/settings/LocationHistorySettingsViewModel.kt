package ch.mcfx.urs.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.location.LocationActivityRecognitionManager
import ch.mcfx.urs.location.LocationCapture
import ch.mcfx.urs.location.LocationCaptureModeManager
import ch.mcfx.urs.location.LocationCaptureScheduler
import ch.mcfx.urs.location.LocationGeofenceManager
import ch.mcfx.urs.location.LocationHistorySettingsStore
import ch.mcfx.urs.location.hasActivityRecognitionPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LocationHistorySettingsViewModel(
    private val context: Context,
    private val settingsStore: LocationHistorySettingsStore,
    private val locationCapture: LocationCapture,
) : ViewModel() {

    private val debugLog = (context.applicationContext as UrsApplication).container.locationCaptureDebugLog

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

    private val _geofenceAdaptiveEnabled = MutableStateFlow(settingsStore.isGeofenceAdaptiveEnabled())
    val geofenceAdaptiveEnabled: StateFlow<Boolean> = _geofenceAdaptiveEnabled.asStateFlow()

    private val _geofenceRadiusMeters = MutableStateFlow(settingsStore.geofenceRadiusMeters())
    val geofenceRadiusMeters: StateFlow<Long> = _geofenceRadiusMeters.asStateFlow()

    private val _geofenceSparseIntervalMinutes = MutableStateFlow(settingsStore.geofenceSparseIntervalMinutes())
    val geofenceSparseIntervalMinutes: StateFlow<Long> = _geofenceSparseIntervalMinutes.asStateFlow()

    private val _activityPauseEnabled = MutableStateFlow(settingsStore.isActivityPauseEnabled())
    val activityPauseEnabled: StateFlow<Boolean> = _activityPauseEnabled.asStateFlow()

    private val _activityStillFallbackMinutes = MutableStateFlow(settingsStore.activityStillFallbackMinutes())
    val activityStillFallbackMinutes: StateFlow<Long> = _activityStillFallbackMinutes.asStateFlow()

    fun canScheduleExactAlarms(): Boolean = LocationCaptureScheduler.canScheduleExactAlarms(context)

    fun hasActivityRecognitionPermission(): Boolean = hasActivityRecognitionPermission(context)

    fun setEnabled(enabled: Boolean) {
        settingsStore.setEnabled(enabled)
        _enabled.value = enabled
        if (enabled) {
            LocationCaptureModeManager.applyEffectiveCapture(context, "enabled")
        } else {
            LocationCaptureScheduler.cancel(context)
            LocationGeofenceManager.disarm(context)
            LocationActivityRecognitionManager.stop(context)
        }
    }

    fun setIntervalMinutes(minutes: Long) {
        settingsStore.setIntervalMinutes(minutes)
        _intervalMinutes.value = minutes
        // Only re-arms if capture is already running — picking an interval
        // while disabled just changes what setEnabled(true) will use later.
        if (settingsStore.isEnabled()) {
            LocationCaptureModeManager.applyEffectiveCapture(context, "interval-changed")
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
            LocationCaptureModeManager.applyEffectiveCapture(context, "precision-mode-changed")
        }
    }

    /**
     * Turning this on grabs one fix to center the first circle around — a
     * missing/denied location permission just leaves the toggle on with no
     * circle armed yet (GitHub issue #60); it'll pick one up the next time
     * a capture happens to succeed, via [rearmIfNeeded].
     */
    fun setGeofenceAdaptiveEnabled(enabled: Boolean) {
        settingsStore.setGeofenceAdaptiveEnabled(enabled)
        _geofenceAdaptiveEnabled.value = enabled
        if (enabled) {
            settingsStore.setGeofenceDense(false)
            viewModelScope.launch {
                val location = locationCapture.captureLocation("history-geofence-center")
                if (location == null) {
                    debugLog.log("GEOFENCE_ARM_SKIPPED", "no location fix available yet")
                    return@launch
                }
                settingsStore.setGeofenceCenter(location.latitude, location.longitude)
                try {
                    LocationGeofenceManager.arm(context, location.latitude, location.longitude, settingsStore.geofenceRadiusMeters().toFloat())
                } catch (e: SecurityException) {
                    debugLog.log("GEOFENCE_ARM_SKIPPED", "location permission denied")
                    return@launch
                }
                if (settingsStore.isEnabled()) LocationCaptureModeManager.applyEffectiveCapture(context, "geofence-enabled")
            }
        } else {
            LocationGeofenceManager.disarm(context)
            if (settingsStore.isEnabled()) LocationCaptureModeManager.applyEffectiveCapture(context, "geofence-disabled")
        }
    }

    fun setGeofenceRadiusMeters(meters: Long) {
        settingsStore.setGeofenceRadiusMeters(meters)
        _geofenceRadiusMeters.value = meters
        // Re-centers the currently armed circle (if any) at its existing
        // center with the new radius — takes effect on the next exit/settle
        // otherwise, which could be a long wait.
        val (lat, lon) = settingsStore.geofenceCenter() ?: return
        if (settingsStore.isGeofenceAdaptiveEnabled() && !settingsStore.isGeofenceDense()) {
            try {
                LocationGeofenceManager.arm(context, lat, lon, meters.toFloat())
            } catch (e: SecurityException) {
                // Permission revoked since first armed — nothing to do here.
            }
        }
    }

    fun setGeofenceSparseIntervalMinutes(minutes: Long) {
        settingsStore.setGeofenceSparseIntervalMinutes(minutes)
        _geofenceSparseIntervalMinutes.value = minutes
        if (settingsStore.isEnabled() && settingsStore.isGeofenceAdaptiveEnabled()) {
            LocationCaptureModeManager.applyEffectiveCapture(context, "geofence-sparse-interval-changed")
        }
    }

    fun setActivityPauseEnabled(enabled: Boolean) {
        settingsStore.setActivityPauseEnabled(enabled)
        _activityPauseEnabled.value = enabled
        if (enabled) {
            settingsStore.setCurrentlyStill(false)
            if (hasActivityRecognitionPermission(context)) {
                LocationActivityRecognitionManager.start(context)
            } else {
                debugLog.log("ACTIVITY_START_SKIPPED", "ACTIVITY_RECOGNITION permission not granted")
            }
        } else {
            LocationActivityRecognitionManager.stop(context)
            settingsStore.setCurrentlyStill(false)
            if (settingsStore.isEnabled()) LocationCaptureModeManager.applyEffectiveCapture(context, "activity-disabled")
        }
    }

    fun setActivityStillFallbackMinutes(minutes: Long) {
        settingsStore.setActivityStillFallbackMinutes(minutes)
        _activityStillFallbackMinutes.value = minutes
        if (settingsStore.isEnabled() && settingsStore.isActivityPauseEnabled() && settingsStore.isCurrentlyStill()) {
            LocationCaptureModeManager.applyEffectiveCapture(context, "still-fallback-changed")
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
            LocationCaptureModeManager.applyEffectiveCapture(context, "resume-rearm")
        }
        // Same permission-granted-after-the-fact rationale as above, for the
        // activity-recognition toggle (GitHub issue #60): starting is
        // idempotent, safe to call redundantly on every resume.
        if (settingsStore.isActivityPauseEnabled() && hasActivityRecognitionPermission(context)) {
            LocationActivityRecognitionManager.start(context)
        }
        // The geofence toggle can end up armed with no circle yet if it was
        // turned on before location permission was granted, or the initial
        // fix failed — pick one up here once a permission/fix becomes
        // available, same idempotent-retry rationale.
        if (settingsStore.isGeofenceAdaptiveEnabled() && settingsStore.geofenceCenter() == null) {
            viewModelScope.launch {
                val location = locationCapture.captureLocation("history-geofence-center") ?: return@launch
                settingsStore.setGeofenceCenter(location.latitude, location.longitude)
                try {
                    LocationGeofenceManager.arm(context, location.latitude, location.longitude, settingsStore.geofenceRadiusMeters().toFloat())
                } catch (e: SecurityException) {
                    return@launch
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                LocationHistorySettingsViewModel(app, app.container.locationHistorySettingsStore, app.container.locationCapture)
            }
        }
    }
}
