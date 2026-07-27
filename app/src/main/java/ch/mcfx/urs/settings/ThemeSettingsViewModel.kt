package ch.mcfx.urs.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.ui.theme.ThemePreference
import kotlinx.coroutines.flow.StateFlow

class ThemeSettingsViewModel(private val store: ThemeSettingsStore) : ViewModel() {

    val themePreference: StateFlow<ThemePreference> = store.themePreference

    fun setThemePreference(preference: ThemePreference) {
        store.setThemePreference(preference)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as UrsApplication
                ThemeSettingsViewModel(app.container.themeSettingsStore)
            }
        }
    }
}
