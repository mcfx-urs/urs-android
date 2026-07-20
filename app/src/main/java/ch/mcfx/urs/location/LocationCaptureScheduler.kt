package ch.mcfx.urs.location

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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
 */
object LocationCaptureScheduler {
    const val WORK_NAME = "life-map-capture"
    const val PERIODIC_FLOOR_MINUTES = 15L

    fun reschedule(context: Context, intervalMinutes: Long) {
        if (intervalMinutes >= PERIODIC_FLOOR_MINUTES) {
            val request = PeriodicWorkRequestBuilder<LocationCaptureWorker>(intervalMinutes, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
        } else {
            scheduleNext(context, intervalMinutes, ExistingWorkPolicy.REPLACE)
        }
    }

    /** Arms a single delayed run — [LocationCaptureWorker] calls this itself to chain the next one. */
    fun scheduleNext(context: Context, intervalMinutes: Long, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val request = OneTimeWorkRequestBuilder<LocationCaptureWorker>()
            .setInitialDelay(intervalMinutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
