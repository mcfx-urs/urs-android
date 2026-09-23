package ch.mcfx.urs.location

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ch.mcfx.urs.UrsApplication
import java.util.concurrent.TimeUnit

/**
 * Arms/re-arms/cancels [LocationCaptureWorker]'s periodic run. Deliberately
 * no [androidx.work.Constraints] here either — see the worker's own doc
 * comment, capture must keep working fully offline.
 *
 * WorkManager enforces a hard 15-minute floor on periodic work (anything
 * shorter is silently clamped up to 15 minutes), which is too coarse for
 * testing this feature. Intervals below [PERIODIC_FLOOR_MINUTES] are instead
 * driven by a self-rescheduling one-time work chain: [LocationCaptureWorker]
 * calls [scheduleNext] itself to arm its own next run. Both mechanisms
 * enqueue under the same [WORK_NAME], so switching between them (a settings
 * change crossing the 15-minute line) replaces whichever chain was
 * previously running — [reschedule] uses [ExistingWorkPolicy.REPLACE] for
 * that external-trigger case. [LocationCaptureWorker]'s own self-chaining
 * call must use [ExistingWorkPolicy.APPEND_OR_REPLACE] instead: it enqueues
 * under [WORK_NAME] while its own run under that same name is still in
 * progress, and REPLACE would cancel whatever currently holds the name —
 * including itself.
 *
 * Both of the above trade exact timing for battery efficiency: WorkManager
 * deliberately batches/defers delayed work around Doze/App Standby windows
 * instead of firing it right on schedule (confirmed on-device via
 * job-history correlation that real captures drift 70s-177s off a
 * configured 1-minute interval, at any interval, not just short/test ones).
 * [armExact] is the opt-in alternative ("Precision mode" in Settings →
 * Location history): [AlarmManager.setExactAndAllowWhileIdle], the same
 * Doze-defeating mechanism [ch.mcfx.urs.notifications.ReminderScheduler]
 * already uses for daily reminders, at the cost of a real per-trigger
 * battery wake — that trade-off is the user's call via the toggle, not
 * something to decide here.
 */
object LocationCaptureScheduler {
    const val WORK_NAME = "life-map-capture"
    const val PERIODIC_FLOOR_MINUTES = 15L
    private const val ALARM_REQUEST_CODE = 9010

    /** Single entry point for (re-)establishing scheduling in whichever mode is currently configured. */
    fun reschedule(context: Context, intervalMinutes: Long, precisionModeEnabled: Boolean) {
        cancel(context)
        when {
            precisionModeEnabled -> armExact(context, intervalMinutes)
            intervalMinutes >= PERIODIC_FLOOR_MINUTES -> {
                val request = PeriodicWorkRequestBuilder<LocationCaptureWorker>(intervalMinutes, TimeUnit.MINUTES).build()
                WorkManager.getInstance(context)
                    .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
                stampNextScheduledFor(context, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(intervalMinutes))
            }
            else -> scheduleNext(context, intervalMinutes, ExistingWorkPolicy.REPLACE)
        }
    }

    /** Arms a single delayed run — [LocationCaptureWorker] calls this itself to chain the next one (non-precision-mode only). */
    fun scheduleNext(context: Context, intervalMinutes: Long, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val request = OneTimeWorkRequestBuilder<LocationCaptureWorker>()
            .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        stampNextScheduledFor(context, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(intervalMinutes))
    }

    /**
     * Precision-mode arm — [LocationCaptureAlarmReceiver] calls this itself
     * to chain the next exact alarm. No-ops without [canScheduleExactAlarms]:
     * turning the toggle on and granting the exact-alarm permission are two
     * separate steps in the UI (the permission row only appears once the
     * toggle is already on), so this must tolerate being called before the
     * permission exists rather than crashing with a SecurityException —
     * [ch.mcfx.urs.settings.LocationHistorySettingsScreen] re-arms via
     * [ch.mcfx.urs.settings.LocationHistorySettingsViewModel.rearmIfNeeded]
     * once the permission is actually granted.
     */
    fun armExact(context: Context, intervalMinutes: Long) {
        if (!canScheduleExactAlarms(context)) return
        val triggerAtMillis = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(intervalMinutes)
        alarmManager(context)?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, alarmPendingIntent(context))
        stampNextScheduledFor(context, triggerAtMillis)
    }

    /**
     * Records this scheduling call's own trigger time (GitHub issue #88) so
     * the run it produces can read back the interval that was *actually*
     * applied when it was armed, instead of recomputing drift from whatever
     * the settings say by the time the run fires — those can have moved on
     * (e.g. an activity-based interval switching back) since this call.
     */
    private fun stampNextScheduledFor(context: Context, triggerAtMillis: Long) {
        val app = context.applicationContext as UrsApplication
        app.container.locationHistorySettingsStore.setNextScheduledForMillis(triggerAtMillis)
    }

    // canScheduleExactAlarms() itself requires API 31 — below that, exact
    // alarms don't need any special permission at all (mirrors
    // ReminderScheduler.canScheduleExactAlarms()).
    fun canScheduleExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager(context)?.canScheduleExactAlarms() == true

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        alarmManager(context)?.cancel(alarmPendingIntent(context))
    }

    private fun alarmManager(context: Context) = ContextCompat.getSystemService(context, AlarmManager::class.java)

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LocationCaptureAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
