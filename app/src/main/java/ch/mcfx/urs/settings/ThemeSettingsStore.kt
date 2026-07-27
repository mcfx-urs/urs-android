package ch.mcfx.urs.settings

import android.content.Context
import ch.mcfx.urs.ui.theme.ThemePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "theme_prefs"
private const val KEY_THEME_PREFERENCE = "theme_preference"

/**
 * Same plain-SharedPreferences pattern as [ch.mcfx.urs.watchrelay.WatchRelaySettingsStore],
 * but also exposes the current value as a [StateFlow]: [ch.mcfx.urs.MainActivity]'s root
 * `UrsTheme{}` call needs to react live when this changes on the settings screen, and it
 * sits above any per-destination ViewModel scope, so a directly observable flow here is
 * simpler than routing the update through one.
 */
class ThemeSettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themePreference = MutableStateFlow(readPersisted())
    val themePreference: StateFlow<ThemePreference> = _themePreference.asStateFlow()

    fun setThemePreference(preference: ThemePreference) {
        prefs.edit().putString(KEY_THEME_PREFERENCE, preference.name).apply()
        _themePreference.value = preference
    }

    private fun readPersisted(): ThemePreference =
        prefs.getString(KEY_THEME_PREFERENCE, null)
            ?.let { stored -> runCatching { ThemePreference.valueOf(stored) }.getOrNull() }
            ?: ThemePreference.SYSTEM
}
