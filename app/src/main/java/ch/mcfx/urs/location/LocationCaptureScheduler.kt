package ch.mcfx.urs.location

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Arms/re-arms/cancels [LocationCaptureWorker]'s periodic run. Deliberately
 * no [androidx.work.Constraints] here either — see the worker's own doc
 * comment, capture must keep working fully offline.
 */
object LocationCaptureScheduler {
    private const val WORK_NAME = "life-map-capture"

    fun reschedule(context: Context, intervalMinutes: Long) {
        val request = PeriodicWorkRequestBuilder<LocationCaptureWorker>(intervalMinutes, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
