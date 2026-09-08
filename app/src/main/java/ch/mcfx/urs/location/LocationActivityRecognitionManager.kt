package ch.mcfx.urs.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import ch.mcfx.urs.UrsApplication
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient

private const val ACTIVITY_UPDATE_INTERVAL_MILLIS = 30_000L
private const val ACTIVITY_REQUEST_CODE = 9030

/**
 * Registers/unregisters periodic activity classification for Location
 * History's "Activity-based pause on stillness" toggle (GitHub issue #60).
 */
object LocationActivityRecognitionManager {

    @SuppressLint("MissingPermission") // caller checks ACTIVITY_RECOGNITION before starting
    fun start(context: Context) {
        val debugLog = (context.applicationContext as UrsApplication).container.locationCaptureDebugLog
        client(context).requestActivityUpdates(ACTIVITY_UPDATE_INTERVAL_MILLIS, pendingIntent(context))
            .addOnSuccessListener {
                debugLog.log("ACTIVITY_RECOGNITION_STARTED", "polling every ${ACTIVITY_UPDATE_INTERVAL_MILLIS / 1000}s")
            }
            .addOnFailureListener { e ->
                debugLog.log("ACTIVITY_RECOGNITION_ERROR", "start failed: ${e.message}")
            }
    }

    fun stop(context: Context) {
        client(context).removeActivityUpdates(pendingIntent(context))
    }

    private fun client(context: Context): ActivityRecognitionClient = ActivityRecognition.getClient(context)

    // Must be FLAG_MUTABLE, not FLAG_IMMUTABLE: Play Services fills the
    // ActivityRecognitionResult into this PendingIntent's extras before
    // broadcasting it back, which Android 12+ refuses for an immutable one
    // (confirmed 2026-09-08 via the equivalent geofencing failure — see
    // LocationGeofenceManager — activity recognition failed the same way
    // but silently, since requestActivityUpdates()'s Task result wasn't
    // being checked before this fix).
    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LocationActivityRecognitionReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            ACTIVITY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }
}
