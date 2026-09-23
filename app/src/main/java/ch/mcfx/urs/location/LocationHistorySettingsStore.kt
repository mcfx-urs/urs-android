package ch.mcfx.urs.location

import android.content.Context

private const val PREFS_NAME = "location_history_prefs"
private const val KEY_ENABLED = "enabled"
private const val KEY_INTERVAL_MINUTES = "interval_minutes"
private const val DEFAULT_INTERVAL_MINUTES = 60L
private const val KEY_STATIONARY_THRESHOLD_METERS = "stationary_threshold_meters"
private const val DEFAULT_STATIONARY_THRESHOLD_METERS = 50L
private const val KEY_PRECISION_MODE_ENABLED = "precision_mode_enabled"
// Stamped by whichever LocationCaptureScheduler call actually arms a run
// (GitHub issue #86, fixed for interval switches in #88) with that call's
// own trigger time — read back by the run it produces to log how far its
// actual start drifted from what was scheduled. Recomputing this from the
// *current* interval at drift-calc time (the original #86 approach) went
// wrong whenever the effective interval changed between scheduling and
// firing, e.g. an activity-based interval switching back before the run
// fires — the interval in force at read time no longer matched the one
// actually used to schedule the gap.
private const val KEY_NEXT_SCHEDULED_FOR_MILLIS = "next_scheduled_for_millis"

// Adaptive-interval toggles (GitHub issue #60) — see LocationCaptureModeManager
// for how these combine into one effective interval.
private const val KEY_GEOFENCE_ADAPTIVE_ENABLED = "geofence_adaptive_enabled"
private const val KEY_GEOFENCE_RADIUS_METERS = "geofence_radius_meters"
private const val DEFAULT_GEOFENCE_RADIUS_METERS = 150L
private const val KEY_GEOFENCE_SPARSE_INTERVAL_MINUTES = "geofence_sparse_interval_minutes"
private const val DEFAULT_GEOFENCE_SPARSE_INTERVAL_MINUTES = 10L
// Sub-state: whether we're currently outside the armed circle (dense
// capture) or settled inside one (sparse). Persisted so a process restart
// doesn't lose track of which tier is active.
private const val KEY_GEOFENCE_IS_DENSE = "geofence_is_dense"
// Stamped by setGeofenceDense() whenever the tier actually flips — lets the
// UI show "dense/sparse since HH:mm" (owner follow-up to GitHub issue #60)
// without a DB query. 0L means never flipped.
private const val KEY_GEOFENCE_IS_DENSE_SINCE = "geofence_is_dense_since"
private const val KEY_GEOFENCE_CENTER_LAT = "geofence_center_lat"
private const val KEY_GEOFENCE_CENTER_LON = "geofence_center_lon"

private const val KEY_ACTIVITY_PAUSE_ENABLED = "activity_pause_enabled"
private const val KEY_ACTIVITY_STILL_FALLBACK_MINUTES = "activity_still_fallback_minutes"
private const val DEFAULT_ACTIVITY_STILL_FALLBACK_MINUTES = 0L
// Sub-state: the last activity-recognition result classified as STILL or
// not, purely to detect a *change* rather than acting on every callback.
private const val KEY_ACTIVITY_IS_STILL = "activity_is_still"
// Stamped by setCurrentlyStill() whenever it actually flips (owner
// follow-up to GitHub issue #60) — 0L means never flipped.
private const val KEY_ACTIVITY_IS_STILL_SINCE = "activity_is_still_since"
// Consecutive-poll counter for the still/not-still debounce in
// LocationActivityRecognitionReceiver — how many polls in a row have
// disagreed with the currently committed isCurrentlyStill() value. Reset to
// 0 once a poll agrees with the committed value again, or once the streak
// reaches STILL_CONFIRM_POLLS and the flip actually commits.
private const val KEY_ACTIVITY_PENDING_STILL_STREAK = "activity_pending_still_streak"
// The raw classification behind isCurrentlyStill(), refreshed on every poll
// (not just on a STILL/not-STILL flip) so "what is it seeing right now" is
// always current, e.g. "walking (82%)" rather than just a still/not-still bit.
private const val KEY_ACTIVITY_LAST_LABEL = "activity_last_label"
private const val KEY_ACTIVITY_LAST_CONFIDENCE = "activity_last_confidence"

private const val KEY_TRACK_COLOR_OLD = "track_color_old"
private const val KEY_TRACK_COLOR_MID = "track_color_mid"
private const val KEY_TRACK_COLOR_NEW = "track_color_new"
private const val KEY_TRACK_HALO_ENABLED = "track_halo_enabled"
private const val KEY_MUTED_MAP = "muted_map"
private const val KEY_GRADIENT_MODE = "gradient_mode"

// Default life-map track gradient, oldest -> newest: cyan -> blue -> magenta.
// Deliberately a hue family that OSM Carto's own road/label/landuse colours
// barely use, so the track stays readable where it runs along a coloured road.
const val DEFAULT_TRACK_COLOR_OLD = 0xFF00E5FF.toInt()
const val DEFAULT_TRACK_COLOR_MID = 0xFF2962FF.toInt()
const val DEFAULT_TRACK_COLOR_NEW = 0xFFD500F9.toInt()

