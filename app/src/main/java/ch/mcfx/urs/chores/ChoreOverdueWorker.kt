package ch.mcfx.urs.chores

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ch.mcfx.urs.R
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.data.local.publicId
import ch.mcfx.urs.navigation.Destination
import ch.mcfx.urs.notifications.NotificationChannels
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Periodic overdue-chore check (GitHub issue #29). WorkManager's 15-minute
 * periodic floor is irrelevant here — "overdue" moves on a daily scale, so
 * a twice-a-day pass is plenty. Carries no [androidx.work.Constraints]:
 * everything it reads is local Room data, no network needed.
 *
 * Fires one notification per overdue stretch: a type is recorded in the
 * store once notified and cleared again once it is no longer overdue, so
 * logging it (or bumping its interval) re-arms the reminder.
 */
class ChoreOverdueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as UrsApplication
        val store = app.container.choreReminderSettingsStore
        if (!store.isGlobalEnabled()) return Result.success()

        val repo = app.container.choreRepository
        val sender = app.container.notificationSender

        return try {
            val types = repo.observeTypes().first().filter { it.archivedAtMillis == null }
            val events = repo.observeEvents().first()
            val today = LocalDate.now()

            val lastDoneByType: Map<String, LocalDate> = events
                .mapNotNull { e -> runCatching { LocalDate.parse(e.occurredOn) }.getOrNull()?.let { e.trackerTypeId to it } }
                .filter { !it.second.isAfter(today) }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, dates) -> dates.max() }

            val alreadyNotified = store.notifiedTypeIds()
            for (type in types) {
                val id = type.publicId
                val overdue = store.isTypeNotifyEnabled(id) &&
                    isChoreOverdue(type.expectedIntervalDays, lastDoneByType[id], today)
                when {
                    overdue && id !in alreadyNotified -> {
                        sender.show(
                            channelId = NotificationChannels.CHORES,
                            notificationId = NOTIFICATION_ID_BASE + (id.hashCode() and 0xFFFF),
                            title = applicationContext.getString(R.string.chores_overdue_notification_title),
                            body = applicationContext.getString(R.string.chores_overdue_notification_body, type.name),
                            deepLinkRoute = Destination.CHORES.route,
                            groupKey = GROUP_KEY,
                        )
                        store.markNotified(id)
                    }
                    !overdue && id in alreadyNotified -> store.clearNotified(id)
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "chore-overdue-check"
        private const val NOTIFICATION_ID_BASE = 940_000
        private const val GROUP_KEY = "chores-overdue"

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ChoreOverdueWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
