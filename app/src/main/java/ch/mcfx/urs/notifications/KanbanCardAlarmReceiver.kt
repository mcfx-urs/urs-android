package ch.mcfx.urs.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ch.mcfx.urs.R
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.kanban.KanbanRoutes

/**
 * Fired by AlarmManager at a single Kanban card's due-date trigger time
 * (fixed 08:00, see `KanbanRepository`). Synchronous, no goAsync() needed —
 * same reasoning as [BakingStepAlarmReceiver]. Fires once, no self-rearm.
 */
class KanbanCardAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as UrsApplication
        // Carried by the PendingIntent's extras for a future card-detail deep
        // link (no per-board/per-card route exists yet to jump straight to
        // it — see KanbanRoutes) — not read here today, only the alarm id
        // and title are needed to post the notification itself.
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        if (alarmId < 0) return

        app.container.notificationSender.show(
            channelId = NotificationChannels.KANBAN,
            notificationId = alarmId,
            title = title,
            body = context.getString(R.string.kanban_card_alarm_body),
            deepLinkRoute = KanbanRoutes.BOARDS,
        )
    }

    companion object {
        const val EXTRA_CARD_ID = "card_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
