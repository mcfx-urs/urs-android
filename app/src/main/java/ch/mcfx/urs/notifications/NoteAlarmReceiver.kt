package ch.mcfx.urs.notifications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import ch.mcfx.urs.R
import ch.mcfx.urs.UrsApplication
import ch.mcfx.urs.notes.NotesRoutes

/**
 * Fired by AlarmManager at a single note's exact reminder time (GitHub
 * issue #10). Synchronous, no goAsync() needed — same reasoning as
 * [BakingStepAlarmReceiver]. Fires once, no self-rearm.
 */
class NoteAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as UrsApplication
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        if (alarmId < 0) return

        // "Done" only clears the reminder (GitHub issue #52) — it never
        // touches the note's completion status, unlike marking it Erledigt
        // from within the app. NoteReminderDoneReceiver needs a suspend DB
        // write, so it goAsync()s itself; this receiver stays synchronous.
        val doneIntent = Intent(context, NoteReminderDoneReceiver::class.java).apply {
            putExtra(EXTRA_NOTE_ID, noteId)
            putExtra(EXTRA_ALARM_ID, alarmId)
        }
        val donePendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val doneAction = NotificationCompat.Action(
            0,
            context.getString(R.string.note_reminder_done_action),
            donePendingIntent,
        )

        app.container.notificationSender.show(
            channelId = NotificationChannels.NOTES,
            notificationId = alarmId,
            title = title,
            body = context.getString(R.string.note_reminder_body),
            deepLinkRoute = NotesRoutes.detail(noteId),
            actions = listOf(doneAction),
        )
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_ALARM_ID = "alarm_id"
    }
}
