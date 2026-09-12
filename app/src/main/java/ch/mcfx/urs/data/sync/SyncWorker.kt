package ch.mcfx.urs.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import ch.mcfx.urs.UrsApplication
import java.util.concurrent.TimeUnit

/**
 * Durability backstop for the offline fill outbox *and* the pull side
 * (GitHub issue #53): even if the app is never reopened while connectivity
 * briefly returns, this eventually replays whatever is still queued and
 * re-pulls whatever a previous [PullCoordinator.pullAll] attempt failed on.
 * [PeriodicWorkRequestBuilder]'s 15-minute floor means this is *not* the
 * "feels instant" path — see [enqueueOneTime], layered alongside this via
 * [ch.mcfx.urs.vpn.NetworkGate]'s existing connectivity callback. Returning
 * [Result.retry] on a pull failure reuses WorkManager's own exponential
 * backoff (already configured below) rather than this class implementing
 * its own retry/backoff scheduling.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as UrsApplication
        val pushOk = app.container.syncManager.syncNow()
        val pullOk = app.container.pullCoordinator.pullAll()
        return if (pushOk && pullOk) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "fuel-sync"
        private const val ONE_TIME_WORK_NAME = "fuel-sync-now"

        private fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        // Faster-than-the-15-minute floor: fired opportunistically whenever
        // NetworkGate observes connectivity return, alongside (not instead
        // of) the periodic backstop above.
        fun enqueueOneTime(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
