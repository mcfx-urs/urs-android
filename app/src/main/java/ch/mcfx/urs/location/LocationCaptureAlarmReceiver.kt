package ch.mcfx.urs.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import ch.mcfx.urs.UrsApplication

/**
 * Fired by AlarmManager at the exact scheduled time (Precision mode).
 * Enqueues an immediate zero-delay [LocationCaptureWorker] run — reusing its
 * existing capture/dedup/outbox logic unchanged, only the trigger mechanism
 * differs from the non-precision self-chaining path — then arms the next
 * exact alarm itself, mirroring [ch.mcfx.urs.notifications.ReminderScheduler]'s
 * armNext pattern. No goAsync() needed here, unlike
 * [ch.mcfx.urs.notifications.ReminderAlarmReceiver]: both calls below are
 * synchronous (WorkManager enqueue, AlarmManager set), nothing suspends.
 * APPEND_OR_REPLACE, not REPLACE: a short precision-mode interval could fire
 * this again while the previous capture (up to the 10s GPS timeout) is still
 * in flight under the same WORK_NAME — see LocationCaptureWorker's own doc
 * comment for why REPLACE would silently cancel that still-running capture.
 */
class LocationCaptureAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as UrsApplication
        val store = app.container.locationHistorySettingsStore
        if (!store.isEnabled() || !store.isPrecisionModeEnabled()) return

        WorkManager.getInstance(context).enqueueUniqueWork(
            LocationCaptureScheduler.WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<LocationCaptureWorker>().build(),
        )
        // Effective interval (GitHub issue #60), not necessarily the plain
        // Settings value — null means the adaptive toggles currently want
        // capture paused, in which case the chain simply isn't re-armed
        // (whatever triggered the pause already cancelled it via
        // LocationCaptureModeManager; re-arming here would undo that).
        LocationCaptureModeManager.effectiveIntervalMinutes(store)?.let {
            LocationCaptureScheduler.armExact(context, it)
        }
    }
}
