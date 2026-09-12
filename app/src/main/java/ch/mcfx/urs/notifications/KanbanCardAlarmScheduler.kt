package ch.mcfx.urs.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * One-shot exact alarm per Kanban card due date (GitHub issue #62) —
 * structured 1:1 like [BakingStepAlarmScheduler]: one independent alarm per
 * entity (keyed by the card's own persisted `alarmId`), no self-rearm after
 * firing. Exact alarms don't survive reboot — `BootCompletedReceiver`
 * re-arms every active card with a future due date.
 */
object KanbanCardAlarmScheduler {

    fun canScheduleExactAlarms(context: Context): Boolean = BakingStepAlarmScheduler.canScheduleExactAlarms(context)

    fun scheduleCardAlarm(context: Context, alarmId: Int, triggerAtMillis: Long, cardId: String, title: String) {
        if (!canScheduleExactAlarms(context)) return
        alarmManager(context)?.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntentFor(context, alarmId, cardId, title),
        )
    }

    // cardId/title don't need to match what scheduleCardAlarm passed —
    // PendingIntent identity for AlarmManager.cancel() is action/data/type/
    // component/requestCode, extras play no part in it.
    fun cancel(context: Context, alarmId: Int) {
        alarmManager(context)?.cancel(pendingIntentFor(context, alarmId, cardId = "", title = ""))
    }

    private fun alarmManager(context: Context) = ContextCompat.getSystemService(context, AlarmManager::class.java)

    private fun pendingIntentFor(context: Context, alarmId: Int, cardId: String, title: String): PendingIntent {
        val intent = Intent(context, KanbanCardAlarmReceiver::class.java).apply {
            putExtra(KanbanCardAlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(KanbanCardAlarmReceiver.EXTRA_CARD_ID, cardId)
            putExtra(KanbanCardAlarmReceiver.EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
