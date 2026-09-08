package ch.mcfx.urs.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import ch.mcfx.urs.UrsApplication
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

private const val GEOFENCE_ID = "life-map-geofence"
private const val GEOFENCE_REQUEST_CODE = 9020

/**
 * Arms/disarms the single circle Location History's "Geofence-based
 * adaptive interval" toggle (GitHub issue #60) uses to detect real
 * displacement. Only the EXIT transition is requested — the return to
 * sparse capture happens via [LocationCaptureWorker]'s own settle-check
 * re-arming a fresh circle at the new position, not a re-ENTER of this one.
 */
object LocationGeofenceManager {

    @SuppressLint("MissingPermission") // caller checks location permission before arming
    fun arm(context: Context, latitude: Double, longitude: Double, radiusMeters: Float) {
        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(latitude, longitude, radiusMeters)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()
        // Logged here (from the actual Play Services callback) rather than
        // by every caller, so "was this circle really accepted" reflects
        // ground truth instead of just "we asked to arm one" — a caller can
        // still throw SecurityException synchronously before this Task is
        // even created, which is why call sites also log their own skip
        // reason in that case.
        val debugLog = (context.applicationContext as UrsApplication).container.locationCaptureDebugLog
        client(context).addGeofences(request, pendingIntent(context))
            .addOnSuccessListener {
                debugLog.log("GEOFENCE_ARMED", "circle at %.5f, %.5f, radius %d m".format(latitude, longitude, radiusMeters.toInt()))
            }
            .addOnFailureListener { e ->
                debugLog.log("GEOFENCE_ERROR", "arm failed: ${e.message}")
            }
    }

    fun disarm(context: Context) {
        client(context).removeGeofences(pendingIntent(context))
    }

    private fun client(context: Context): GeofencingClient = LocationServices.getGeofencingClient(context)

    // Must be FLAG_MUTABLE: Play Services fills the GeofencingEvent into
    // this PendingIntent's extras before broadcasting it back, which
    // Android 12+ refuses for an immutable one — surfaced 2026-09-08 as
    // "GEOFENCE_ERROR arm failed: 10: PendingIntent must be mutable" in the
    // capture log, i.e. every arm() call had been failing outright.
    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LocationGeofenceBroadcastReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            GEOFENCE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }
}
