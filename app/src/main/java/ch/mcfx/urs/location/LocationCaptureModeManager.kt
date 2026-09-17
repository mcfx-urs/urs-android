package ch.mcfx.urs.location

import android.content.Context
import ch.mcfx.urs.UrsApplication

/**
 * Turns the current toggle/sub-state combination (see
 * [LocationHistorySettingsStore]) into one effective capture interval and
 * applies it via the existing [LocationCaptureScheduler] — GitHub issue
 * #60. Call [applyEffectiveCapture] from every signal that could change the
 * outcome: a toggle flipping, a geofence transition, an activity-state
 * change, or the worker's own settle-check.
 *
 * Precedence: STILL (if the activity toggle is on) pauses/throttles
 * regardless of geofence state; otherwise the geofence toggle's dense/
 * sparse sub-state decides; otherwise the plain fixed interval applies.
 * Geofence monitoring itself keeps running in the background (cheap,
 * Play-Services-managed) even while paused for stillness — only the app's
 * own periodic capture loop is gated here.
 */
object LocationCaptureModeManager {

    /** Null means capture should be paused entirely. */
    fun effectiveIntervalMinutes(store: LocationHistorySettingsStore): Long? {
        if (store.isActivityPauseEnabled() && store.isCurrentlyStill()) {
            val fallback = store.activityStillFallbackMinutes()
            return if (fallback <= 0L) null else fallback
        }
        if (store.isGeofenceAdaptiveEnabled()) {
            return if (store.isGeofenceDense()) store.intervalMinutes() else store.geofenceSparseIntervalMinutes()
        }
        return store.intervalMinutes()
    }

    /**
     * A short, comma-joined label for whichever sub-states are actually
     * toggled on (e.g. "moving,geofence-dense", or "fixed" when neither
     * adaptive toggle is enabled) — attached directly to capture log entries
     * (GitHub issue #86) instead of requiring the reader to cross-reference
     * the nearest earlier `INTERVAL`/`PAUSED` entry.
     */
    fun currentModeLabel(store: LocationHistorySettingsStore): String {
        val parts = buildList {
            if (store.isActivityPauseEnabled()) add(if (store.isCurrentlyStill()) "still" else "moving")
            if (store.isGeofenceAdaptiveEnabled()) add(if (store.isGeofenceDense()) "geofence-dense" else "geofence-sparse")
        }
        return parts.ifEmpty { listOf("fixed") }.joinToString(",")
    }

    fun applyEffectiveCapture(context: Context, reason: String) {
        val app = context.applicationContext as UrsApplication
        val store = app.container.locationHistorySettingsStore
        if (!store.isEnabled()) return

        val minutes = effectiveIntervalMinutes(store)
        if (minutes == null) {
            LocationCaptureScheduler.cancel(context)
            app.container.locationCaptureDebugLog.log("PAUSED", reason)
        } else {
            LocationCaptureScheduler.reschedule(context, minutes, store.isPrecisionModeEnabled())
            app.container.locationCaptureDebugLog.log("INTERVAL", "every $minutes min ($reason)")
        }
    }
}
