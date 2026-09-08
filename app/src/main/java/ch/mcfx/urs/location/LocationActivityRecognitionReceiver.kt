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

/**
 * Fired periodically by Play Services while [LocationActivityRecognitionManager]
 * is registered (GitHub issue #60). Every callback is logged as a heartbeat
 * (`ACTIVITY_POLL`), so the current classification is always visible even
 * across a long unchanged stretch — only an actual STILL/not-STILL *change*
 * additionally logs `ACTIVITY_CHANGED` and re-applies the capture interval.
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
                if (!changed) return@launch

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
