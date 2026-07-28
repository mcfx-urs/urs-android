package ch.mcfx.urs.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ch.mcfx.urs.R
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.baking.BakingRoutes

/**
 * Fired by AlarmManager at a single baking-plan step's exact trigger time
 * (). Synchronous, no goAsync() needed — [NotificationSender.show] is
 * not suspend (same reasoning as `LocationCaptureAlarmReceiver`). Fires
 * once, no self-rearm — unlike [ReminderScheduler]'s daily reminders or
 * `LocationCaptureScheduler`'s repeating chain, a step alarm is genuinely
 * one-shot.
 */
class BakingStepAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as UrsApplication
        val planId = intent.getStringExtra(EXTRA_PLAN_ID) ?: return
        val stepLabel = intent.getStringExtra(EXTRA_STEP_LABEL) ?: return
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        if (alarmId < 0) return

        app.container.notificationSender.show(
            channelId = NotificationChannels.BAKING,
            notificationId = alarmId,
            title = stepLabel,
            body = context.getString(R.string.baking_step_alarm_body),
            deepLinkRoute = BakingRoutes.planDetail(planId),
        )
    }

    companion object {
        const val EXTRA_PLAN_ID = "plan_id"
        const val EXTRA_STEP_LABEL = "step_label"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