/** Alternative to blending between hues (GitHub issue #66): vary one colour's brightness along the track instead. */
enum class GradientMode { HUE, INTENSITY }

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

    /** 0L means no run has ever been scheduled yet (first run since install/data-clear). */
    fun nextScheduledForMillis(): Long = prefs.getLong(KEY_NEXT_SCHEDULED_FOR_MILLIS, 0L)

    fun setNextScheduledForMillis(millis: Long) {
        prefs.edit().putLong(KEY_NEXT_SCHEDULED_FOR_MILLIS, millis).apply()
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

    /** Whether the track gradient blends between the three stops below (HUE) or varies one colour's brightness (INTENSITY). */
    fun gradientMode(): GradientMode =
        if (prefs.getString(KEY_GRADIENT_MODE, null) == GradientMode.INTENSITY.name) GradientMode.INTENSITY else GradientMode.HUE

    fun setGradientMode(mode: GradientMode) {
        prefs.edit().putString(KEY_GRADIENT_MODE, mode.name).apply()
    }

    /** Desaturate the base map tiles so any track colour stands out — toggled on the Life Map screen itself. */
    fun isMutedMap(): Boolean = prefs.getBoolean(KEY_MUTED_MAP, false)

    fun setMutedMap(muted: Boolean) {
        prefs.edit().putBoolean(KEY_MUTED_MAP, muted).apply()
    }

    // --- adaptive interval (GitHub issue #60) ---------------------------

    fun isGeofenceAdaptiveEnabled(): Boolean = prefs.getBoolean(KEY_GEOFENCE_ADAPTIVE_ENABLED, false)

    fun setGeofenceAdaptiveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_GEOFENCE_ADAPTIVE_ENABLED, enabled).apply()
    }

    fun geofenceRadiusMeters(): Long = prefs.getLong(KEY_GEOFENCE_RADIUS_METERS, DEFAULT_GEOFENCE_RADIUS_METERS)

    fun setGeofenceRadiusMeters(meters: Long) {
        prefs.edit().putLong(KEY_GEOFENCE_RADIUS_METERS, meters).apply()
    }

    fun geofenceSparseIntervalMinutes(): Long =
        prefs.getLong(KEY_GEOFENCE_SPARSE_INTERVAL_MINUTES, DEFAULT_GEOFENCE_SPARSE_INTERVAL_MINUTES)

    fun setGeofenceSparseIntervalMinutes(minutes: Long) {
        prefs.edit().putLong(KEY_GEOFENCE_SPARSE_INTERVAL_MINUTES, minutes).apply()
    }

    fun isGeofenceDense(): Boolean = prefs.getBoolean(KEY_GEOFENCE_IS_DENSE, false)

    fun setGeofenceDense(dense: Boolean) {
        val editor = prefs.edit().putBoolean(KEY_GEOFENCE_IS_DENSE, dense)
        if (dense != isGeofenceDense()) editor.putLong(KEY_GEOFENCE_IS_DENSE_SINCE, System.currentTimeMillis())
        editor.apply()
    }

    /** 0L until the dense/sparse tier has flipped at least once. */
    fun geofenceDenseSinceMillis(): Long = prefs.getLong(KEY_GEOFENCE_IS_DENSE_SINCE, 0L)

    /** Null until a geofence has been armed at least once. */
    fun geofenceCenter(): Pair<Double, Double>? {
        if (!prefs.contains(KEY_GEOFENCE_CENTER_LAT)) return null
        val lat = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_GEOFENCE_CENTER_LAT, 0L))
        val lon = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_GEOFENCE_CENTER_LON, 0L))
        return lat to lon
    }

    fun setGeofenceCenter(latitude: Double, longitude: Double) {
        prefs.edit()
            .putLong(KEY_GEOFENCE_CENTER_LAT, java.lang.Double.doubleToRawLongBits(latitude))
            .putLong(KEY_GEOFENCE_CENTER_LON, java.lang.Double.doubleToRawLongBits(longitude))
            .apply()
    }

    fun isActivityPauseEnabled(): Boolean = prefs.getBoolean(KEY_ACTIVITY_PAUSE_ENABLED, false)

    fun setActivityPauseEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ACTIVITY_PAUSE_ENABLED, enabled).apply()
    }

    /** 0 pauses capture entirely while STILL; otherwise the fallback interval in minutes. */
    fun activityStillFallbackMinutes(): Long =
        prefs.getLong(KEY_ACTIVITY_STILL_FALLBACK_MINUTES, DEFAULT_ACTIVITY_STILL_FALLBACK_MINUTES)

    fun setActivityStillFallbackMinutes(minutes: Long) {
        prefs.edit().putLong(KEY_ACTIVITY_STILL_FALLBACK_MINUTES, minutes).apply()
    }

    fun isCurrentlyStill(): Boolean = prefs.getBoolean(KEY_ACTIVITY_IS_STILL, false)

    fun setCurrentlyStill(still: Boolean) {
        val editor = prefs.edit().putBoolean(KEY_ACTIVITY_IS_STILL, still)
        if (still != isCurrentlyStill()) editor.putLong(KEY_ACTIVITY_IS_STILL_SINCE, System.currentTimeMillis())
        editor.apply()
    }

    /** 0L until the still/not-still state has flipped at least once. */
    fun activityStillSinceMillis(): Long = prefs.getLong(KEY_ACTIVITY_IS_STILL_SINCE, 0L)

    fun pendingStillStreak(): Int = prefs.getInt(KEY_ACTIVITY_PENDING_STILL_STREAK, 0)

    fun setPendingStillStreak(streak: Int) {
        prefs.edit().putInt(KEY_ACTIVITY_PENDING_STILL_STREAK, streak).apply()
    }

    /** Null until the first activity-recognition callback has been received. */
    fun lastActivityLabel(): String? = prefs.getString(KEY_ACTIVITY_LAST_LABEL, null)

    fun lastActivityConfidence(): Int = prefs.getInt(KEY_ACTIVITY_LAST_CONFIDENCE, 0)

    fun setLastActivity(label: String, confidencePercent: Int) {
        prefs.edit()
            .putString(KEY_ACTIVITY_LAST_LABEL, label)
            .putInt(KEY_ACTIVITY_LAST_CONFIDENCE, confidencePercent)
            .apply()
    }
}
