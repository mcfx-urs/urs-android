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

    fun setEnabled(enabled: Boolean) {
        settingsStore.setEnabled(enabled)
        _enabled.value = enabled
        if (enabled) {
            LocationCaptureScheduler.reschedule(context, settingsStore.intervalMinutes())
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
            LocationCaptureScheduler.reschedule(context, minutes)
        }
    }

    fun setStationaryThresholdMeters(meters: Long) {
        settingsStore.setStationaryThresholdMeters(meters)
        _stationaryThresholdMeters.value = meters
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
