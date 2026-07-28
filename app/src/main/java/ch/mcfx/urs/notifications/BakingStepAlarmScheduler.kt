package ch.mcfx.urs.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * One-shot exact alarm per baking-plan step — modeled on
 * `LocationCaptureScheduler`'s [AlarmManager.setExactAndAllowWhileIdle]
 * mechanics, but for N independent alarms (one per step, keyed by the
 * step's own persisted `alarmId`) rather than one fixed slot, and no
 * self-rearm after firing: each step fires once, unlike a daily reminder or
 * a repeating capture chain. Exact alarms don't survive reboot —
 * `BootCompletedReceiver` re-arms every not-yet-done step of every active
 * plan.
 */
object BakingStepAlarmScheduler {

    // canScheduleExactAlarms() itself requires API 31 — below that, exact
    // alarms don't need any special permission at all (mirrors
    // LocationCaptureScheduler.canScheduleExactAlarms()).
    fun canScheduleExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager(context)?.canScheduleExactAlarms() == true

    fun scheduleStepAlarm(context: Context, alarmId: Int, triggerAtMillis: Long, planId: String, stepLabel: String) {
        if (!canScheduleExactAlarms(context)) return
        alarmManager(context)?.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntentFor(context, alarmId, planId, stepLabel),
        )
    }

    // planId/stepLabel don't need to match what scheduleStepAlarm passed —
    // PendingIntent identity for AlarmManager.cancel() is action/data/type/
    // component/requestCode, extras play no part in it.
    fun cancel(context: Context, alarmId: Int) {
        alarmManager(context)?.cancel(pendingIntentFor(context, alarmId, planId = "", stepLabel = ""))
    }

    private fun alarmManager(context: Context) = ContextCompat.getSystemService(context, AlarmManager::class.java)

    private fun pendingIntentFor(context: Context, alarmId: Int, planId: String, stepLabel: String): PendingIntent {
        val intent = Intent(context, BakingStepAlarmReceiver::class.java).apply {
            putExtra(BakingStepAlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(BakingStepAlarmReceiver.EXTRA_PLAN_ID, planId)
            putExtra(BakingStepAlarmReceiver.EXTRA_STEP_LABEL, stepLabel)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
