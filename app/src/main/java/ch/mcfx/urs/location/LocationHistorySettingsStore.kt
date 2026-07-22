package ch.mcfx.urs.location

import android.content.Context

private const val PREFS_NAME = "location_history_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_INTERVAL_MINUTES = "interval_minutes"
private const val DEFAULT_INTERVAL_MINUTES = 60L
private const val KEY_STATIONARY_THRESHOLD_METERS = "stationary_threshold_meters"
private const val DEFAULT_STATIONARY_THRESHOLD_METERS = 50L

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

    /** 0 disables the filter — every capture is stored regardless of distance from the last point. */
    fun stationaryThresholdMeters(): Long = prefs.getLong(KEY_STATIONARY_THRESHOLD_METERS, DEFAULT_STATIONARY_THRESHOLD_METERS)

    fun setStationaryThresholdMeters(meters: Long) {
        prefs.edit().putLong(KEY_STATIONARY_THRESHOLD_METERS, meters).apply()
    }
}
