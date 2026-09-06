package ch.mcfx.urs.location

import android.content.Context

private const val PREFS_NAME = "location_history_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_INTERVAL_MINUTES = "interval_minutes"
private const val DEFAULT_INTERVAL_MINUTES = 60L
private const val KEY_STATIONARY_THRESHOLD_METERS = "stationary_threshold_meters"
private const val DEFAULT_STATIONARY_THRESHOLD_METERS = 50L
private const val KEY_PRECISION_MODE_ENABLED = "precision_mode_enabled"

private const val KEY_TRACK_COLOR_OLD = "track_color_old"
private const val KEY_TRACK_COLOR_MID = "track_color_mid"
private const val KEY_TRACK_COLOR_NEW = "track_color_new"
private const val KEY_TRACK_HALO_ENABLED = "track_halo_enabled"
private const val KEY_MUTED_MAP = "muted_map"

// Default life-map track gradient, oldest -> newest: cyan -> blue -> magenta.
// Deliberately a hue family that OSM Carto's own road/label/landuse colours
// barely use, so the track stays readable where it runs along a coloured road.
const val DEFAULT_TRACK_COLOR_OLD = 0xFF00E5FF.toInt()
const val DEFAULT_TRACK_COLOR_MID = 0xFF2962FF.toInt()
const val DEFAULT_TRACK_COLOR_NEW = 0xFFD500F9.toInt()

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

    /** Exact-alarm-based scheduling instead of WorkManager's inexact delay — off by default, see LocationCaptureScheduler. */
    fun isPrecisionModeEnabled(): Boolean = prefs.getBoolean(KEY_PRECISION_MODE_ENABLED, false)

    fun setPrecisionModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRECISION_MODE_ENABLED, enabled).apply()
    }

    /** The three life-map track gradient stops (ARGB ints), oldest to newest. */
    fun trackColors(): List<Int> = listOf(
        prefs.getInt(KEY_TRACK_COLOR_OLD, DEFAULT_TRACK_COLOR_OLD),
        prefs.getInt(KEY_TRACK_COLOR_MID, DEFAULT_TRACK_COLOR_MID),
        prefs.getInt(KEY_TRACK_COLOR_NEW, DEFAULT_TRACK_COLOR_NEW),
    )

    /** [index] 0 = oldest stop, 1 = middle, 2 = newest. */
    fun setTrackColor(index: Int, argb: Int) {
        val key = when (index) {
            0 -> KEY_TRACK_COLOR_OLD
            1 -> KEY_TRACK_COLOR_MID
            else -> KEY_TRACK_COLOR_NEW
        }
        prefs.edit().putInt(key, argb).apply()
    }

    /** A contrasting outline drawn under the track so it reads over same-coloured roads. */
    fun isTrackHaloEnabled(): Boolean = prefs.getBoolean(KEY_TRACK_HALO_ENABLED, true)

    fun setTrackHaloEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TRACK_HALO_ENABLED, enabled).apply()
    }

    /** Desaturate the base map tiles so any track colour stands out — toggled on the Life Map screen itself. */
    fun isMutedMap(): Boolean = prefs.getBoolean(KEY_MUTED_MAP, false)

    fun setMutedMap(muted: Boolean) {
        prefs.edit().putBoolean(KEY_MUTED_MAP, muted).apply()
    }
}
