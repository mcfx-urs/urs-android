package ch.mcfx.urs.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE

// Public API for scheduling exact daily reminders. Deliberately built on
// AlarmManager.setExactAndAllowWhileIdle(), not WorkManager: WorkManager's
// periodic work can drift by minutes to hours under Doze/battery
// restrictions, which defeats the point of a reminder meant to fire at a
// specific time (e.g. before leaving the house in the morning).
class ReminderScheduler(
    private val context: Context,
    private val store: ReminderStore,
    private val sender: NotificationSender,
) {

    // canScheduleExactAlarms() itself requires API 31 — below that, exact
    // alarms don't need any special permission at all, so it's just always
    // allowed (minSdk here is 26).
    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager()?.canScheduleExactAlarms() == true

    fun scheduleDaily(
        id: Int,
        channelId: String,
        hour: Int,
        minute: Int,
        title: String,
        body: String,
        deepLinkRoute: String? = null,
    ) {
        val reminder = ReminderConfig(id, channelId, hour, minute, title, body, deepLinkRoute, lastFiredDate = null)
        store.save(reminder)
        armNext(reminder)
    }

    fun cancel(id: Int) {
        store.remove(id)
        alarmManager()?.cancel(pendingIntentFor(id))
    }

    // Called from both UrsApplication.onCreate() (app-open case) and
    // BootCompletedReceiver (boot case, since exact alarms don't survive a
    // reboot) — re-arms every persisted reminder's next occurrence, firing
    // immediately first for any whose scheduled time already passed today
    // without having fired, rather than silently waiting for tomorrow.
    fun rearmAndCheckMissed() {
        val today = LocalDate.now()
        store.getAll().forEach { reminder ->
            val scheduledToday = today.atTime(reminder.hour, reminder.minute)
            val alreadyFiredToday = reminder.lastFiredDate == today.format(DATE_FORMAT)
            if (!alreadyFiredToday && LocalDateTime.now().isAfter(scheduledToday)) {
                fireNow(reminder)
            } else {
                armNext(reminder)
            }
        }
    }

    // Called by ReminderAlarmReceiver when the exact alarm actually fires,
    // and by rearmAndCheckMissed() for one that was missed.
    internal fun fireNow(reminder: ReminderConfig) {
        sender.show(
            channelId = reminder.channelId,
            notificationId = reminder.id,
            title = reminder.title,
            body = reminder.body,
            deepLinkRoute = reminder.deepLinkRoute,
            groupKey = reminder.channelId,
        )
        val updated = reminder.copy(lastFiredDate = LocalDate.now().format(DATE_FORMAT))
        store.save(updated)
        armNext(updated)
    }

    private fun armNext(reminder: ReminderConfig) {
        val alarmManager = alarmManager() ?: return
        val now = LocalDateTime.now()
        var next = LocalDate.now().atTime(reminder.hour, reminder.minute)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAtMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntentFor(reminder.id))
    }

    private fun alarmManager(): AlarmManager? = ContextCompat.getSystemService(context, AlarmManager::class.java)

    private fun pendingIntentFor(id: Int): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, id)
        }
        return PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
