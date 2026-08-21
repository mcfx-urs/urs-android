package ch.mcfx.urs.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * One-shot exact alarm per note reminder (GitHub issue #10) — same
 * mechanics as [BakingStepAlarmScheduler]: one independent alarm per note,
 * keyed by the note's own persisted alarm id, no self-rearm after firing.
 * Exact alarms don't survive reboot — `BootCompletedReceiver` re-arms every
 * active note with a still-pending reminder.
 */
object NoteAlarmScheduler {

    fun canScheduleExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager(context)?.canScheduleExactAlarms() == true

    fun scheduleNoteAlarm(context: Context, alarmId: Int, triggerAtMillis: Long, noteId: String, title: String) {
        if (!canScheduleExactAlarms(context)) return
        alarmManager(context)?.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntentFor(context, alarmId, noteId, title),
        )
    }

    // noteId/title don't need to match what scheduleNoteAlarm passed —
    // PendingIntent identity for AlarmManager.cancel() is action/data/type/
    // component/requestCode, extras play no part in it.
    fun cancel(context: Context, alarmId: Int) {
        alarmManager(context)?.cancel(pendingIntentFor(context, alarmId, noteId = "", title = ""))
    }

    private fun alarmManager(context: Context) = ContextCompat.getSystemService(context, AlarmManager::class.java)

    private fun pendingIntentFor(context: Context, alarmId: Int, noteId: String, title: String): PendingIntent {
        val intent = Intent(context, NoteAlarmReceiver::class.java).apply {
            putExtra(NoteAlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(NoteAlarmReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(NoteAlarmReceiver.EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
