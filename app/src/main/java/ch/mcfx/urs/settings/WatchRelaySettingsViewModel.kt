package ch.mcfx.urs.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.watchrelay.WatchRelayService
import ch.mcfx.urs.watchrelay.WatchRelaySettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WatchRelaySettingsViewModel(
    private val context: Context,
    private val settingsStore: WatchRelaySettingsStore,
) : ViewModel() {

    private val _enabled = MutableStateFlow(settingsStore.isEnabled())
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        settingsStore.setEnabled(enabled)
        _enabled.value = enabled
        if (enabled) {
            WatchRelayService.start(context)
        } else {
            WatchRelayService.stop(context)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                WatchRelaySettingsViewModel(app, app.container.watchRelaySettingsStore)
            }
        }
    }
}
