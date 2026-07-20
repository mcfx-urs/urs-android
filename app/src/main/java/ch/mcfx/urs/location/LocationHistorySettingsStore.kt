package ch.mcfx.urs.location

import android.content.Context

private const val PREFS_NAME = "location_history_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_INTERVAL_MINUTES = "interval_minutes"
private const val DEFAULT_INTERVAL_MINUTES = 60L

/**
 * Two-scalar settings store for the life map's periodic capture (enabled +
 * interval) — same plain-SharedPreferences approach as
 * [ch.mcfx.urs.notifications.ReminderStore], minus that store's JSON/
 * kotlinx-serialization machinery, since there's nothing structured to
 * persist here.
 */
class LocationHistorySettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun intervalMinutes(): Long = prefs.getLong(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)

    fun setIntervalMinutes(minutes: Long) {
        prefs.edit().putLong(KEY_INTERVAL_MINUTES, minutes).apply()
    }
}
