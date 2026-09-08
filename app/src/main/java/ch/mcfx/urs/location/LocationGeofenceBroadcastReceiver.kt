package ch.mcfx.urs.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ch.mcfx.urs.UrsApplication
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.launch

/**
 * Fired by Play Services when the circle armed by [LocationGeofenceManager]
 * is exited (GitHub issue #60) — a real displacement signal, unlike an
 * activity-type classification. Switches to dense capture; the return to
 * sparse happens separately via [LocationCaptureWorker]'s settle-check.
 */
class LocationGeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        val app = context.applicationContext as UrsApplication
        val store = app.container.locationHistorySettingsStore
        if (!store.isGeofenceAdaptiveEnabled()) return

        if (event.hasError()) {
            app.container.locationCaptureDebugLog.log("GEOFENCE_ERROR", "transition error code ${event.errorCode}")
            return
        }
        if (event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_EXIT) return
        if (store.isGeofenceDense()) return // already dense, e.g. a duplicate callback

        val pendingResult = goAsync()
        app.container.applicationScope.launch {
            try {
                store.setGeofenceDense(true)
                app.container.locationCaptureDebugLog.log(
                    "GEOFENCE_EXIT",
                    "left the ${store.geofenceRadiusMeters()} m circle, switching to dense capture",
                )
                LocationCaptureModeManager.applyEffectiveCapture(context, "geofence-exit")
                LocationGeofenceManager.disarm(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
