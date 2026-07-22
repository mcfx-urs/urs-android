package ch.mcfx.urs.watchrelay

import android.content.Context

private const val PREFS_NAME = "watch_relay_prefs"
private const val KEY_ENABLED = "enabled"

/** Same plain-SharedPreferences pattern as [ch.mcfx.urs.location.LocationHistorySettingsStore]. */
class WatchRelaySettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
