package ch.mcfx.urs.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ch.mcfx.urs.UrsApplication
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import kotlinx.coroutines.launch

// Below this confidence (0-100), the classification is too uncertain to act on.
private const val STILL_CONFIDENCE_THRESHOLD = 75

// A still/not-still flip only commits once this many consecutive polls agree
// with it, instead of acting on the very first one — absorbs single-poll
// noise (raw STILL confidence bouncing just above/below the threshold, or a
// stray TILTING/"unknown" reading while genuinely parked) that otherwise
// re-triggers the full capture interval on every poll in both directions.
// See urs/LOCATION-CAPTURE-LOG-ANALYSIS.md: a real-world log showed a
// 4h07m stationary stretch (GPS fixes confined to ~50m) flapping between
// the 30-min "still" interval and the 1-min default at least 19 times.
private const val STILL_CONFIRM_POLLS = 3

/**
 * Fired periodically by Play Services while [LocationActivityRecognitionManager]
 * is registered (GitHub issue #60). Every callback is logged as a heartbeat
 * (`ACTIVITY_POLL`), so the current classification is always visible even
 * across a long unchanged stretch — only an actual STILL/not-STILL *change*,
 * once confirmed by [STILL_CONFIRM_POLLS] consecutive polls, additionally
 * logs `ACTIVITY_CHANGED` and re-applies the capture interval.
 */
class LocationActivityRecognitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val app = context.applicationContext as UrsApplication
        val store = app.container.locationHistorySettingsStore
        if (!store.isActivityPauseEnabled()) return

        val mostProbable = result.mostProbableActivity
        val isStillNow = mostProbable.type == DetectedActivity.STILL && mostProbable.confidence >= STILL_CONFIDENCE_THRESHOLD
        val label = activityLabel(mostProbable.type)
        val changed = isStillNow != store.isCurrentlyStill()

        val pendingResult = goAsync()
        app.container.applicationScope.launch {
            try {
                store.setLastActivity(label, mostProbable.confidence)
                app.container.locationCaptureDebugLog.log("ACTIVITY_POLL", "$label (${mostProbable.confidence}%)")
                if (!changed) {
                    store.setPendingStillStreak(0)
                    return@launch
                }

                val streak = store.pendingStillStreak() + 1
                if (streak < STILL_CONFIRM_POLLS) {
                    store.setPendingStillStreak(streak)
                    return@launch
                }
                store.setPendingStillStreak(0)

                store.setCurrentlyStill(isStillNow)
                app.container.locationCaptureDebugLog.log(
                    "ACTIVITY_CHANGED",
                    "$label (${mostProbable.confidence}%) -> ${if (isStillNow) "paused/throttled" else "resumed"}",
                )
                LocationCaptureModeManager.applyEffectiveCapture(context, "activity-$label")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun activityLabel(type: Int): String = when (type) {
        DetectedActivity.STILL -> "still"
        DetectedActivity.WALKING -> "walking"
        DetectedActivity.RUNNING -> "running"
        DetectedActivity.ON_BICYCLE -> "cycling"
        DetectedActivity.IN_VEHICLE -> "in vehicle"
        DetectedActivity.ON_FOOT -> "on foot"
        DetectedActivity.TILTING -> "tilting"
        else -> "unknown"
    }
}
